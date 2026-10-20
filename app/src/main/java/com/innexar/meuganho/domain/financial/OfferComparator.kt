package com.innexar.meuganho.domain.financial

import com.innexar.meuganho.domain.model.OfferRating

/**
 * Qual oferta é melhor quando chegam várias ao mesmo tempo (Uber x 99, Radar).
 *
 * Primeiro a classificação pessoal (EXCELENTE > ACEITÁVEL > RUIM), depois o R$/h:
 * entre duas ofertas simultâneas, o recurso escasso é o tempo. R$/km desempata.
 * Oferta INCERTA nunca é recomendada: não se recomenda com base em dado duvidoso.
 */
object OfferComparator {

    fun bestIndex(analyses: List<OfferAnalysis>): Int? {
        val eligible = analyses.withIndex().filter { it.value.rating != OfferRating.UNCERTAIN }
        return eligible.maxWithOrNull(
            compareBy<IndexedValue<OfferAnalysis>>({ rank(it.value.rating) }, { it.value.centsPerHour ?: 0 }, { it.value.centsPerKm ?: 0 })
        )?.index
    }

    private fun rank(r: OfferRating) = when (r) {
        OfferRating.EXCELLENT -> 3
        OfferRating.ACCEPTABLE -> 2
        OfferRating.POOR -> 1
        OfferRating.UNCERTAIN -> 0
    }
}
