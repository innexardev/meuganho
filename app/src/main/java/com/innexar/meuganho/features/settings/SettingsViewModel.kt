package com.innexar.meuganho.features.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.data.preferences.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val message = MutableStateFlow<String?>(null)

    /** Combustível do carro cadastrado: a tela só mostra o que vale para ele. */
    val vehicleFuel: StateFlow<com.innexar.meuganho.domain.model.FuelType?> = container.database.vehicleDao().observeActive()
        .map { it?.fuelType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        container.settingsRepository.update(transform)
    }

    /** Campos monetários: só grava se o texto for um valor válido. */
    fun updateMoney(text: String, apply: (AppSettings, Long) -> AppSettings) {
        val cents = Money.parseInput(text) ?: return
        update { apply(it, cents) }
    }

    fun exportJson(uri: Uri) = viewModelScope.launch {
        container.backupManager.exportJson(uri, System.currentTimeMillis())
            .onSuccess { message.value = "Backup salvo ($it corridas)" }
            .onFailure { message.value = "Falha no backup: ${it.message}" }
    }

    fun importJson(uri: Uri) = viewModelScope.launch {
        container.backupManager.importJson(uri)
            .onSuccess { message.value = "Backup restaurado ($it corridas)" }
            .onFailure { message.value = "Falha ao restaurar: ${it.message}" }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        container.backupManager.exportTripsCsv(uri)
            .onSuccess { message.value = "CSV salvo ($it corridas)" }
            .onFailure { message.value = "Falha no CSV: ${it.message}" }
    }
}
