package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.domain.model.Platform

data class PlatformSlice(
    val tripCount: Int = 0,
    val grossCents: Long = 0,
    val paidMeters: Long = 0,
    val inTripSeconds: Long = 0,
)

/**
 * Três visões financeiras que NUNCA devem ser confundidas:
 * - bruto: o que as plataformas pagaram;
 * - caixa: bruto - combustível comprado - despesas diretas (o que sobrou no bolso);
 * - resultado operacional: bruto - custo/km estimado (combustível + manutenção +
 *   depreciação) - despesas. É uma ESTIMATIVA, não valor contábil.
 */
data class PeriodSummary(
    val tripCount: Int,
    val grossCents: Long,
    val pendingCount: Int,
    val pendingCents: Long,
    val byPlatform: Map<Platform, PlatformSlice>,
    val workedSeconds: Long,
    val onlineSeconds: Long,
    val toPickupSeconds: Long,
    val waitingSeconds: Long,
    val inTripSeconds: Long,
    val idleSeconds: Long,
    val totalMeters: Long,
    val paidMeters: Long,
    val pickupMeters: Long,
    val emptyMeters: Long,
    val gpsMeters: Long,
    val odometerMeters: Long?,
    val fuelEstimateCents: Long,
    val fuelPurchasedCents: Long,
    val expensesCents: Long,
    val operationalCostCents: Long,
    val cashCents: Long,
    val operationalResultCents: Long,
    val centsPerHourOnline: Long?,
    val centsPerHourInTrip: Long?,
    val centsPerKm: Long?,
) {
    val averageTicketCents: Long? get() = if (tripCount > 0) (grossCents + tripCount / 2) / tripCount else null // arredonda: 146,32/6 = 24,39
    /** Lucro de verdade (bruto − custo do carro − despesas) por hora online e por km rodado. */
    val netCentsPerHourOnline: Long? get() = com.innexar.meuganho.domain.financial.Rates.centsPerHour(operationalResultCents, onlineSeconds)
    val netCentsPerKm: Long? get() = com.innexar.meuganho.domain.financial.Rates.centsPerKm(operationalResultCents, totalMeters)
    val costCentsPerKm: Long? get() = com.innexar.meuganho.domain.financial.Rates.centsPerKm(operationalCostCents, totalMeters)
    val centsPerTrip: Long? get() = averageTicketCents
    val idleRatio: Double get() = if (onlineSeconds > 0) idleSeconds.toDouble() / onlineSeconds else 0.0
    val emptyRatio: Double get() = if (totalMeters > 0) emptyMeters.toDouble() / totalMeters else 0.0
}
