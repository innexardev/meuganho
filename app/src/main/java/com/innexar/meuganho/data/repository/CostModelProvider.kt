package com.innexar.meuganho.data.repository

import com.innexar.meuganho.data.local.dao.FuelLogDao
import com.innexar.meuganho.data.local.dao.VehicleDao
import com.innexar.meuganho.data.local.entity.FuelLogEntity
import com.innexar.meuganho.data.local.entity.VehicleEntity
import com.innexar.meuganho.data.mapper.toFill
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.data.preferences.SettingsRepository
import com.innexar.meuganho.domain.financial.ConsumptionCalculator
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.model.FuelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Monta o [CostModel] vigente a partir de veículo + configurações + abastecimentos.
 * Reage a mudanças (Flow) em vez de ser recalculado por consulta.
 */
class CostModelProvider(
    vehicleDao: VehicleDao,
    fuelLogDao: FuelLogDao,
    settings: SettingsRepository,
) {
    val costModel: Flow<CostModel> = combine(
        vehicleDao.observeActive(),
        fuelLogDao.observeAll(),
        settings.settings,
    ) { vehicle, fills, prefs -> build(vehicle, fills, prefs) }.distinctUntilChanged()

    companion object {
        /** Etanol rende em média ~70% da gasolina: usado se o FLEX não tiver consumo de etanol. */
        private const val ETHANOL_FACTOR = 0.7

        fun build(vehicle: VehicleEntity?, fills: List<FuelLogEntity>, prefs: AppSettings): CostModel {
            if (vehicle == null) return CostModel.fallback(prefs.fuelPriceCentsPerLiter)
            // Elétrico: mesma conta, com km/kWh e R$/kWh (abastecimentos em litros não se aplicam).
            if (vehicle.fuelType == FuelType.ELECTRIC) {
                return CostModel(
                    fuelPriceCentsPerLiter = prefs.electricityPriceCentsPerKwh,
                    kmPerLiter = vehicle.averageKmPerLiter,
                    maintenanceCentsPerKm = vehicle.maintenanceCentsPerKm,
                    depreciationCentsPerKm = vehicle.depreciationCentsPerKm,
                    otherCentsPerKm = prefs.otherCentsPerKm,
                )
            }
            // Carro só-etanol já tem o consumo médio cadastrado em etanol.
            val flexOnEthanol = vehicle.fuelType == FuelType.FLEX && prefs.currentFuel == FuelType.ETHANOL
            val nominal = if (flexOnEthanol) {
                vehicle.ethanolKmPerLiter ?: (vehicle.averageKmPerLiter * ETHANOL_FACTOR)
            } else {
                vehicle.averageKmPerLiter
            }
            // Consumo observado só com abastecimentos do mesmo combustível em uso agora.
            val sameFuel = fills.filter { it.vehicleId == vehicle.id && it.fuelType == effectiveFuel(vehicle, prefs) }
            val observed = if (prefs.useObservedConsumption) {
                ConsumptionCalculator.observedKmPerLiter(sameFuel.map { it.toFill() })
            } else {
                null
            }
            return CostModel(
                fuelPriceCentsPerLiter = prefs.fuelPriceCentsPerLiter,
                kmPerLiter = observed ?: nominal,
                maintenanceCentsPerKm = vehicle.maintenanceCentsPerKm,
                depreciationCentsPerKm = vehicle.depreciationCentsPerKm,
                otherCentsPerKm = prefs.otherCentsPerKm,
                consumptionIsObserved = observed != null,
            )
        }

        private fun effectiveFuel(vehicle: VehicleEntity, prefs: AppSettings): FuelType =
            if (vehicle.fuelType == FuelType.FLEX) prefs.currentFuel else vehicle.fuelType
    }
}
