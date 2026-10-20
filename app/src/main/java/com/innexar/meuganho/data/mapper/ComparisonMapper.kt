package com.innexar.meuganho.data.mapper

import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.stats.TripComparison

/** Só corridas concluídas, confiáveis e com estimativa completa entram na comparação. */
fun TripEntity.toComparison(): TripComparison? {
    if (state != TripState.COMPLETED || !status.countsInStats) return null
    val est = estimatedCents ?: return null
    val real = effectiveCents ?: return null
    val estSeconds = (estTripSeconds ?: return null) + (estPickupSeconds ?: 0)
    val estMeters = (estTripMeters ?: return null) + (estPickupMeters ?: 0)
    val end = endedAt ?: return null
    return TripComparison(
        estimatedCents = est,
        realCents = real,
        estimatedSeconds = estSeconds.toLong(),
        realSeconds = (end - acceptedAt) / 1000,
        estimatedMeters = estMeters.toLong(),
        realMeters = paidMeters + pickupMeters,
    )
}
