package com.innexar.meuganho.capture.uber

import com.innexar.meuganho.capture.core.KeywordPlatformParser
import com.innexar.meuganho.domain.model.Platform

/**
 * Uber V1. Na tela de oferta do Uber o preço costuma vir isolado numa linha e
 * busca/viagem aparecem como "8 min (3,2 km) de distância" e "Viagem de 15 min (7,1 km)".
 */
class UberParserV1 : KeywordPlatformParser(UberPatterns.v1) {
    override val platform = Platform.UBER
    override val version = "uber-v1"

    /**
     * Telas reais (29/09/2026) sem frase de fase: o Uber só navega (manobra, "2 min · 0,4 km",
     * "Pessoa convidada de"). Sem isso a corrida inteira passava em branco.
     */
    override fun classify(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot): com.innexar.meuganho.capture.core.ScreenType {
        val ids = snapshot.lines.mapNotNull { it.viewId }
        // Lista "Atividade" do histórico: valor + detalhes + horário por corrida.
        if (ids.any { it.contains("activity_feed") || it.contains("styled_icon_link_tertiary") } &&
            ids.any { it.contains("styled_icon_link_subtitle") }
        ) return com.innexar.meuganho.capture.core.ScreenType.EARNINGS_HISTORY
        // "Procurando viagens" no rótulo principal = online SEM corrida, mesmo com rota na tela
        // (navegando por conta própria, "Filtro de destino"). Real (30/09/2026): virava "em viagem".
        val phaseLabel = snapshot.lines.any { it.normalized.startsWith("encontro com") || it.normalized.startsWith("destino de ") }
        if (snapshot.lines.any { it.viewId?.contains("key_info_label") == true && it.normalized.startsWith("procurando viagens") } ||
            (!phaseLabel && snapshot.lines.none { it.viewId != null } && snapshot.lines.any { it.normalized.startsWith("procurando viagens") } &&
                super.classify(snapshot) != com.innexar.meuganho.capture.core.ScreenType.OFFER) // oferta por cima da busca continua oferta
        ) {
            return com.innexar.meuganho.capture.core.ScreenType.IDLE
        }
        val base = super.classify(snapshot)
        if (base != com.innexar.meuganho.capture.core.ScreenType.UNKNOWN &&
            base != com.innexar.meuganho.capture.core.ScreenType.IDLE &&
            base != com.innexar.meuganho.capture.core.ScreenType.DAILY_EARNINGS
        ) return base
        val navigating = ids.any { it.contains("nav_maneuver") } ||
            snapshot.lines.any { it.viewId?.contains("status_label_leading") == true && navEta.matches(it.normalized) } ||
            // Só texto (iPhone/OCR): "2 min" + "0,4 km" da barra de navegação.
            (ids.isEmpty() && snapshot.lines.any { navEta.matches(it.normalized) } && snapshot.lines.any { navKm.matches(it.normalized) })
        val online = snapshot.lines.any {
            it.normalized.contains("voce esta online") || it.normalized.contains("nenhuma solicitacao") || it.normalized.contains("procurando viagens")
        }
        return if (navigating && !online) com.innexar.meuganho.capture.core.ScreenType.NAVIGATING else base
    }

    /** "Coletar pagamento R$ 38,96" é o dinheiro a cobrar do passageiro, não o ganho: fim sem valor. */
    override fun parseCompletedValue(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot) =
        if (snapshot.lines.any { it.normalized.contains("coletar pagamento") }) null else super.parseCompletedValue(snapshot)

    private val navKm = Regex("""^\d+([,.]\d+)?\s?(km|m)$""")
    private val navEta = Regex("""^\d+ min$""")

    override fun parseEarningsHistory(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot, carriedDate: java.time.LocalDate?) =
        UberEarningsHistoryParser.parse(snapshot.lines, java.time.LocalDate.now(), carriedDate)

    /** "R$ 88,14" com id ub__earnings_tracker_counter_content_status e o rótulo "HOJE" (real, 29/09/2026). */
    override fun dayTracker(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot): Long? {
        // Rótulo "HOJE", ou SEM rótulo (contador recolhido): real (30/09/2026) "R$ 32,80" sem rótulo era o
        // total do dia (19,81 + 7,16 + 5,83). Outro rótulo (semana, outro período) não conta.
        val label = snapshot.lines.firstOrNull { it.viewId?.contains("earnings_tracker_label") == true }
        if (label != null && label.normalized != "hoje") return null
        if (snapshot.lines.none { it.viewId != null }) return textDayTracker(snapshot)
        return snapshot.lines.firstOrNull { it.viewId?.contains("earnings_tracker_counter") == true }
            ?.let { com.innexar.meuganho.capture.core.TextPatterns.moneyIn(it.text).firstOrNull()?.cents }
    }

    override fun lastHistoryDate(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot) =
        UberEarningsHistoryParser.lastDate(snapshot.lines, java.time.LocalDate.now())

    /**
     * Total do dia na tela inicial, só pelo TEXTO (iPhone/OCR): tela inicial ("Página inicial"/"Pesquisar locais")
     * e o primeiro preço "cheio" (não "+R$" do mapa). Rótulo de outro período (semana) não conta.
     */
    private fun textDayTracker(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot): Long? {
        val n = snapshot.lines.map { it.normalized }
        if (n.none { it == "pagina inicial" || it == "pesquisar locais" }) return null
        if (n.any { it == "semana" || it == "esta semana" } || classify(snapshot) == com.innexar.meuganho.capture.core.ScreenType.OFFER) return null
        return snapshot.lines.firstOrNull { com.innexar.meuganho.capture.core.TextPatterns.isStandalonePrice(it.text) }
            ?.let { com.innexar.meuganho.capture.core.TextPatterns.moneyIn(it.text).first().cents }
    }
}
