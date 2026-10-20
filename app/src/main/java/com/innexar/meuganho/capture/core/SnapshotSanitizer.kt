package com.innexar.meuganho.capture.core

/**
 * Remove dados pessoais de um snapshot antes de guardá-lo ou enviá-lo.
 *
 * Mascaramento POR PALAVRA. Mantém:
 * - números, valores, unidades e símbolos;
 * - palavras-chave das plataformas e o vocabulário comum de interface ([UI_VOCABULARY]);
 * - palavras em minúsculas (texto de interface em PT-BR é minúsculo após a 1ª palavra);
 * - a 1ª palavra da linha quando a seguinte é minúscula ("Iniciar viagem", "Rua das…").
 * Mascara ("‹texto›"): nomes próprios e demais palavras capitalizadas ("Buscando Maria"
 * → "Buscando ‹texto›"; "Rua das Flores, 123" → "Rua das ‹texto› 123").
 *
 * Texto livre (ex.: recado do passageiro) é mascarado por inteiro: linhas longas com
 * muitas palavras desconhecidas viram "‹texto livre›".
 */
object SnapshotSanitizer {

    const val MASK = "‹texto›"
    const val FREE_TEXT_MASK = "‹texto livre›"
    private val categories = setOf("uberx", "comfort", "black", "flash", "moto", "pop", "top")

    private val UI_VOCABULARY = setOf(
        "chegada", "prevista", "finalizar", "iniciar", "cheguei", "embarque", "aguarde", "ganhos", "central",
        "r$", "km", "m", "min", "h", "de", "a", "o", "e", "da", "do", "em", "para", "com", "ate", "no", "na",
        "aceitar", "recusar", "confirmar", "cancelar", "iniciar", "finalizar", "concluir", "chegar", "cheguei",
        "voltar", "fechar", "ok", "ganhos", "hoje", "semana", "online", "offline", "viagem", "viagens", "corrida",
        "corridas", "passageiro", "passageira", "destino", "embarque", "desembarque", "parada", "paradas",
        "pagamento", "dinheiro", "cartao", "pix", "tarifa", "dinamico", "dinamica", "promocao", "avaliar",
        "avaliacao", "avalie", "navegar", "mapa", "pausar", "conta", "carteira", "mensagem", "mensagens",
        "ligar", "chat", "rota", "taxa", "distancia", "tempo", "total", "valor", "voce", "recebeu", "ganhou",
        "buscar", "buscando", "aguardando", "esperando", "deixando", "procurando", "cancelada", "cancelado",
        "concluida", "finalizada", "detalhes", "resumo", "incluido", "estimado", "estimativa", "preco", "bonus",
    )

    fun sanitize(snapshot: ScreenSnapshot, knownKeywords: List<String>): String {
        val vocabulary = knownKeywords.flatMap { it.split(' ') }.filter { it.isNotBlank() }.toSet()
        return snapshot.lines.joinToString("\n") { line ->
            val text = sanitizeLine(line.text, vocabulary)
            if (line.viewId != null) "$text @id:${line.viewId}" else text
        }
    }

    fun sanitizeLine(text: String, platformVocabulary: Set<String>): String {
        val vocabulary = platformVocabulary + UI_VOCABULARY
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val bare = tokens.map { TextPatterns.normalize(it).trim('(', ')', ',', '.', ':', ';', '!', '?', '·', '•', '"') }
        val unknownWords = bare.count { it.any(Char::isLetter) && it !in vocabulary }
        if (tokens.size >= 6 && unknownWords * 2 > tokens.size) return FREE_TEXT_MASK

        val out = mutableListOf<String>()
        tokens.forEachIndexed { i, token ->
            val b = bare[i]
            val keep = when {
                b.isEmpty() || b.any(Char::isDigit) || b.none(Char::isLetter) -> true
                b in vocabulary -> true
                token.none(Char::isUpperCase) -> true // palavra de interface em minúsculas
                // Só siglas curtas (X, VIP, XL). Real: nomes em maiúsculas ("FULANO") vazavam com a regra antiga.
                token.length <= 3 && token.filter(Char::isLetter).all(Char::isUpperCase) -> true
                b in categories -> true
                i == 0 && tokens.getOrNull(1)?.let { next -> next.any(Char::isLetter) && next.none(Char::isUpperCase) } == true -> true
                // Botão de uma palavra: verbos no infinitivo ("Selecionar", "Recusar") não são nomes.
                tokens.size == 1 && b.length in 5..14 && (b.endsWith("ar") || b.endsWith("er") || b.endsWith("ir")) -> true
                else -> false
            }
            val piece = if (keep) token else MASK
            if (!(piece == MASK && out.lastOrNull() == MASK)) out += piece
        }
        return out.joinToString(" ")
    }

    fun keywordsOf(patterns: PlatformPatterns): List<String> = with(patterns) {
        offer.acceptKeywords + offer.pickupAnchors + offer.tripAnchors + offer.promoKeywords + offer.categories +
            screens.toPickup + screens.waiting + screens.inTrip + screens.completed +
            screens.cancelled + screens.dailyEarnings + screens.idle
    }
}
