package com.innexar.meuganho.domain.financial

/** Taxas em centavos. Retornam null quando o denominador é zero ou desconhecido. */
object Rates {
    // Arredondamento (não truncamento): mostra R$ 1,58/km como o próprio Uber, não 1,57.
    fun centsPerKm(cents: Long, meters: Long): Long? =
        if (meters <= 0) null else Math.floorDiv(cents * 1000 + meters / 2, meters)

    fun centsPerHour(cents: Long, seconds: Long): Long? =
        if (seconds <= 0) null else Math.floorDiv(cents * 3600 + seconds / 2, seconds)
}
