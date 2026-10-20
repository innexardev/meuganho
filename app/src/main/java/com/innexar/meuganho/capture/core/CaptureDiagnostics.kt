package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlatformDiagnostics(
    val lastEventAt: Long? = null,
    val lastScreenType: ScreenType? = null,
    val appVersionCode: Long? = null,
    val parserVersion: String? = null,
    val eventsLast5Min: Int = 0,
    val lastOfferSummary: String? = null,
    val lastOfferAt: Long? = null,
    val lastParserError: String? = null,
    val lastParserErrorAt: Long? = null,
    val duplicatesIgnored: Int = 0,
    /** Radiografia da última leitura (tamanho da árvore, cortes, janelas). */
    val lastTree: String? = null,
    /** Leituras por imagem (OCR): sucessos, falhas e tempo médio. */
    val ocrOk: Int = 0,
    val ocrFailed: Int = 0,
    val ocrAvgMs: Long = 0,
    val offersFound: Int = 0,
    /** Sons detectados com a plataforma na tela (gatilho sonoro de oferta). */
    val soundTriggers: Int = 0,
    /** Toques de oferta (fora de corrida) com oferta lida em seguida / sem oferta lida: a taxa de acerto real. */
    val soundOffersRead: Int = 0,
    val soundOffersMissed: Int = 0,
    /** Telas descartadas por fila cheia (nunca deveria passar de zero). */
    val queueDropped: Int = 0,
)

/** Estado em memória para a tela de Diagnóstico. Nada disso vai para o banco. */
class CaptureDiagnostics {

    private val _platforms = MutableStateFlow<Map<Platform, PlatformDiagnostics>>(emptyMap())
    val platforms: StateFlow<Map<Platform, PlatformDiagnostics>> = _platforms.asStateFlow()

    private val _recorded = MutableStateFlow<String?>(null)
    /** Última tela gravada sob demanda (já sanitizada) para virar fixture de teste. */
    val recorded: StateFlow<String?> = _recorded.asStateFlow()

    @Volatile var recordingArmed: Boolean = false
        private set

    private val eventTimes = mutableMapOf<Platform, ArrayDeque<Long>>()

    fun onEvent(platform: Platform, type: ScreenType, appVersion: Long, parserVersion: String, now: Long, tree: String = "") {
        val count = synchronized(eventTimes) {
            val times = eventTimes.getOrPut(platform) { ArrayDeque() }
            times.addLast(now)
            while (times.isNotEmpty() && now - times.first() > FIVE_MIN_MS) times.removeFirst()
            times.size
        }
        update(platform) {
            it.copy(lastEventAt = now, lastScreenType = type, appVersionCode = appVersion,
                parserVersion = parserVersion, eventsLast5Min = count, lastTree = tree.ifEmpty { it.lastTree })
        }
    }

    fun onOffer(platform: Platform, summary: String, now: Long) =
        update(platform) { it.copy(lastOfferSummary = summary, lastOfferAt = now, offersFound = it.offersFound + 1) }

    fun onParserError(platform: Platform, message: String, now: Long) =
        update(platform) { it.copy(lastParserError = message, lastParserErrorAt = now) }

    fun onOcr(platform: Platform, ok: Boolean, tookMs: Long) = update(platform) {
        val n = it.ocrOk + it.ocrFailed
        it.copy(
            ocrOk = it.ocrOk + if (ok) 1 else 0,
            ocrFailed = it.ocrFailed + if (ok) 0 else 1,
            ocrAvgMs = if (n == 0) tookMs else (it.ocrAvgMs * n + tookMs) / (n + 1),
        )
    }

    fun onSoundTrigger(platform: Platform) = update(platform) { it.copy(soundTriggers = it.soundTriggers + 1) }

    fun onSoundOutcome(platform: Platform, read: Boolean) = update(platform) {
        if (read) it.copy(soundOffersRead = it.soundOffersRead + 1) else it.copy(soundOffersMissed = it.soundOffersMissed + 1)
    }

    fun onQueueDropped(platform: Platform) = update(platform) { it.copy(queueDropped = it.queueDropped + 1) }

    fun onDuplicate(platform: Platform) = update(platform) { it.copy(duplicatesIgnored = it.duplicatesIgnored + 1) }

    fun armRecording() {
        recordingArmed = true
        _recorded.value = null
    }

    fun deliverRecording(text: String) {
        recordingArmed = false
        _recorded.value = text
    }

    private fun update(platform: Platform, block: (PlatformDiagnostics) -> PlatformDiagnostics) =
        _platforms.update { it + (platform to block(it[platform] ?: PlatformDiagnostics())) }

    private companion object {
        const val FIVE_MIN_MS = 5 * 60_000L
    }
}
