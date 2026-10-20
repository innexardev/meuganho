package com.innexar.meuganho.capture.core

/** Linhas de uma tela divididas em cartões de oferta. */
data class OfferBlocks(val lines: List<List<ScreenLine>>, val textual: Boolean) {

    companion object {
        /**
         * Separa os cartões de oferta de uma tela.
         * 1. Estrutural: grupos clicáveis (cada cartão é um bloco) com preço e tempo/distância.
         * 2. Textual: fatias que começam em cada preço isolado, se cada fatia tiver tempo/distância.
         * 3. Senão, a tela inteira é uma oferta só.
         */
        fun split(lines: List<ScreenLine>): OfferBlocks {
            val cards = lines.filter { it.group >= 0 }.groupBy { it.group }.values.filter(::looksLikeCard)
            if (cards.size >= 2) return OfferBlocks(cards, textual = false)

            val priceIdx = lines.indices.filter { TextPatterns.isStandalonePrice(lines[it].text) }
            if (priceIdx.size >= 2) {
                val slices = priceIdx.mapIndexed { k, start -> lines.subList(start, priceIdx.getOrNull(k + 1) ?: lines.size) }
                if (slices.all { slice -> slice.any { TextPatterns.hasTimeAndDistance(it.normalized) } }) {
                    return OfferBlocks(slices, textual = true)
                }
            }
            return OfferBlocks(listOf(lines), textual = false)
        }

        private fun looksLikeCard(group: List<ScreenLine>): Boolean =
            group.any { TextPatterns.isStandalonePrice(it.text) } &&
                group.any { TextPatterns.hasTimeAndDistance(it.normalized) }
    }
}
