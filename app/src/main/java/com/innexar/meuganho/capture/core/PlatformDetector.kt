package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Platform

/**
 * De qual app é esta tela, só pelo TEXTO? No iPhone (Transmissão de tela + OCR) não existe pacote:
 * a imagem é a tela inteira. Marcas de cada app tiradas das telas reais (28/09–01/10/2026).
 * Empate ou nenhuma marca = null (a tela é ignorada: melhor perder uma leitura que atribuir ao app errado).
 */
object PlatformDetector {
    private val uber = listOf(
        "/km aprox", "uberx", "uber x", "comfort", "radar de viagens", "encontro com", "destino de ", "procurando viagens",
        "voce esta online", "voce esta offline", "pesquisar locais", "tendencias de ganhos", "historico de ganhos",
        "coletar pagamento", "aguardando o usuario", "filtro de destino", "nao e possivel ficar offline", "selecionar",
        "pessoa convidada", "viagem longa", "exclusivo", "nenhuma solicitacao",
    )
    private val ninetyNine = listOf(
        " corridas", "cheguei no embarque", "iniciar corrida", "finalizar corrida", "chegada prevista", "central de ganhos",
        "corrida aceita", "como foi sua corrida", "avaliar como anonimo", "solicitacoes seguidas", "procurando corridas",
        "ganhos do dia", "corrida em dinheiro", "passageiro a caminho", "aceitar por", "negocia", "99pop", "conta99",
        "embarque distante", "cartao verif", "o passageiro cancelou a corrida", "passageiro confirmando",
        "extrato", "saldo r$", "99multiplica", "compensacao aprovada", "mapa do ",
    )
    private val multiplier = Regex("""\d,\dx""")

    fun detect(lines: List<ScreenLine>): Platform? {
        val text = lines.joinToString("\n") { " " + it.normalized }
        val u = uber.count { text.contains(it) }
        val n = ninetyNine.count { text.contains(it) } +
            (if (multiplier.containsMatchIn(text)) 1 else 0) +
            (if (lines.any { it.normalized == "buscando" }) 1 else 0)
        return when {
            u == 0 && n == 0 -> null
            u > n -> Platform.UBER
            n > u -> Platform.NINETY_NINE
            else -> null
        }
    }
}
