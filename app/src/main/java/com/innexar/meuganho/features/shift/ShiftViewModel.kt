package com.innexar.meuganho.features.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.repository.DayOverview
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.engine.LiveTrip
import com.innexar.meuganho.engine.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShiftUiState(
    val openShift: ShiftEntity? = null,
    val today: DayOverview? = null,
    val live: LiveTrip = LiveTrip(),
    val accessibilityOn: Boolean = false,
    val gpsRunning: Boolean = false,
    val suggestedOdometer: String = "",
    val suggestedFuelPrice: String = "",
    val gpsEnabled: Boolean = true,
)

class ShiftViewModel(private val container: AppContainer) : ViewModel() {

    val platforms = MutableStateFlow(setOf(Platform.UBER, Platform.NINETY_NINE))

    val state: StateFlow<ShiftUiState> = combine(
        combine(container.shiftRepository.openShift, container.statsRepository.observeToday()) { a, b -> a to b },
        container.tripTracker.live,
        combine(ServiceStatus.accessibilityConnected, ServiceStatus.shiftServiceRunning) { a, b -> a to b },
        container.database.vehicleDao().observeActive(),
        container.settingsRepository.settings,
    ) { (shift, today), live, (acc, gps), vehicle, settings ->
        ShiftUiState(
            openShift = shift,
            today = today,
            live = live,
            accessibilityOn = acc,
            gpsRunning = gps,
            suggestedOdometer = vehicle?.odometerKm?.takeIf { it > 0 }?.toString().orEmpty(),
            suggestedFuelPrice = Money.toInput(settings.fuelPriceCentsPerLiter),
            gpsEnabled = settings.gpsEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShiftUiState())

    fun togglePlatform(p: Platform) {
        platforms.value = if (p in platforms.value) platforms.value - p else platforms.value + p
    }

    /** Inicia o turno. O serviço de GPS é iniciado pela tela, que tem acesso às permissões. */
    fun start(odometer: String, fuelPrice: String, onStarted: (gpsEnabled: Boolean) -> Unit) {
        viewModelScope.launch {
            val price = Money.parseInput(fuelPrice)
            if (price != null) container.settingsRepository.update { it.copy(fuelPriceCentsPerLiter = price) }
            val current = container.settingsRepository.current()
            container.shiftRepository.start(
                platforms = platforms.value.ifEmpty { Platform.entries.toSet() },
                startOdometerKm = odometer.trim().toIntOrNull(),
                fuelPriceCents = price ?: current.fuelPriceCentsPerLiter,
                now = System.currentTimeMillis(),
            )
            onStarted(current.gpsEnabled)
        }
    }

    fun togglePause() = viewModelScope.launch { container.togglePause(System.currentTimeMillis()) }

    fun end(odometer: String, notes: String, onEnded: () -> Unit) {
        viewModelScope.launch {
            val shiftId = container.database.shiftDao().open()?.id
            container.shiftRepository.end(odometer.trim().toIntOrNull(), notes.ifBlank { null }, System.currentTimeMillis())
            shiftId?.let { container.statsSync.enqueue(com.innexar.meuganho.data.remote.StatsSync.Kind.SHIFT, it) }
            // Atualização baixada durante o turno instala agora, com o turno encerrado.
            container.updateManager.installIfIdle()
            onEnded()
        }
    }
}
