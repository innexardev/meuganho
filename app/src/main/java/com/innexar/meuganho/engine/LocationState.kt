package com.innexar.meuganho.engine

import com.innexar.meuganho.core.geo.Geohash

/**
 * Última posição conhecida do GPS do turno, só em memória. Usada para marcar a
 * REGIÃO aproximada (célula de ~1 km) de ofertas e fim de corridas.
 */
object LocationState {
    private const val MAX_AGE_MS = 3 * 60_000L

    @Volatile private var cell: String? = null
    @Volatile private var at: Long = 0

    fun update(latitude: Double, longitude: Double, time: Long) {
        cell = Geohash.encode(latitude, longitude)
        at = time
    }

    /** Célula atual, ou null se o GPS está desligado ou a posição é antiga demais. */
    fun currentCell(now: Long = System.currentTimeMillis()): String? = cell?.takeIf { now - at <= MAX_AGE_MS }
}
