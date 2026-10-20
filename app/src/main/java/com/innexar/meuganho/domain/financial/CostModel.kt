package com.innexar.meuganho.domain.financial

import kotlin.math.roundToLong

/**
 * Custo por quilômetro rodado. Separamos combustível (sai do caixa na hora)
 * dos custos diluídos (manutenção, pneus, depreciação), que só aparecem
 * meses depois mas são tão reais quanto o combustível.
 */
data class CostModel(
    val fuelPriceCentsPerLiter: Long,
    val kmPerLiter: Double,
    val maintenanceCentsPerKm: Double,
    val depreciationCentsPerKm: Double,
    val otherCentsPerKm: Double,
    /** Indica se o consumo veio do histórico de abastecimentos (mais confiável). */
    val consumptionIsObserved: Boolean = false,
) {
    val fuelCentsPerKm: Double
        get() = if (kmPerLiter > 0) fuelPriceCentsPerLiter / kmPerLiter else 0.0

    val fixedCentsPerKm: Double
        get() = maintenanceCentsPerKm + depreciationCentsPerKm + otherCentsPerKm

    fun fuelCostCents(meters: Long): Long = (meters / 1000.0 * fuelCentsPerKm).roundToLong()

    /** Custo operacional total estimado: combustível + custos diluídos. */
    fun operationalCostCents(meters: Long): Long =
        (meters / 1000.0 * (fuelCentsPerKm + fixedCentsPerKm)).roundToLong()

    companion object {
        /** Usado enquanto o motorista não cadastra veículo: apenas combustível. */
        fun fallback(fuelPriceCentsPerLiter: Long) = CostModel(
            fuelPriceCentsPerLiter = fuelPriceCentsPerLiter,
            kmPerLiter = 10.0,
            maintenanceCentsPerKm = 0.0,
            depreciationCentsPerKm = 0.0,
            otherCentsPerKm = 0.0,
        )
    }
}
