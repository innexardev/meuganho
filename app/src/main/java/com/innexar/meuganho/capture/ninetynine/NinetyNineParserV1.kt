package com.innexar.meuganho.capture.ninetynine

import com.innexar.meuganho.capture.core.KeywordPlatformParser
import com.innexar.meuganho.domain.model.Platform

/**
 * 99 V1. Particularidade: algumas categorias exibem faixa de preço
 * ("R$ 18,00 - R$ 22,00"); o extrator marca isso como LOW, que leva a oferta
 * para revisão em vez de assumir um valor.
 */
class NinetyNineParserV1 : KeywordPlatformParser(NinetyNinePatterns.v1) {
    override val platform = Platform.NINETY_NINE
    override val version = "99-v1"

    /**
     * Navegação da 99 (real, 29/09/2026). A Acessibilidade vê o mapa ("183 m", "Av. X", "17 km/h");
     * pela tela (OCR), a barra recolhida "1 min · 10 m / Chegada prevista: 15:47" sem botão de fase.
     * Só conta com o carro andando (>= 8 km/h): parado no embarque também aparece o mapa.
     */
    override fun classify(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot): com.innexar.meuganho.capture.core.ScreenType {
        if (NinetyNineWalletParser.looksLikeWallet(snapshot.lines)) return com.innexar.meuganho.capture.core.ScreenType.EARNINGS_HISTORY
        val base = super.classify(snapshot)
        if (base != com.innexar.meuganho.capture.core.ScreenType.UNKNOWN &&
            base != com.innexar.meuganho.capture.core.ScreenType.IDLE
        ) return base
        val speed = snapshot.lines.firstOrNull { it.viewId?.contains("current_speed_tv") == true }?.text?.trim()?.toIntOrNull()
        // Mapa da 99 (dois conjuntos de ids reais: share_track_road… e mRoad…, 01/10/2026). Só existe em corrida.
        val navIds = snapshot.lines.any { l -> l.viewId?.let { it.contains("share_track_road") || it.startsWith("mRoad") } == true }
        if (navIds && (speed ?: 0) < 8) return com.innexar.meuganho.capture.core.ScreenType.NAVIGATING_STOPPED
        val navMap = navIds
        val navBar = snapshot.lines.any { it.normalized.contains("chegada prevista") }
        // Segunda navegação da 99 ("navegação leve"), real 30/09/2026: "56 m" / "Vire à esquerda" / "Mapa do …".
        val lightNav = snapshot.lines.any { it.viewId?.contains("maprouter_light_nav") == true }
        if (navMap || navBar || lightNav) return com.innexar.meuganho.capture.core.ScreenType.NAVIGATING
        return textNavigation(snapshot) ?: base
    }

    /**
     * Navegação reconhecida só pelo TEXTO (iPhone/OCR, sem ids): distância da manobra ("183 m", "1,1 km")
     * + velocímetro ("km/h") ou instrução ("Vire à esquerda", "Mapa do …"). Fora da tela inicial ("Buscando").
     */
    private fun textNavigation(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot): com.innexar.meuganho.capture.core.ScreenType? {
        if (snapshot.lines.any { it.viewId != null }) return null
        val n = snapshot.lines.map { it.normalized }
        if (n.any { it == "buscando" || it.startsWith("buscando ") }) return null
        val distance = n.indices.any { i -> navDistance.matches(n[i]) || (n[i].matches(Regex("""\d+(,\d+)?""")) && n.getOrNull(i + 1) in setOf("m", "km")) }
        val cue = n.any { it == "km/h" || it.startsWith("mapa do ") || maneuver.any { m -> it.startsWith(m) } }
        if (!distance || !cue) return null
        val speed = n.indexOf("km/h").takeIf { it > 0 }?.let { n[it - 1].toIntOrNull() }
        return if (speed != null && speed < 8) com.innexar.meuganho.capture.core.ScreenType.NAVIGATING_STOPPED
        else com.innexar.meuganho.capture.core.ScreenType.NAVIGATING
    }

    /** Extrato da Conta99 = histórico oficial da 99. */
    override fun parseEarningsHistory(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot, carriedDate: java.time.LocalDate?) =
        NinetyNineWalletParser.records(NinetyNineWalletParser.parse(snapshot.lines, carriedDate))

    override fun lastHistoryDate(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot) = NinetyNineWalletParser.lastDate(snapshot.lines)

    /**
     * Ganho do dia na TELA INICIAL da 99 (lida por imagem). Real 01/10/2026: "R$0,00" às 07:10,
     * "R$41,60" às 08:11, "R$47,70" às 08:37 (a diferença, R$ 6,10, era a corrida das 08:24).
     * Marca da tela: "Buscando" e/ou multiplicadores "1,1X-1,5X"; o valor é o primeiro preço no topo.
     * Cartão de oferta (tem R$/km) nunca conta. O coordenador ainda exige 2 leituras iguais.
     */
    override fun dayTracker(snapshot: com.innexar.meuganho.capture.core.ScreenSnapshot): Long? {
        if (snapshot.channel != com.innexar.meuganho.domain.model.CaptureChannel.OCR) return null
        val norm = snapshot.lines.map { it.normalized }
        val home = norm.any { it == "buscando" || it.startsWith("buscando ") } || norm.any { multiplier.containsMatchIn(it) }
        if (!home || norm.any { it.contains("/km") || it.contains("central de ganhos") }) return null
        if (classify(snapshot) == com.innexar.meuganho.capture.core.ScreenType.OFFER) return null
        return snapshot.lines.take(8).firstNotNullOfOrNull { l ->
            homeTotal.matchEntire(l.text.trim())?.let { m -> m.groupValues[1].replace(".", "").toLong() * 100 + m.groupValues[2].toLong() }
        }
    }

    private val navDistance = Regex("""^\d+(,\d+)?\s?(m|km)$""")
    private val maneuver = listOf("vire a", "siga ", "mantenha-se", "pegue a", "na rotatoria", "continue ")
    private val multiplier = Regex("""\d,\dx""")
    private val homeTotal = Regex("""^R\$\s?(\d{1,3}(?:\.\d{3})*),(\d{2})\s*[->]?$""")
}
