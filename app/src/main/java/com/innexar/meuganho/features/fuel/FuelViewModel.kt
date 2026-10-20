package com.innexar.meuganho.features.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.data.local.entity.FuelLogEntity
import com.innexar.meuganho.data.mapper.toFill
import com.innexar.meuganho.domain.financial.ConsumptionCalculator
import com.innexar.meuganho.domain.financial.ConsumptionInterval
import com.innexar.meuganho.domain.model.FuelType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

data class FuelState(
    val logs: List<FuelLogEntity> = emptyList(),
    val intervals: List<ConsumptionInterval> = emptyList(),
    val observedKmPerLiter: Double? = null,
    val nominalKmPerLiter: Double? = null,
    val vehicleFuel: FuelType? = null,
    val lastOdometer: Int? = null,
)

class FuelViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.fuelLogDao()

    val state: StateFlow<FuelState> = combine(dao.observeAll(), container.database.vehicleDao().observeActive()) { logs, vehicle ->
        val fills = logs.map { it.toFill() }
        FuelState(
            logs = logs,
            intervals = ConsumptionCalculator.intervals(fills),
            observedKmPerLiter = ConsumptionCalculator.observedKmPerLiter(fills),
            nominalKmPerLiter = vehicle?.averageKmPerLiter,
            vehicleFuel = vehicle?.fuelType,
            lastOdometer = maxOf(logs.maxOfOrNull { it.odometerKm } ?: 0, vehicle?.odometerKm ?: 0).takeIf { it > 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelState())

    /**
     * Preço, litros e total: basta informar dois; o terceiro é calculado.
     * Salvar também atualiza o preço/combustível atual usados nas análises.
     */
    fun save(
        odometer: String, fuel: FuelType, price: String, liters: String, total: String, fullTank: Boolean, note: String,
        onSaved: () -> Unit, onError: (String) -> Unit,
    ) {
        val odo = odometer.toIntOrNull() ?: return onError("Informe o odômetro")
        var priceCents = Money.parseInput(price)
        var litersValue = liters.replace(',', '.').toDoubleOrNull()
        var totalCents = Money.parseInput(total)
        when {
            priceCents != null && litersValue != null && totalCents == null -> totalCents = (priceCents * litersValue).roundToLong()
            priceCents != null && totalCents != null && litersValue == null && priceCents > 0 -> litersValue = totalCents.toDouble() / priceCents
            litersValue != null && totalCents != null && priceCents == null && litersValue > 0 -> priceCents = (totalCents / litersValue).roundToLong()
        }
        if (priceCents == null || litersValue == null || totalCents == null || litersValue <= 0) {
            return onError("Informe dois entre preço, litros e total")
        }
        val finalPrice: Long = priceCents
        val finalLiters: Double = litersValue
        val finalTotal: Long = totalCents
        viewModelScope.launch {
            val vehicle = container.database.vehicleDao().active()
            dao.insert(
                FuelLogEntity(
                    vehicleId = vehicle?.id, at = System.currentTimeMillis(), odometerKm = odo, fuelType = fuel,
                    priceCentsPerLiter = finalPrice, liters = finalLiters, totalCents = finalTotal, fullTank = fullTank,
                    note = note.ifBlank { null },
                )
            )
            vehicle?.let { container.database.vehicleDao().raiseOdometer(it.id, odo) }
            container.settingsRepository.update {
                it.copy(fuelPriceCentsPerLiter = finalPrice, currentFuel = if (fuel == FuelType.FLEX) it.currentFuel else fuel)
            }
            onSaved()
        }
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}
