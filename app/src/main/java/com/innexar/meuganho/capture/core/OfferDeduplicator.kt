package com.innexar.meuganho.capture.core

/**
 * A mesma oferta gera dezenas de eventos de acessibilidade (contagem regressiva,
 * animações). Consideramos duplicata a mesma impressão digital dentro da janela.
 * Memória limitada: entradas antigas são descartadas a cada chamada.
 */
class OfferDeduplicator(private val windowMs: Long = 90_000L) {

    private val lastSeen = LinkedHashMap<String, Long>()

    @Synchronized
    fun isDuplicate(fingerprint: String, now: Long): Boolean {
        lastSeen.entries.removeAll { now - it.value > windowMs }
        val seen = lastSeen[fingerprint]
        lastSeen[fingerprint] = now
        return seen != null
    }
}
