package com.innexar.meuganho.domain.financial

import com.innexar.meuganho.domain.model.OfferRating

data class OfferAnalysis(
    val valueCents: Long,
    val totalMeters: Long?,
    val totalSeconds: Long?,
    val centsPerKm: Long?,
    val centsPerHour: Long?,
    val fuelCostCents: Long?,
    val afterFuelCents: Long?,
    val operationalCostCents: Long?,
    val operationalResultCents: Long?,
    val rating: OfferRating,
    /** R$/min calculado direto (arredondado), não derivado do R$/h. */
    val centsPerMinute: Long? = null,
    /** Custo por km do carro (combustível + manutenção + depreciação + outros). */
    val costCentsPerKm: Long? = null,
    /** Lucro de verdade (valor − custo operacional) por hora e por km. */
    val netCentsPerHour: Long? = null,
    val netCentsPerKm: Long? = null,
) {
    /** Lucro: com custos do carro cadastrados, o operacional; senão, só descontando combustível. */
    val profitCents: Long? get() = operationalResultCents ?: afterFuelCents
}

/**
 * Análise instantânea de uma oferta (ou de uma corrida real, com os mesmos números).
 *
 * O km total inclui o deslocamento até o passageiro: esses quilômetros não são
 * pagos, mas gastam combustível e tempo, então fazem parte do custo real.
 */
object OfferAnalyzer {

    fun analyze(
        valueCents: Long,
        totalMeters: Long?,
        totalSeconds: Long?,
        reliable: Boolean,
        costModel: CostModel,
        thresholds: RatingThresholds,
    ): OfferAnalysis {
        val perKm = totalMeters?.let { Rates.centsPerKm(valueCents, it) }
        val perHour = totalSeconds?.let { Rates.centsPerHour(valueCents, it) }
        val fuel = totalMeters?.let { costModel.fuelCostCents(it) }
        val operational = totalMeters?.let { costModel.operationalCostCents(it) }
        return OfferAnalysis(
            valueCents = valueCents,
            totalMeters = totalMeters,
            totalSeconds = totalSeconds,
            centsPerKm = perKm,
            centsPerHour = perHour,
            fuelCostCents = fuel,
            afterFuelCents = fuel?.let { valueCents - it },
            operationalCostCents = operational,
            operationalResultCents = operational?.let { valueCents - it },
            rating = if (reliable) thresholds.rate(perKm, perHour) else OfferRating.UNCERTAIN,
            centsPerMinute = totalSeconds?.takeIf { it > 0 }?.let { (valueCents * 60 + it / 2) / it },
            costCentsPerKm = kotlin.math.round(costModel.fuelCentsPerKm + costModel.fixedCentsPerKm).toLong(),
            netCentsPerHour = operational?.let { op -> totalSeconds?.let { Rates.centsPerHour(valueCents - op, it) } },
            netCentsPerKm = operational?.let { op -> totalMeters?.let { Rates.centsPerKm(valueCents - op, it) } },
        )
    }
}
