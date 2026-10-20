package com.innexar.meuganho.domain.trip

import kotlin.math.abs

/**
 * Qual oferta virou esta corrida? Função pura, usada pelo conciliador.
 *
 * - Corrida com valor OFICIAL (histórico/total do dia): a oferta de mesmo valor (±3% ou R$ 0,30)
 *   vista até [OFFICIAL_BEFORE_MS] antes; entre várias, a mais próxima no tempo.
 * - Corrida sem valor oficial: a última oferta da plataforma vista até [LIVE_BEFORE_MS] antes do aceite.
 * Ofertas já ligadas a outra corrida nunca entram.
 */
object OfferMatcher {
    data class Candidate(val offerId: Long, val detectedAt: Long, val cents: Long?)

    const val OFFICIAL_BEFORE_MS = 60 * 60_000L
    const val LIVE_BEFORE_MS = 3 * 60_000L
    const val AFTER_MS = 3 * 60_000L
    const val AMBIGUOUS_MS = 20_000L

    fun match(tripAt: Long, officialCents: Long?, candidates: List<Candidate>): Candidate? {
        if (officialCents != null) {
            return candidates
                .filter { it.detectedAt in (tripAt - OFFICIAL_BEFORE_MS)..(tripAt + AFTER_MS) }
                .filter { c -> c.cents != null && sameValue(c.cents, officialCents) }
                .minByOrNull { abs(tripAt - it.detectedAt) }
        }
        val recent = candidates.filter { it.detectedAt in (tripAt - LIVE_BEFORE_MS)..(tripAt + 10_000L) }
            .sortedByDescending { it.detectedAt }
        // Várias ofertas juntas (Radar) sem valor oficial: não dá para saber qual foi. Espera o oficial.
        if (recent.size >= 2 && recent[0].detectedAt - recent[1].detectedAt < AMBIGUOUS_MS) return null
        return recent.firstOrNull()
    }

    fun sameValue(a: Long, b: Long): Boolean = abs(a - b) <= maxOf(30L, b * 3 / 100)
}
