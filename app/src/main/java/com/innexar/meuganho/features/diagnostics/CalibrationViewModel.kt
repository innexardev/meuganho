package com.innexar.meuganho.features.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.data.local.entity.DiagnosticSampleEntity
import com.innexar.meuganho.data.remote.UploadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalibrationState(
    val enabled: Boolean = true,
    val pending: Int = 0,
    val status: UploadStatus = UploadStatus(),
    val latest: List<DiagnosticSampleEntity> = emptyList(),
    val message: String? = null,
)

/** Estado da gravação automática e do envio de amostras de calibração. */
class CalibrationViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.diagnosticSampleDao()
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<CalibrationState> = combine(
        container.settingsRepository.settings,
        dao.observePendingCount(),
        container.sampleUploader.status,
        dao.observeLatestPerType(),
        message,
    ) { settings, pending, status, latest, msg ->
        CalibrationState(settings.sendSamples, pending, status, latest, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalibrationState())

    fun uploadNow() = viewModelScope.launch {
        message.value = "Enviando…"
        message.value = container.sampleUploader.uploadNow().fold(
            onSuccess = { "Enviadas: $it" },
            onFailure = { "Não enviado: ${it.message}" },
        )
    }

    /** Texto único com a última amostra de cada tipo, para compartilhar manualmente. */
    fun shareText(): String = state.value.latest.joinToString("\n\n") { s ->
        "# ${s.platform} · ${s.screenType} · ${s.reason} · app ${s.sourceAppVersion} · ${s.parserVersion}\n" +
            "# parse: ${s.parseSummary}\n${s.sanitizedText}"
    }
}
