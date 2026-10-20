package com.innexar.meuganho.features.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import kotlinx.coroutines.launch

/**
 * Registro manual: o motorista informou, então é CONFIRMED. É a rede de segurança
 * quando a captura automática não está ativa ou perdeu uma corrida.
 */
class ManualTripViewModel(private val container: AppContainer) : ViewModel() {

    fun save(platform: Platform, value: String, km: String, minutes: String, onSaved: () -> Unit, onError: (String) -> Unit) {
        val cents = Money.parseInput(value)
        if (cents == null || cents <= 0) return onError("Informe o valor da corrida")
        val meters = km.replace(',', '.').toDoubleOrNull()?.let { (it * 1000).toInt() }
        val seconds = minutes.toIntOrNull()?.times(60)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val started = now - (seconds ?: 0) * 1000L
            val id = container.database.tripDao().insert(
                TripEntity(
                    shiftId = container.database.shiftDao().open()?.id,
                    offerId = null,
                    platform = platform,
                    state = TripState.COMPLETED,
                    status = DataStatus.CONFIRMED,
                    acceptedAt = started,
                    pickupArrivedAt = started,
                    startedAt = started,
                    endedAt = now,
                    finalCents = cents,
                    valueSource = ValueSource.USER,
                    manualMeters = meters,
                    isManual = true,
                )
            )
            container.statsSync.enqueue(com.innexar.meuganho.data.remote.StatsSync.Kind.TRIP, id)
            onSaved()
        }
    }
}
