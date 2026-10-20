package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.Captured
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.FieldSource
import com.innexar.meuganho.domain.model.RideOffer
import com.innexar.meuganho.domain.model.cappedAt

/**
 * Parser-base por palavras-chave. Cada plataforma fornece seus [PlatformPatterns]
 * e pode sobrescrever qualquer etapa quando a tela dela exigir algo específico.
 */
abstract class KeywordPlatformParser(protected val patterns: PlatformPatterns) : PlatformParser {

    private val extractor = AnchoredOfferExtractor(patterns.offer)

    override fun classify(snapshot: ScreenSnapshot): ScreenType {
        val lines = snapshot.lines.map { it.normalized }
        fun has(keywords: List<String>) = lines.any { TextPatterns.containsAny(it, keywords) }
        // Só valores "cheios" contam: "+R$ 6,25" (bônus no mapa, dinâmica) não é preço de oferta.
        val hasMoney = snapshot.lines.any { line -> TextPatterns.moneyIn(line.text).any { !it.isAddition } }
        val s = patterns.screens.let { base ->
            if (RemoteRules.version == 0) base else base.copy(
                toPickup = base.toPickup + RemoteRules.extra(platform, "toPickup"),
                waiting = base.waiting + RemoteRules.extra(platform, "waiting"),
                inTrip = base.inTrip + RemoteRules.extra(platform, "inTrip"),
                completed = base.completed + RemoteRules.extra(platform, "completed"),
                cancelled = base.cancelled + RemoteRules.extra(platform, "cancelled"),
                dailyEarnings = base.dailyEarnings + RemoteRules.extra(platform, "dailyEarnings"),
                idle = base.idle + RemoteRules.extra(platform, "idle"),
                earningsHistory = base.earningsHistory + RemoteRules.extra(platform, "earningsHistory"),
            )
        }
        return when {
            // Título EXATO: a tela inicial do Uber tem um botão "VER HISTÓRICO DE GANHOS", que não é a tela.
            s.earningsHistory.isNotEmpty() && lines.any { it in s.earningsHistory } -> ScreenType.EARNINGS_HISTORY
            hasMoney && has(patterns.offer.acceptKeywords) -> ScreenType.OFFER
            // Botão fora da tela (ou não lido): preço isolado + R$/km do app + tempo/distância já é oferta.
            snapshot.lines.any { TextPatterns.isStandalonePrice(it.text) } &&
                snapshot.lines.any { TextPatterns.platformRatePerKm(it.text) != null } &&
                lines.any { TextPatterns.hasTimeAndDistance(it) } -> ScreenType.OFFER
            has(s.completed) -> ScreenType.TRIP_COMPLETED
            has(s.cancelled) -> ScreenType.TRIP_CANCELLED
            has(s.inTrip) -> ScreenType.IN_TRIP
            has(s.waiting) -> ScreenType.WAITING_PASSENGER
            has(s.toPickup) -> ScreenType.TO_PICKUP
            hasMoney && has(s.dailyEarnings) -> ScreenType.DAILY_EARNINGS
            has(s.idle) -> ScreenType.IDLE
            else -> ScreenType.UNKNOWN
        }
    }

    override fun parseOffers(snapshot: ScreenSnapshot): List<RideOffer> {
        val blocks = OfferBlocks.split(snapshot.lines)
        return blocks.lines.mapNotNull { block -> parseBlock(block, snapshot, textualSplit = blocks.textual) }
    }

    private fun parseBlock(block: List<ScreenLine>, snapshot: ScreenSnapshot, textualSplit: Boolean): RideOffer? {
        val x = extractor.extract(block)
        if (!x.price.isPresent) return null
        var offer = RideOffer(
            platform = platform,
            detectedAt = snapshot.capturedAt,
            // Cartões separados só pelo texto (sem estrutura) não passam de MEDIUM no preço.
            offeredCents = if (textualSplit) x.price.cappedAt(Confidence.MEDIUM) else x.price,
            pickupMeters = x.pickupMeters,
            pickupSeconds = x.pickupSeconds,
            tripMeters = x.tripMeters,
            tripSeconds = x.tripSeconds,
            surgeCents = x.surgeCents,
            category = x.category,
            parserVersion = version,
            sourceAppVersion = snapshot.appVersionCode,
            channel = snapshot.channel,
            warnings = x.warnings,
        )
        // Notificação e OCR não têm estrutura (e o OCR pode errar dígitos): começam em no máximo MEDIUM.
        // A prova do R$/km do app, aplicada depois, pode subir o preço para HIGH: são números
        // independentes na tela que só concordam se foram lidos certo.
        if (snapshot.channel != CaptureChannel.ACCESSIBILITY) {
            offer = offer.copy(
                offeredCents = offer.offeredCents.cappedAt(Confidence.MEDIUM),
                pickupMeters = offer.pickupMeters.cappedAt(Confidence.MEDIUM),
                pickupSeconds = offer.pickupSeconds.cappedAt(Confidence.MEDIUM),
                tripMeters = offer.tripMeters.cappedAt(Confidence.MEDIUM),
                tripSeconds = offer.tripSeconds.cappedAt(Confidence.MEDIUM),
            )
        }
        return PlatformRateCheck.apply(offer, x.shownCentsPerKm)
    }

    /**
     * Valor final: a linha da palavra-chave de conclusão com valor é o sinal mais forte;
     * sem ela, o maior valor isolado (o total costuma ser maior que as parcelas).
     */
    override fun parseCompletedValue(snapshot: ScreenSnapshot): Captured<Long>? =
        valueNear(snapshot, patterns.screens.completed)

    override fun parseDailyTotal(snapshot: ScreenSnapshot): Captured<Long>? =
        valueNear(snapshot, patterns.screens.dailyEarnings)

    private fun valueNear(snapshot: ScreenSnapshot, keywords: List<String>): Captured<Long>? {
        val lines = snapshot.lines.filterNot { TextPatterns.containsAny(it.normalized, patterns.offer.promoKeywords) }
        // Qualquer linha-chave com valor ("Você recebeu R$ 16,40"), ou valor isolado logo abaixo dela.
        lines.forEachIndexed { idx, line ->
            if (!TextPatterns.containsAny(line.normalized, keywords)) return@forEachIndexed
            TextPatterns.moneyIn(line.text).firstOrNull { !it.isAddition }?.let {
                return Captured(it.cents, FieldSource.TEXT_ANCHOR, Confidence.HIGH)
            }
            lines.getOrNull(idx + 1)?.takeIf { TextPatterns.isStandalonePrice(it.text) }?.let { next ->
                return Captured(TextPatterns.moneyIn(next.text).first().cents, FieldSource.TEXT_ANCHOR, Confidence.HIGH)
            }
        }
        val standalone = lines.filter { TextPatterns.isStandalonePrice(it.text) }.map { TextPatterns.moneyIn(it.text).first().cents }
        val max = standalone.maxOrNull() ?: return null
        return Captured(max, FieldSource.REGEX, Confidence.MEDIUM, listOf("maior valor isolado da tela"))
    }
}
