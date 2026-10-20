package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.domain.financial.Rates

data class TripComparison(
    val estimatedCents: Long,
    val realCents: Long,
    val estimatedSeconds: Long,
    val realSeconds: Long,
    val estimatedMeters: Long,
    val realMeters: Long,
) {
    val estimatedPerHour: Long? get() = Rates.centsPerHour(estimatedCents, estimatedSeconds)
    val realPerHour: Long? get() = Rates.centsPerHour(realCents, realSeconds)
    val estimatedPerKm: Long? get() = Rates.centsPerKm(estimatedCents, estimatedMeters)
    val realPerKm: Long? get() = Rates.centsPerKm(realCents, realMeters)

    /** Pareceu boa e não foi: R$/h real abaixo de 80% do prometido pela oferta. */
    val disappointed: Boolean
        get() {
            val est = estimatedPerHour ?: return false
            val real = realPerHour ?: return false
            return real < est * 0.8
        }
}

data class EstimateVsRealSummary(
    val comparisons: Int,
    val avgEstimatedPerHour: Long?,
    val avgRealPerHour: Long?,
    val avgEstimatedPerKm: Long?,
    val avgRealPerKm: Long?,
    val avgExtraMinutes: Long?,
    val disappointedCount: Int,
)

/** Oferta prometida x corrida real: revela que tipo de oferta engana. */
object EstimateVsReal {
    fun summarize(items: List<TripComparison>): EstimateVsRealSummary {
        val valid = items.filter { it.realSeconds > 0 && it.estimatedSeconds > 0 }
        if (valid.isEmpty()) return EstimateVsRealSummary(0, null, null, null, null, null, 0)
        val estCents = valid.sumOf { it.estimatedCents }
        val realCents = valid.sumOf { it.realCents }
        return EstimateVsRealSummary(
            comparisons = valid.size,
            avgEstimatedPerHour = Rates.centsPerHour(estCents, valid.sumOf { it.estimatedSeconds }),
            avgRealPerHour = Rates.centsPerHour(realCents, valid.sumOf { it.realSeconds }),
            avgEstimatedPerKm = Rates.centsPerKm(estCents, valid.sumOf { it.estimatedMeters }),
            avgRealPerKm = Rates.centsPerKm(realCents, valid.sumOf { it.realMeters }),
            avgExtraMinutes = valid.sumOf { it.realSeconds - it.estimatedSeconds } / valid.size / 60,
            disappointedCount = valid.count { it.disappointed },
        )
    }
}
