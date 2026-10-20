package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Platform

/** Por que uma tela virou amostra de calibração. */
enum class SampleReason { OFFER, OFFER_UNPARSED, UNKNOWN, SCREEN, OFFER_MISSED }

/**
 * Decide quais telas viram amostra, para calibrar sem inundar o servidor nem a bateria:
 * - OFFER: toda oferta nova (já deduplicada), no máximo 1 a cada 15 s;
 * - OFFER_UNPARSED e UNKNOWN: conteúdo inédito, no máximo 1 por minuto por plataforma
 *   (são as telas mais valiosas: mostram o que o parser ainda não entende);
 * - SCREEN (demais tipos): 1 a cada 10 min por plataforma e tipo.
 */
class SamplingPolicy(
    private val offerIntervalMs: Long = 3_000, // rajadas de ofertas: cada uma vale como amostra
    private val unknownIntervalMs: Long = 60_000,
    private val screenIntervalMs: Long = 10 * 60_000,
) {
    private val lastByKey = HashMap<String, Long>()
    private val seenHashes = LinkedHashSet<Int>()

    @Synchronized
    fun shouldSample(platform: Platform, type: ScreenType, reason: SampleReason, contentHash: Int, now: Long): Boolean {
        val interval = when (reason) {
            SampleReason.OFFER -> offerIntervalMs
            SampleReason.OFFER_MISSED -> 0L
            SampleReason.OFFER_UNPARSED, SampleReason.UNKNOWN -> unknownIntervalMs
            SampleReason.SCREEN -> screenIntervalMs
        }
        if ((reason == SampleReason.UNKNOWN || reason == SampleReason.OFFER_UNPARSED) && contentHash in seenHashes) return false
        val key = "$platform|$type|$reason"
        val last = lastByKey[key]
        if (last != null && now - last < interval) return false
        lastByKey[key] = now
        seenHashes += contentHash
        if (seenHashes.size > MAX_HASHES) seenHashes.remove(seenHashes.first())
        return true
    }

    private companion object {
        const val MAX_HASHES = 300
    }
}
