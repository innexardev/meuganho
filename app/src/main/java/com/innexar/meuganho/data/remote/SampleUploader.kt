package com.innexar.meuganho.data.remote

import android.os.Build
import com.innexar.meuganho.BuildConfig
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.local.dao.DiagnosticSampleDao
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.data.preferences.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class UploadStatus(
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val totalSent: Int = 0,
)

/**
 * Envia as amostras de calibração (já sanitizadas) em lotes.
 *
 * Orientado a eventos: tenta enviar quando a fila acumula 10 itens ou tem item
 * com mais de 5 min, nunca em laço. Sem rede, as amostras esperam na fila
 * (limitada a 1000) e vão na próxima oportunidade.
 */
class SampleUploader(
    private val dao: DiagnosticSampleDao,
    private val settingsRepository: SettingsRepository,
    private val settings: StateFlow<AppSettings>,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var lastFailureAt = 0L
    private val _status = MutableStateFlow(UploadStatus())
    val status: StateFlow<UploadStatus> = _status.asStateFlow()

    val enabled: Boolean get() = settings.value.sendSamples && BuildConfig.UPLOAD_KEY.isNotEmpty()

    /**
     * Chamado após cada amostra nova e na abertura do app.
     * [urgent] (ofertas e telas desconhecidas): envia se o último envio foi há mais de 1 min.
     */
    fun maybeUpload(urgent: Boolean = false) {
        if (!enabled || mutex.isLocked) return
        scope.launch {
            val now = System.currentTimeMillis()
            if (now - lastFailureAt < RETRY_AFTER_MS) return@launch
            val count = dao.pendingCount()
            val oldest = dao.oldestPendingAt() ?: return@launch
            val sinceLastSend = now - (_status.value.lastSuccessAt ?: 0)
            if ((urgent && sinceLastSend >= URGENT_MIN_INTERVAL_MS) || count >= BATCH_TRIGGER || now - oldest >= MAX_WAIT_MS) upload()
        }
    }

    /** Envio imediato (botão "Enviar agora" do Diagnóstico). */
    suspend fun uploadNow(): Result<Int> = if (enabled) upload() else Result.failure(IllegalStateException("Envio desligado"))

    private suspend fun upload(): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            var sent = 0
            val now = System.currentTimeMillis()
            _status.value = _status.value.copy(lastAttemptAt = now)
            val result = runCatching {
                val installId = settingsRepository.installId()
                while (true) {
                    val batch = dao.pending(BATCH_SIZE)
                    if (batch.isEmpty()) break
                    val body = buildJsonObject {
                        put("installId", installId)
                        put("appVersionName", BuildConfig.VERSION_NAME)
                        put("appVersionCode", BuildConfig.VERSION_CODE)
                        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("android", Build.VERSION.SDK_INT)
                        put("samples", buildJsonArray {
                            batch.forEach { s ->
                                add(buildJsonObject {
                                    put("capturedAt", s.capturedAt)
                                    put("platform", s.platform.name)
                                    put("screenType", s.screenType.name)
                                    put("reason", s.reason)
                                    put("parserVersion", s.parserVersion)
                                    put("sourceAppVersion", s.sourceAppVersion)
                                    put("text", s.sanitizedText)
                                    put("parse", JsonPrimitive(s.parseSummary))
                                })
                            }
                        })
                    }
                    HttpJson.post("${BuildConfig.DIST_BASE_URL}/api/samples", body.toString(),
                        mapOf("X-MeuGanho-Key" to BuildConfig.UPLOAD_KEY))
                    dao.markUploaded(batch.map { it.id })
                    sent += batch.size
                }
                dao.deleteUploadedBefore(now - KEEP_UPLOADED_MS)
                sent
            }
            result.onSuccess {
                _status.value = _status.value.copy(lastSuccessAt = now, lastError = null, totalSent = _status.value.totalSent + it)
                if (it > 0) AppLog.d(LogCategory.SYSTEM, "Amostras enviadas: $it")
            }.onFailure {
                lastFailureAt = now
                _status.value = _status.value.copy(lastError = it.message ?: it.javaClass.simpleName)
                AppLog.e(LogCategory.SYSTEM, "Falha ao enviar amostras (tentará depois)", it)
            }
            dao.trimPending(MAX_PENDING)
            result
        }
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val BATCH_TRIGGER = 5
        const val MAX_WAIT_MS = 2 * 60_000L
        const val URGENT_MIN_INTERVAL_MS = 60_000L
        const val RETRY_AFTER_MS = 2 * 60_000L
        const val KEEP_UPLOADED_MS = 3 * 86_400_000L
        const val MAX_PENDING = 1000
    }
}
