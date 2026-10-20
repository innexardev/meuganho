package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState

/**
 * Entradas mínimas dos cálculos de período. Os calculadores não conhecem Room;
 * os mapeamentos ficam em data/mapper, o que mantém o domínio testável em JVM pura.
 */
data class TripFacts(
    val platform: Platform,
    val state: TripState,
    val status: DataStatus,
    /** Valor efetivo (corrigido > tela final > estimativa). Null se desconhecido. */
    val valueCents: Long?,
    val acceptedAt: Long,
    val pickupArrivedAt: Long?,
    val startedAt: Long?,
    val endedAt: Long?,
    val pickupMeters: Long,
    val paidMeters: Long,
)

data class ShiftFacts(
    val startedAt: Long,
    val endedAt: Long?,
    val pausedMillis: Long,
    /** Pausado agora, desde quando (a pausa em andamento também não conta como online). */
    val pausedAt: Long? = null,
    val startOdometerKm: Int?,
    val endOdometerKm: Int?,
    val gpsMeters: Long,
)
