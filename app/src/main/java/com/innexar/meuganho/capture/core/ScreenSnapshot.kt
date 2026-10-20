package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.CaptureChannel

/** Uma linha de texto visível na tela, na ordem de leitura da árvore de acessibilidade. */
data class ScreenLine(
    val text: String,
    /** Id da view sem o prefixo do pacote (ex.: "fare_text"). Mais estável que o texto. */
    val viewId: String? = null,
    /**
     * Bloco clicável mais próximo que contém esta linha (-1 = nenhum). Cartões de
     * oferta são blocos clicáveis: linhas do mesmo cartão compartilham o grupo.
     */
    val group: Int = -1,
) {
    val normalized: String by lazy { TextPatterns.normalize(text) }
}

/**
 * Fotografia textual de uma tela. É o único formato que os parsers recebem:
 * serviços Android montam o snapshot, parsers só processam texto (testável em JVM).
 */
data class ScreenSnapshot(
    val packageName: String,
    val appVersionCode: Long,
    val capturedAt: Long,
    val lines: List<ScreenLine>,
    val channel: CaptureChannel = CaptureChannel.ACCESSIBILITY,
    /** Radiografia técnica da leitura (tamanho da árvore, cortes). Sem conteúdo de tela. */
    val debug: String = "",
) {
    /** Hash do conteúdo para ignorar a mesma tela repetida por eventos consecutivos. */
    val contentHash: Int by lazy { lines.joinToString("\n") { it.text }.hashCode() }

    companion object {
        /** Tela inteira sem app conhecido (iPhone: Transmissão de tela). A plataforma vem do texto. */
        const val WHOLE_SCREEN = "screen:whole"

        /** Monta um snapshot a partir de texto (fixtures de teste, uma linha por item). */
        fun fromText(packageName: String, text: String, capturedAt: Long = 0L) = ScreenSnapshot(
            packageName = packageName,
            appVersionCode = 0,
            capturedAt = capturedAt,
            lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map(::parseFixtureLine),
        )
    }
}

/** Formato de fixture: "texto", opcionalmente com " @id:view_id" e/ou " @g:N" (grupo/cartão). */
private fun parseFixtureLine(raw: String): ScreenLine {
    var text = raw
    val group = Regex(""" @g:(\d+)""").find(text)?.also { text = text.removeRange(it.range) }?.groupValues?.get(1)?.toInt() ?: -1
    val id = Regex(""" @id:(\S+)""").find(text)?.also { text = text.removeRange(it.range) }?.groupValues?.get(1)
    return ScreenLine(text.trim(), id, group)
}

enum class ScreenType {
    EARNINGS_HISTORY, OFFER, TO_PICKUP, WAITING_PASSENGER, IN_TRIP, TRIP_COMPLETED, TRIP_CANCELLED, DAILY_EARNINGS, IDLE, UNKNOWN,
    /** Navegando sem frase de fase (o Uber muitas vezes só mostra o mapa e a manobra). */
    NAVIGATING,
    /** Navegação com o carro parado (sinal, embarque): mantém a corrida viva, mas não indica embarque. */
    NAVIGATING_STOPPED,
}
