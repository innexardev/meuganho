package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Captured
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.FieldSource

/** Campos brutos extraídos de uma tela de oferta, cada um com sua confiança. */
data class ExtractedOffer(
    val price: Captured<Long>,
    val surgeCents: Long?,
    val pickupMeters: Captured<Int>,
    val pickupSeconds: Captured<Int>,
    val tripMeters: Captured<Int>,
    val tripSeconds: Captured<Int>,
    val category: String?,
    val warnings: List<String>,
    /** R$/km exibido pelo próprio app no cartão, quando existe. */
    val shownCentsPerKm: Long? = null,
)

/**
 * Estratégia de extração por âncoras, compartilhada por Uber e 99 (as palavras
 * de cada uma vêm de [OfferPatterns]).
 *
 * Preço: preferimos view com id de preço, depois linha que contém só o preço.
 * Nunca pegamos simplesmente "o primeiro R$ da tela": promoções, metas e o
 * acréscimo da dinâmica ("+R$ 5,00") também aparecem na mesma tela.
 *
 * Distância/tempo: procuramos "X min" e "Y km" na linha da âncora ("de distância",
 * "viagem"...). Se estiverem em linhas vizinhas, a confiança cai para MEDIUM;
 * sem âncora nenhuma, assumimos a ordem busca -> viagem com confiança LOW.
 */
class AnchoredOfferExtractor(private val patterns: OfferPatterns) {

    fun extract(lines: List<ScreenLine>): ExtractedOffer {
        val warnings = mutableListOf<String>()
        val shownRate = lines.firstNotNullOfOrNull { TextPatterns.platformRatePerKm(it.text) }
        // Linhas de promoção e o "R$1,58/km aprox." nunca são candidatas a preço.
        val usable = lines.filterNot {
            TextPatterns.containsAny(it.normalized, patterns.promoKeywords) || TextPatterns.platformRatePerKm(it.text) != null
        }
        val promoMoneyIgnored = lines.any { it !in usable && TextPatterns.moneyIn(it.text).isNotEmpty() }

        val price = extractPrice(usable, warnings)
        // Promoção ignorada só é problema se o preço não foi lido com segurança.
        if (promoMoneyIgnored && price.confidence != Confidence.HIGH) {
            warnings += "Valor de promoção/meta na mesma tela"
        }
        val surge = usable.flatMap { TextPatterns.moneyIn(it.text) }.firstOrNull { it.isAddition }?.cents

        // Botões ("Aceitar viagem") contêm palavras de âncora mas nunca números da oferta.
        fun isAnchor(line: ScreenLine, anchors: List<String>, viewIds: List<String>) =
            (line.viewId != null && viewIds.any { line.viewId.contains(it) }) ||
                (TextPatterns.containsAny(line.normalized, anchors) &&
                    !TextPatterns.containsAny(line.normalized, patterns.acceptKeywords))

        val pickupIdx = usable.indexOfFirst { isAnchor(it, patterns.pickupAnchors, patterns.pickupViewIds) }
        val tripIdx = usable.indices.firstOrNull {
            it != pickupIdx && isAnchor(usable[it], patterns.tripAnchors, patterns.tripViewIds)
        } ?: -1

        var pickup = pickupIdx.takeIf { it >= 0 }?.let { pairAround(usable, it) }
        var trip = tripIdx.takeIf { it >= 0 }?.let { pairAround(usable, it) }

        if (pickup == null || trip == null) {
            val loose = usable.mapNotNull { pairIn(it.normalized) }
            if (trip == null && pickup == null && loose.size >= 2) {
                // Exatamente dois pares no cartão: o TOTAL (base do R$/km e R$/h) é certo mesmo que
                // a ordem busca/viagem estivesse trocada -> MEDIUM. Mais de dois pares: ambíguo -> LOW.
                val level = if (loose.size == 2) Confidence.MEDIUM else Confidence.LOW
                pickup = loose[0].copy(confidence = level, source = FieldSource.REGEX)
                trip = loose[1].copy(confidence = level, source = FieldSource.REGEX)
                if (level == Confidence.LOW) warnings += "Busca/viagem identificadas pela ordem, sem âncora"
            } else if (trip == null && pickup != null) {
                // Achamos a busca pela âncora; a viagem é o outro par tempo/distância da tela.
                val found = pickup
                trip = loose.firstOrNull { it.seconds != found.seconds || it.meters != found.meters }
                    ?.copy(confidence = Confidence.LOW, source = FieldSource.REGEX)
            } else if (pickup == null && trip != null) {
                val found = trip
                pickup = loose.firstOrNull { it.seconds != found.seconds || it.meters != found.meters }
                    ?.copy(confidence = Confidence.LOW, source = FieldSource.REGEX)
            }
        }

        val category = usable.firstNotNullOfOrNull { line ->
            patterns.categories.firstOrNull { cat -> line.normalized == cat || line.normalized.startsWith("$cat ") }
                ?.let { line.text.trim() }
        }

        return ExtractedOffer(
            price = price,
            surgeCents = surge,
            pickupMeters = pickup.metersField("km até passageiro"),
            pickupSeconds = pickup.secondsField("tempo até passageiro"),
            tripMeters = trip.metersField("km da viagem"),
            tripSeconds = trip.secondsField("tempo da viagem"),
            category = category,
            warnings = warnings,
            shownCentsPerKm = shownRate,
        )
    }

    private fun extractPrice(lines: List<ScreenLine>, warnings: MutableList<String>): Captured<Long> {
        // "Aceitar por R$13,07" (99 Negocia): o botão de aceite diz exatamente o valor da corrida.
        // Os botões de contraproposta ("R$13,72", "R$14,38") também são preços isolados e seriam ambíguos.
        lines.firstOrNull { TextPatterns.containsAny(it.normalized, patterns.acceptKeywords) }?.let { line ->
            TextPatterns.moneyIn(line.text).singleOrNull { !it.isAddition }?.let {
                return Captured(it.cents, FieldSource.TEXT_ANCHOR, Confidence.HIGH, listOf("valor do botão de aceite"))
            }
        }
        lines.firstOrNull { l -> l.viewId != null && patterns.priceViewIds.any { l.viewId.contains(it) } }?.let { line ->
            TextPatterns.moneyIn(line.text).firstOrNull { !it.isAddition }?.let {
                return Captured(it.cents, FieldSource.NODE_ID, Confidence.HIGH)
            }
        }
        lines.firstOrNull { TextPatterns.isPriceRange(it.text) }?.let { line ->
            val low = TextPatterns.moneyIn(line.text).first()
            warnings += "Preço em faixa; usado o menor valor"
            return Captured(low.cents, FieldSource.REGEX, Confidence.LOW, listOf("faixa de preço"))
        }
        val standalone = lines.filter { TextPatterns.isStandalonePrice(it.text) }
            .map { TextPatterns.moneyIn(it.text).first().cents }.distinct()
        if (standalone.size == 1) return Captured(standalone[0], FieldSource.TEXT_ANCHOR, Confidence.HIGH)
        if (standalone.size > 1) {
            return Captured(standalone[0], FieldSource.TEXT_ANCHOR, Confidence.LOW, listOf("vários valores isolados na tela"))
        }
        val embedded = lines.flatMap { TextPatterns.moneyIn(it.text) }.filterNot { it.isAddition }.map { it.cents }.distinct()
        return when (embedded.size) {
            0 -> Captured.missing("valor não encontrado")
            1 -> Captured(embedded[0], FieldSource.REGEX, Confidence.MEDIUM, listOf("valor dentro de texto"))
            else -> Captured(embedded[0], FieldSource.REGEX, Confidence.LOW, listOf("vários valores no texto"))
        }
    }

    private data class TimeDistance(val seconds: Int?, val meters: Int?, val confidence: Confidence, val source: FieldSource)

    private fun pairIn(normalized: String): TimeDistance? {
        val s = TextPatterns.durationSeconds(normalized)
        val m = TextPatterns.distanceMeters(normalized)
        return if (s != null && m != null) TimeDistance(s, m, Confidence.HIGH, FieldSource.TEXT_ANCHOR) else null
    }

    /** Tempo e distância na linha da âncora; se faltar algo, completa com as vizinhas (MEDIUM). */
    private fun pairAround(lines: List<ScreenLine>, index: Int): TimeDistance? {
        pairIn(lines[index].normalized)?.let { return it }
        val window = listOfNotNull(lines.getOrNull(index - 1), lines[index], lines.getOrNull(index + 1))
        val seconds = window.firstNotNullOfOrNull { TextPatterns.durationSeconds(it.normalized) }
        val meters = window.firstNotNullOfOrNull { TextPatterns.distanceMeters(it.normalized) }
        if (seconds == null && meters == null) return null
        return TimeDistance(seconds, meters, Confidence.MEDIUM, FieldSource.TEXT_ANCHOR)
    }

    private fun TimeDistance?.metersField(label: String): Captured<Int> =
        if (this?.meters == null) Captured.missing("$label não encontrado") else Captured(meters, source, confidence)

    private fun TimeDistance?.secondsField(label: String): Captured<Int> =
        if (this?.seconds == null) Captured.missing("$label não encontrado") else Captured(seconds, source, confidence)
}
