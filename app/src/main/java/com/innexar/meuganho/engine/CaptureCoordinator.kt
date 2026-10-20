package com.innexar.meuganho.engine

import com.innexar.meuganho.capture.core.CaptureDiagnostics
import com.innexar.meuganho.capture.core.OfferDeduplicator
import com.innexar.meuganho.capture.core.ParserHealthMonitor
import com.innexar.meuganho.capture.core.ParserRegistry
import com.innexar.meuganho.capture.core.SampleReason
import com.innexar.meuganho.capture.core.PlatformParser
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.core.SnapshotSanitizer
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.data.local.dao.PlatformTotalDao
import com.innexar.meuganho.data.local.entity.PlatformTotalEntity
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.financial.OfferAnalyzer
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.trip.TripSignal
import com.innexar.meuganho.domain.validation.OfferAssessment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pipeline de captura, fora da main thread:
 *
 * snapshot -> plataforma -> classificação -> (oferta) parse -> dedup -> validação
 * -> status -> banco -> overlay -> máquina de estados.
 *
 * As telas chegam por uma fila com capacidade fixa; se o sistema produzir eventos
 * mais rápido do que processamos, descartamos os mais antigos em vez de acumular RAM.
 */
class CaptureCoordinator(
    scope: CoroutineScope,
    private val diagnostics: CaptureDiagnostics,
    private val health: ParserHealthMonitor,
    private val recorder: OfferRecorder,
    private val tripTracker: TripTracker,
    private val platformTotalDao: PlatformTotalDao,
    private val costModel: StateFlow<CostModel>,
    private val settings: StateFlow<AppSettings>,
    private val samples: SampleRecorder,
    private val history: HistoryImporter,
    private val reconciler: TripReconciler? = null,
) {
    /** Dia visto na última leitura do histórico (quando a lista é rolada o cabeçalho some). */
    private var historyDate: Pair<java.time.LocalDate, Long>? = null
    // Fila folgada: numa rajada (várias ofertas de Uber e 99 juntas + leituras por imagem) nada se perde.
    // Se mesmo assim encher, a mais antiga sai e isso fica contado no Diagnóstico.
    private val queue = Channel<ScreenSnapshot>(
        capacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { s -> Platform.fromPackage(s.packageName)?.let(diagnostics::onQueueDropped) },
    )

    /** Pedido de nova leitura por imagem: a oferta foi lida pela metade (animação abrindo). */
    private val _ocrRetry = kotlinx.coroutines.flow.MutableSharedFlow<Platform>(extraBufferCapacity = 4)
    val ocrRetry: kotlinx.coroutines.flow.SharedFlow<Platform> = _ocrRetry
    private val retryCount = HashMap<Platform, Pair<Int, Long>>()

    /** Toques de oferta aguardando a oferta ser lida (taxa de acerto real). */
    private val pendingSounds = HashMap<Platform, MutableList<Long>>()
    private val lastOcr = HashMap<Platform, ScreenSnapshot>()
    private val trackerCandidate = HashMap<Platform, Pair<Long, Long>>()
    private val deduplicator = OfferDeduplicator()
    private val lastProcessed = mutableMapOf<String, Pair<Int, Long>>()

    private val _overlayOffers = MutableStateFlow<List<OverlayOffer>>(emptyList())
    val overlayOffers: StateFlow<List<OverlayOffer>> = _overlayOffers.asStateFlow()

    /** Ofertas na tela por plataforma: Uber e 99 ao mesmo tempo aparecem juntas, uma não apaga a outra. */
    private val shownByPlatform = HashMap<Platform, List<OverlayOffer>>()

    /** Valores lidos pela Acessibilidade (confiáveis quanto ao dono), para barrar OCR que pegou a tela de outro app. */
    private val recentScreenOffers = HashMap<Platform, MutableList<ScreenOffer>>()

    private data class ScreenOffer(val cents: Long, val meters: Int?, val at: Long)

    /** Última oferta vinda de notificação, por plataforma+valor: a leitura da tela depois a substitui. */
    private val notificationOffers = HashMap<String, Pair<Long, Long>>()
    private val lastScreenOfferAt = HashMap<Platform, Long>()

    init {
        scope.launch {
            for (snapshot in queue) {
                runCatching { process(snapshot) }
                    .onFailure { AppLog.e(LogCategory.CAPTURE, "Erro processando tela", it) }
            }
        }
    }

    fun submit(snapshot: ScreenSnapshot) {
        queue.trySend(snapshot)
    }

    fun dismissOverlayOffer() {
        shownByPlatform.clear()
        _overlayOffers.value = emptyList()
    }

    /** A 99 tocou o som de oferta (com ela na tela). Fora de corrida, deve aparecer uma oferta em seguida. */
    fun onOfferSound(platform: Platform, now: Long) {
        if (tripTracker.isActive(platform)) return // em corrida o app toca outros avisos
        queue.trySend(ScreenSnapshot(SOUND_MARK + platform.name, 0, now, emptyList()))
    }

    internal suspend fun process(raw: ScreenSnapshot) {
        var snapshot = if (raw.channel != CaptureChannel.OCR) raw else raw.copy(lines = com.innexar.meuganho.capture.core.OcrCleanup.withoutOwnNotification(raw.lines).map {
            val fixed = com.innexar.meuganho.capture.core.TextPatterns.fixOcr(it.text)
            if (fixed == it.text) it else com.innexar.meuganho.capture.core.ScreenLine(fixed, it.viewId, it.group)
        })
        if (snapshot.packageName.startsWith(SOUND_MARK)) {
            val p = Platform.valueOf(snapshot.packageName.removePrefix(SOUND_MARK))
            sweepSounds(snapshot.capturedAt)
            // Toques seguidos (o som repete) contam como uma oferta só.
            val list = pendingSounds.getOrPut(p) { mutableListOf() }
            if (list.none { snapshot.capturedAt - it < SOUND_REPEAT_MS }) list += snapshot.capturedAt
            return
        }
        val platform = Platform.fromPackage(snapshot.packageName)
            ?: if (snapshot.packageName == ScreenSnapshot.WHOLE_SCREEN) {
                // Sem marca de app (ex.: só o mapa da navegação): é do app que está em corrida, se for um só.
                com.innexar.meuganho.capture.core.PlatformDetector.detect(snapshot.lines)
                    ?: Platform.entries.singleOrNull { tripTracker.isActive(it) } ?: return
            } else return
        val now = snapshot.capturedAt
        sweepSounds(now)
        if (snapshot.channel == CaptureChannel.OCR) lastOcr[platform] = snapshot
        if (isRepeat(snapshot, now)) return
        if (snapshot.packageName == ScreenSnapshot.WHOLE_SCREEN) snapshot = snapshot.copy(packageName = platform.packageNames.first())

        val parser = ParserRegistry.forPlatform(platform)
        val type = parser.classify(snapshot)
        diagnostics.onEvent(platform, type, snapshot.appVersionCode, parser.version, now, snapshot.debug)
        if (diagnostics.recordingArmed && snapshot.channel != CaptureChannel.NOTIFICATION) {
            diagnostics.deliverRecording(recordingText(platform, parser, snapshot, type))
        }

        // "R$x/km aprox." é marca do cartão do Uber: numa leitura por imagem de outro app, é o Uber
        // aparecendo por cima. Descartamos a tela inteira para não criar oferta com dono errado.
        if (snapshot.channel == CaptureChannel.OCR && platform != Platform.UBER &&
            snapshot.lines.any { it.normalized.contains("km aprox") }
        ) {
            diagnostics.onParserError(platform, "Leitura por imagem ignorada: cartão do Uber na tela", now)
            return
        }

        // Notificação é fonte de oferta só quando a tela do app não mostrou a oferta
        // (app em segundo plano). Para as demais telas, a Acessibilidade manda.
        if (snapshot.channel == CaptureChannel.NOTIFICATION && ServiceStatus.accessibilityConnected.value) {
            val recentScreenOffer = now - (lastScreenOfferAt[platform] ?: 0) < SCREEN_OFFER_PRIORITY_MS
            if (type != ScreenType.OFFER || recentScreenOffer) return
        }

        sampleScreen(platform, parser, snapshot, type)
        parser.dayTracker(snapshot)?.let { total ->
            // Lido por imagem: só vale com 2 leituras iguais em até 3 min (um número mal lido não vira corrida).
            if (snapshot.channel != CaptureChannel.OCR) saveDailyTotal(platform, parser, total, now)
            else {
                val seen = trackerCandidate[platform]
                if (seen != null && seen.first == total && now - seen.second <= 180_000L) saveDailyTotal(platform, parser, total, now)
                trackerCandidate[platform] = total to now
            }
        }

        when (type) {
            ScreenType.EARNINGS_HISTORY -> handleHistory(platform, parser, snapshot, now)
            ScreenType.OFFER -> handleOffer(platform, parser, snapshot, now)
            ScreenType.TO_PICKUP -> tripTracker.onSignal(TripSignal.HeadingToPickup(platform), now)
            ScreenType.WAITING_PASSENGER -> tripTracker.onSignal(TripSignal.WaitingPassenger(platform), now)
            ScreenType.IN_TRIP -> tripTracker.onSignal(TripSignal.InTrip(platform), now)
            ScreenType.TRIP_COMPLETED -> {
                // Valor final LOW não entra: melhor a estimativa + revisão do que um número duvidoso.
                val value = parser.parseCompletedValue(snapshot)?.takeIf { it.confidence >= Confidence.MEDIUM }?.value
                tripTracker.onSignal(TripSignal.Completed(platform, value), now)
            }
            ScreenType.TRIP_CANCELLED -> tripTracker.onSignal(TripSignal.Cancelled(platform), now)
            ScreenType.DAILY_EARNINGS -> {
                handleDailyTotal(platform, parser, snapshot, now)
                tripTracker.onSignal(TripSignal.Idle(platform), now)
            }
            ScreenType.IDLE -> tripTracker.onSignal(TripSignal.Idle(platform), now)
            ScreenType.NAVIGATING -> tripTracker.onSignal(TripSignal.Navigating(platform), now)
            ScreenType.NAVIGATING_STOPPED -> tripTracker.onSignal(TripSignal.Navigating(platform, moving = false), now)
            ScreenType.UNKNOWN -> Unit
        }
    }

    private suspend fun handleOffer(platform: Platform, parser: PlatformParser, snapshot: ScreenSnapshot, now: Long) {
        val offers = parser.parseOffers(snapshot)
        if (offers.isEmpty()) {
            if (snapshot.channel == CaptureChannel.OCR) requestOcrRetry(platform, now)
            diagnostics.onParserError(platform, "Tela de oferta sem valor legível", now)
            health.record(platform, ok = false)
            samples.record(platform, ScreenType.OFFER, SampleReason.OFFER_UNPARSED, snapshot, parser.version,
                buildJsonObject { put("error", "sem valor legível") })
            return
        }
        if (snapshot.channel != CaptureChannel.NOTIFICATION) lastScreenOfferAt[platform] = now

        val shown = mutableListOf<OverlayOffer>()
        val newIds = mutableListOf<Long>()
        offers.forEachIndexed { index, offer ->
            val value = offer.offeredCents.value ?: 0
            if (snapshot.channel == CaptureChannel.ACCESSIBILITY) {
                recentScreenOffers.getOrPut(platform) { mutableListOf() }
                    .apply { removeAll { now - it.at > OWNER_WINDOW_MS }; add(ScreenOffer(value, offer.totalMeters, now)) }
            } else if (seenOnOtherPlatform(platform, value, offer.totalMeters, now)) {
                // A captura da tela pegou o cartão de OUTRO app (ex.: Uber flutuando sobre a 99).
                diagnostics.onParserError(platform, "Leitura por imagem ignorada: era a oferta de outro app (${Money.format(value)})", now)
                return@forEachIndexed
            }
            val duplicate = deduplicator.isDuplicate(offer.fingerprint(), now)
            val analysisOnly = duplicate && _overlayOffers.value.isNotEmpty()
            if (duplicate) {
                diagnostics.onDuplicate(platform)
                // Radar: a mesma oferta continua na tela junto com uma nova; ela segue no painel.
                if (!analysisOnly) return@forEachIndexed
            }
            val status: DataStatus
            if (analysisOnly) {
                status = OfferAssessment.assess(offer, null).status
            } else {
                val replaceId = notificationOffers.remove(valueKey(platform, offer))
                    ?.takeIf { snapshot.channel != CaptureChannel.NOTIFICATION && now - it.second < CROSS_CHANNEL_MS }?.first
                val recorded = recorder.record(offer, replaceId) {
                    SnapshotSanitizer.sanitize(snapshot, ParserRegistry.sanitizerKeywords(platform))
                }
                status = recorded.decision.status
                if (snapshot.channel == CaptureChannel.NOTIFICATION) notificationOffers[valueKey(platform, offer)] = recorded.entity.id to now
                health.record(platform, ok = status == DataStatus.AUTO_ACCEPTED)
                if (status != DataStatus.AUTO_ACCEPTED) {
                    diagnostics.onParserError(platform, "Oferta ${status.label}: ${recorded.decision.reasons.joinToString("; ")}", now)
                }
                if (replaceId == null) newIds += recorded.entity.id
                samples.record(platform, ScreenType.OFFER, SampleReason.OFFER, snapshot, parser.version, buildJsonObject {
                    put("cards", offers.size)
                    put("card", index)
                    put("value", offer.offeredCents.value)
                    put("surge", offer.surgeCents)
                    put("pickupM", offer.pickupMeters.value)
                    put("pickupS", offer.pickupSeconds.value)
                    put("tripM", offer.tripMeters.value)
                    put("tripS", offer.tripSeconds.value)
                    put("category", offer.category)
                    put("confidence", offer.criticalConfidence.name)
                    put("fields", offer.criticalFields.entries.joinToString(";") { "${it.key}=${it.value.confidence}" })
                    put("status", status.name)
                    put("reasons", recorded.decision.reasons.joinToString(" | "))
                })
            }
            val tripped = health.isTripped(platform)
            val analysis = OfferAnalyzer.analyze(
                valueCents = value,
                totalMeters = offer.totalMeters?.toLong(),
                totalSeconds = offer.totalSeconds?.toLong(),
                // Nunca um veredito (EXCELENTE/RUIM) com dado incerto ou parser em pane.
                reliable = status == DataStatus.AUTO_ACCEPTED && !tripped,
                costModel = costModel.value,
                thresholds = settings.value.thresholds,
            )
            diagnostics.onOffer(platform, "${Money.format(value)} · ${status.label} · ${analysis.rating.label}", now)
            if (status != DataStatus.INVALID) shown += OverlayOffer(platform, analysis, status, tripped, now)
        }
        if (shown.isNotEmpty()) {
            publishOverlay(platform, shown, now)
            for ((p, list) in pendingSounds) {
                val matched = list.filter { now - it in -SOUND_EARLY_MS..SOUND_MATCH_MS }
                // Oferta da própria plataforma: acerto. De OUTRO app (real 01/10: o toque era do Uber): não conta.
                if (p == platform) matched.forEach { diagnostics.onSoundOutcome(p, read = true) }
                list.removeAll(matched)
            }
        }
        // Leitura por imagem com campo faltando: pede outra captura logo em seguida (o cartão ainda abria).
        if (snapshot.channel == CaptureChannel.OCR && offers.any { it.criticalConfidence < Confidence.MEDIUM }) requestOcrRetry(platform, now)
        if (newIds.isNotEmpty()) {
            // Uma oferta: sabemos qual será aceita. Várias (Radar): não sabemos, a corrida nasce sem oferta.
            val offerId = if (offers.size == 1) newIds.single() else null
            tripTracker.onSignal(TripSignal.OfferShown(platform, offerId), now)
        }
    }

    private fun requestOcrRetry(platform: Platform, now: Long) {
        val (n, since) = retryCount[platform]?.takeIf { now - it.second < 5_000 } ?: (0 to now)
        if (n >= 2) return
        retryCount[platform] = n + 1 to since
        _ocrRetry.tryEmit(platform)
    }

    /** Toques sem oferta lida depois de [SOUND_MATCH_MS]: oferta perdida. Guarda a última imagem lida como evidência. */
    private suspend fun sweepSounds(now: Long) {
        for ((platform, list) in pendingSounds) {
            val expired = list.filter { now - it > SOUND_MATCH_MS }
            if (expired.isEmpty()) continue
            list.removeAll(expired)
            expired.forEach { diagnostics.onSoundOutcome(platform, read = false) }
            val parser = ParserRegistry.forPlatform(platform)
            lastOcr[platform]?.takeIf { now - it.capturedAt < 15_000 }?.let { evidence ->
                samples.record(platform, parser.classify(evidence), SampleReason.OFFER_MISSED, evidence, parser.version,
                    buildJsonObject { put("sounds", expired.size) })
            }
            AppLog.d(LogCategory.CAPTURE, "${platform.label}: toque de oferta sem oferta lida")
        }
    }

    /**
     * A leitura por imagem pegou a oferta de OUTRO app? Mesmo valor, ou (o OCR erra dígitos:
     * R$ 11,11 lido como 11,00) km totais iguais com valor parecido, nos últimos 20 s.
     */
    private fun seenOnOtherPlatform(platform: Platform, value: Long, meters: Int?, now: Long): Boolean =
        recentScreenOffers.any { (other, offers) ->
            other != platform && offers.any { o ->
                now - o.at <= OWNER_WINDOW_MS && (o.cents == value || (meters != null && o.meters != null &&
                    kotlin.math.abs(o.meters - meters) <= o.meters / 10 && kotlin.math.abs(o.cents - value) <= o.cents / 7))
            }
        }

    /** Junta as ofertas atuais de todas as plataformas (as de mais de 15 s saem). */
    private fun publishOverlay(platform: Platform, shown: List<OverlayOffer>, now: Long) {
        shownByPlatform[platform] = shown
        shownByPlatform.entries.removeAll { (_, list) -> list.all { now - it.shownAt > OVERLAY_TTL_MS } }
        _overlayOffers.value = shownByPlatform.values.flatten().take(MAX_OVERLAY_OFFERS)
    }

    private fun valueKey(platform: Platform, offer: com.innexar.meuganho.domain.model.RideOffer) =
        "${platform.name}|${offer.offeredCents.value}"

    private suspend fun handleHistory(platform: Platform, parser: PlatformParser, snapshot: ScreenSnapshot, now: Long) {
        val carried = historyDate?.takeIf { now - it.second < 10 * 60_000L }?.first
        val records = parser.parseEarningsHistory(snapshot, carried)
        parser.lastHistoryDate(snapshot)?.let { historyDate = it to now }
        if (records.isEmpty()) return
        val result = history.import(records)
        reconciler?.run(now)
        if (result.updated + result.created > 0) {
            diagnostics.onParserError(platform, "Histórico: ${result.updated} corridas conferidas, ${result.created} adicionadas", now)
            AppLog.d(LogCategory.CAPTURE, "Histórico ${platform.label}: ${result.updated} conferidas, ${result.created} novas")
        }
    }

    private suspend fun handleDailyTotal(platform: Platform, parser: PlatformParser, snapshot: ScreenSnapshot, now: Long) {
        val total = parser.parseDailyTotal(snapshot)?.takeIf { it.confidence >= Confidence.MEDIUM }?.value ?: return
        saveDailyTotal(platform, parser, total, now)
    }

    private suspend fun saveDailyTotal(platform: Platform, parser: PlatformParser, total: Long, now: Long) {
        val day = DateFormats.epochDay(now)
        val last = platformTotalDao.latest(platform.name, day)
        val previous = last?.totalCents
        if (previous == total) return
        platformTotalDao.insert(PlatformTotalEntity(platform = platform, epochDay = day, totalCents = total,
            capturedAt = now, parserVersion = parser.version))
        // Subida do total = valor exato da corrida que acabou de terminar (com dinâmica e gorjeta).
        // Quedas (outro período na tela) e saltos absurdos são ignorados.
        val delta = previous?.let { total - it }
        if (delta != null && delta in 1..MAX_TRIP_DELTA_CENTS) {
            tripTracker.applyEarningsDelta(platform, delta, now, since = last.capturedAt)
            reconciler?.run(now)
        }
        AppLog.d(LogCategory.CAPTURE, "Total diário ${platform.label}: ${Money.format(total)}")
    }

    /** Amostra das telas que não são oferta (a oferta é amostrada após a avaliação, com o resultado). */
    private suspend fun sampleScreen(platform: Platform, parser: PlatformParser, snapshot: ScreenSnapshot, type: ScreenType) {
        val parse = when (type) {
            ScreenType.OFFER -> return
            ScreenType.TRIP_COMPLETED -> parser.parseCompletedValue(snapshot)
            ScreenType.DAILY_EARNINGS -> parser.parseDailyTotal(snapshot)
            else -> null
        }
        val reason = if (type == ScreenType.UNKNOWN) SampleReason.UNKNOWN else SampleReason.SCREEN
        samples.record(platform, type, reason, snapshot, parser.version, buildJsonObject {
            put("value", parse?.value)
            put("confidence", parse?.confidence?.name)
        })
    }

    /** Mesma tela em menos de 5 s: ignorar. Depois disso reprocessa (a janela de decisão da oferta depende do tempo). */
    private fun isRepeat(snapshot: ScreenSnapshot, now: Long): Boolean {
        val key = snapshot.packageName + snapshot.channel
        val last = lastProcessed[key]
        if (last != null && last.first == snapshot.contentHash && now - last.second < 5_000) return true
        lastProcessed[key] = snapshot.contentHash to now
        return false
    }

    private companion object {
        const val SCREEN_OFFER_PRIORITY_MS = 10_000L
        const val CROSS_CHANNEL_MS = 90_000L
        const val MAX_OVERLAY_OFFERS = 4
        const val OWNER_WINDOW_MS = 20_000L
        const val OVERLAY_TTL_MS = 15_000L
        const val MAX_TRIP_DELTA_CENTS = 50_000L
        const val SOUND_MARK = "sound:"
        const val SOUND_MATCH_MS = 8_000L
        const val SOUND_EARLY_MS = 2_000L
        const val SOUND_REPEAT_MS = 4_000L
    }

    private fun recordingText(platform: Platform, parser: PlatformParser, snapshot: ScreenSnapshot, type: ScreenType): String {
        val header = "# ${platform.label} · app ${snapshot.appVersionCode} · ${parser.version} · classificada como $type"
        val offerLine = if (type == ScreenType.OFFER) {
            parser.parseOffer(snapshot)?.let {
                "# valor=${it.offeredCents.value?.let(Money::format)} busca=${it.pickupMeters.value?.let(Units::km)} " +
                    "viagem=${it.tripMeters.value?.let(Units::km)} confiança=${it.criticalConfidence}"
            }
        } else {
            null
        }
        return listOfNotNull(header, offerLine, SnapshotSanitizer.sanitize(snapshot, ParserRegistry.sanitizerKeywords(platform)))
            .joinToString("\n")
    }
}
