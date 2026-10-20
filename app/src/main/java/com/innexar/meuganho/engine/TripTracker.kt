package com.innexar.meuganho.engine

import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.local.dao.OfferDao
import com.innexar.meuganho.data.local.dao.ShiftDao
import com.innexar.meuganho.data.local.dao.TripDao
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.remote.StatsSync
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import com.innexar.meuganho.domain.trip.TripContext
import com.innexar.meuganho.domain.trip.TripEffect
import com.innexar.meuganho.domain.trip.TripSignal
import com.innexar.meuganho.domain.trip.TripStateMachine
import com.innexar.meuganho.domain.validation.TripValuePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Aplica no banco os efeitos da [TripStateMachine]. Toda decisão de transição
 * está na máquina; aqui só existe persistência.
 */
class TripTracker(
    private val tripDao: TripDao,
    private val offerDao: OfferDao,
    private val shiftDao: ShiftDao,
    private val stats: StatsSync,
) {
    private val mutex = Mutex()

    // Um acompanhamento POR PLATAFORMA. Real (29/09/2026): com um só, a oferta da 99 que chegava
    // logo depois do aceite no Uber marcava a do Uber como "não aceita" e a corrida se perdia.
    // Concorrente: isActive() é consultado de outras threads (toque de oferta, pausa automática).
    private val contexts = java.util.concurrent.ConcurrentHashMap<com.innexar.meuganho.domain.model.Platform, TripContext>()
    private val currentIds = HashMap<com.innexar.meuganho.domain.model.Platform, Long?>()
    private val lastCompletedIds = HashMap<com.innexar.meuganho.domain.model.Platform, Long?>()

    // Estado da plataforma sendo processada (carregado/salvo em volta de cada sinal).
    private var context = TripContext()
    private var currentTripId: Long? = null
    private var lastCompletedTripId: Long? = null
    private var lastEndedTripId: Long? = null
    private val lastEndedIds = HashMap<com.innexar.meuganho.domain.model.Platform, Long?>()

    private val _live = MutableStateFlow(LiveTrip())
    val live: StateFlow<LiveTrip> = _live.asStateFlow()

    /** Recupera corrida em andamento após o processo ser morto pelo sistema. */
    suspend fun restore(now: Long) = mutex.withLock {
        tripDao.activeAll().forEach { active ->
            // Aberta há mais de 3 h (o fim não foi visto; real 30/09/2026: corrida da 99 presa desde as 07:00):
            // concluída com a estimativa, para o motorista conferir. Não fica aberta para sempre.
            if (now - active.acceptedAt > STALE_ACTIVE_MS) {
                tripDao.update(active.copy(state = TripState.COMPLETED, endedAt = active.endedAt ?: (active.startedAt ?: active.acceptedAt),
                    status = DataStatus.NEEDS_REVIEW, valueSource = if (active.estimatedCents != null) ValueSource.OFFER_ESTIMATE else ValueSource.NONE,
                    reasons = listOf(active.reasons, "Fim da corrida não detectado: confira valor e horário").filter { it.isNotBlank() }.joinToString(" | ")))
                stats.enqueue(StatsSync.Kind.TRIP, active.id)
                return@forEach
            }
            currentIds[active.platform] = active.id
            contexts[active.platform] = TripContext(state = active.state, platform = active.platform, lastSignalAt = now,
                tripStartedAt = active.acceptedAt, lastTripScreenAt = now)
        }
        publish()
    }

    private companion object {
        const val STALE_ACTIVE_MS = 3 * 3_600_000L
    }

    suspend fun onSignal(signal: TripSignal, now: Long) = mutex.withLock {
        if (signal !is TripSignal.Idle && signal !is TripSignal.Tick) WorkActivity.mark(now) // oferta ou corrida = trabalhando
        val p = signal.platform
        context = contexts[p] ?: TripContext()
        currentTripId = currentIds[p]
        lastCompletedTripId = lastCompletedIds[p]
        lastEndedTripId = lastEndedIds[p]
        val transition = TripStateMachine.reduce(context, signal, now)
        context = transition.context
        transition.effects.forEach { effect ->
            runCatching { apply(effect) }.onFailure { AppLog.e(LogCategory.TRIP, "Falha ao aplicar $effect", it) }
        }
        contexts[p] = context
        currentIds[p] = currentTripId
        lastCompletedIds[p] = lastCompletedTripId
        lastEndedIds[p] = lastEndedTripId
        publish()
    }

    /** Relógio do turno (1x/min): fim de corrida pelo silêncio, em cada plataforma com corrida. */
    suspend fun tick(now: Long) {
        contexts.filterValues { it.state.isActiveTrip }.keys.forEach { onSignal(TripSignal.Tick(it), now) }
    }

    /** A plataforma tem corrida em andamento agora? */
    fun isActive(platform: com.innexar.meuganho.domain.model.Platform): Boolean = contexts[platform]?.state?.isActiveTrip == true

    private suspend fun apply(effect: TripEffect) {
        when (effect) {
            is TripEffect.StartTrip -> start(effect)
            is TripEffect.UpdateTripState -> updateCurrent { trip ->
                when (effect.state) {
                    TripState.WAITING_PASSENGER -> trip.copy(state = effect.state, pickupArrivedAt = effect.at)
                    TripState.IN_TRIP -> trip.copy(state = effect.state, startedAt = effect.at, pickupArrivedAt = trip.pickupArrivedAt ?: effect.at)
                    else -> trip.copy(state = effect.state)
                }
            }
            is TripEffect.CompleteTrip -> {
                updateCurrent { trip -> complete(trip, effect.finalCents, effect.at).copy(endCell = LocationState.currentCell(effect.at)) }
                currentTripId?.let { stats.enqueue(StatsSync.Kind.TRIP, it) }
                lastCompletedTripId = currentTripId
                lastEndedTripId = currentTripId
                currentTripId = null
            }
            is TripEffect.SetCompletedValue -> lastCompletedTripId?.let { id ->
                tripDao.byId(id)?.let { tripDao.update(complete(it, effect.finalCents, it.endedAt ?: 0)) }
                stats.enqueue(StatsSync.Kind.TRIP, id)
            }
            is TripEffect.CancelTrip -> {
                // Cancelada sem taxa não tem valor a revisar: não polui a fila de revisão.
                updateCurrent { it.copy(state = TripState.CANCELLED, endedAt = effect.at, status = DataStatus.AUTO_ACCEPTED) }
                currentTripId?.let { stats.enqueue(StatsSync.Kind.TRIP, it) }
                lastEndedTripId = currentTripId
                currentTripId = null
            }
            is TripEffect.ReopenTrip -> {
                val id = lastEndedTripId ?: lastCompletedTripId ?: return
                val trip = tripDao.byId(id) ?: return
                // Já conferida por fonte oficial (histórico/total) depois do fim: terminou mesmo, não reabre.
                if (trip.valueSource == ValueSource.PLATFORM_HISTORY || trip.valueSource == ValueSource.PLATFORM_TOTAL) {
                    context = context.copy(state = TripState.COMPLETED, completedHasValue = true, lastEndedAt = trip.endedAt)
                    return
                }
                tripDao.update(trip.copy(state = effect.state, endedAt = null, status = DataStatus.NEEDS_REVIEW,
                    reasons = listOf(trip.reasons, "Reaberta: a corrida continuou depois de um fim aparente").filter { it.isNotBlank() }.joinToString(" | ")))
                stats.enqueue(StatsSync.Kind.TRIP, id)
                currentTripId = id
                lastEndedTripId = null
            }
            is TripEffect.AbandonTrip -> {
                updateCurrent {
                    it.copy(state = TripState.UNKNOWN, endedAt = effect.at, status = DataStatus.NEEDS_REVIEW,
                        reasons = "Corrida sem sinais por 3h; conferir valor e fim")
                }
                currentTripId = null
            }
            is TripEffect.ResolveOffer -> {
                offerDao.setOutcome(effect.offerId, effect.outcome)
                stats.enqueue(StatsSync.Kind.OFFER, effect.offerId)
            }
        }
        AppLog.d(LogCategory.TRIP, effect.javaClass.simpleName)
    }

    /**
     * O total do dia da plataforma subiu [delta] desde a leitura anterior (feita em [since]).
     * As corridas sem valor oficial terminadas desde então (mais a em andamento) dividem a subida
     * ([EarningsDeltaSplitter]): uma só recebe o valor oficial; várias são conferidas pela soma;
     * sobra de uma corrida inteira vira corrida não vista, para revisão. Já contada pelo histórico: ignora.
     */
    suspend fun applyEarningsDelta(platform: com.innexar.meuganho.domain.model.Platform, delta: Long, now: Long, since: Long = now - 20 * 60_000L) {
        if (mutex.withLock { tripDao.historyValueSince(platform.name, delta, now - 3 * 3_600_000L) } > 0) return
        val activeId = if (isActive(platform)) currentIds[platform] else null
        val pending = mutex.withLock {
            tripDao.endedSince(platform.name, since).filter {
                it.state == TripState.COMPLETED && (it.valueSource == ValueSource.OFFER_ESTIMATE || it.valueSource == ValueSource.NONE)
            }
        }
        val active = activeId?.let { tripDao.byId(it) }
        val items = listOfNotNull(active) + pending
        val decision = com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.decide(delta, items.map { it.estimatedCents })

        // A corrida em andamento terminou (o total só sobe no fim).
        if (active != null) {
            val single = decision is com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.Decision.Single
            onSignal(TripSignal.Completed(platform, if (single) delta else null), now)
        }
        mutex.withLock {
            when (decision) {
                is com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.Decision.Single -> markOfficial(items[decision.index].id, delta)
                is com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.Decision.ConfirmSum -> {
                    items.forEach { t -> annotate(t.id, DataStatus.AUTO_ACCEPTED, "Conferida pela soma do total do dia da plataforma") }
                    decision.unseenCents?.let { insertUnseen(platform, it, now) }
                }
                com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.Decision.Mismatch ->
                    items.forEach { t -> annotate(t.id, DataStatus.NEEDS_REVIEW, "O total do dia subiu ${com.innexar.meuganho.core.money.Money.format(delta)}, menos que o estimado: confira") }
                com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.Decision.Ignore -> Unit
                com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.Decision.Unseen -> {
                    // "Cancelada" há pouco sem valor: foi feita (a subida é dela).
                    val revived = tripDao.lastEndedSince(platform.name, now - 20 * 60_000L)
                        ?.takeIf { it.state == TripState.CANCELLED && it.finalCents == null }
                    if (revived != null) markOfficial(revived.id, delta) else insertUnseen(platform, delta, now)
                }
            }
        }
    }

    private suspend fun annotate(tripId: Long, status: DataStatus, reason: String) {
        val trip = tripDao.byId(tripId) ?: return
        tripDao.update(trip.copy(status = status,
            reasons = listOf(trip.reasons, reason).filter { it.isNotBlank() }.joinToString(" | ")))
        stats.enqueue(StatsSync.Kind.TRIP, tripId)
    }

    private suspend fun insertUnseen(platform: com.innexar.meuganho.domain.model.Platform, cents: Long, now: Long) {
        val id = tripDao.insert(TripEntity(
            shiftId = shiftDao.open()?.id, offerId = null, platform = platform, state = TripState.COMPLETED,
            status = DataStatus.NEEDS_REVIEW, acceptedAt = now, endedAt = now, finalCents = cents,
            valueSource = ValueSource.PLATFORM_TOTAL,
            reasons = "O total do dia da plataforma subiu ${com.innexar.meuganho.core.money.Money.format(cents)} sem corrida vista: confirme",
        ))
        stats.enqueue(StatsSync.Kind.TRIP, id)
    }

    /** Valor oficial (subida do total): concluída, com o valor pago. Corrige também "canceladas" que não eram. */
    private suspend fun markOfficial(tripId: Long, delta: Long) {
        val trip = tripDao.byId(tripId) ?: return
        tripDao.update(trip.copy(state = TripState.COMPLETED, finalCents = delta, correctedCents = null,
            valueSource = ValueSource.PLATFORM_TOTAL, status = DataStatus.AUTO_ACCEPTED,
            reasons = "Valor oficial: subida do total do dia da plataforma"))
        stats.enqueue(StatsSync.Kind.TRIP, tripId)
    }

    private suspend fun start(e: TripEffect.StartTrip) {
        val offer = e.offerId?.let { offerDao.byId(it) }
        val trip = TripEntity(
            shiftId = shiftDao.open()?.id,
            offerId = offer?.id,
            platform = e.platform,
            state = e.state,
            status = DataStatus.NEEDS_REVIEW, // Definido de verdade na conclusão.
            acceptedAt = e.at,
            pickupArrivedAt = if (e.state.ordinal >= TripState.WAITING_PASSENGER.ordinal) e.at else null,
            startedAt = if (e.state == TripState.IN_TRIP) e.at else null,
            estimatedCents = offer?.effectiveCents,
            estPickupMeters = offer?.pickupMeters,
            estPickupSeconds = offer?.pickupSeconds,
            estTripMeters = offer?.tripMeters,
            estTripSeconds = offer?.tripSeconds,
            valueSource = if (offer?.effectiveCents != null) ValueSource.OFFER_ESTIMATE else ValueSource.NONE,
            reasons = if (offer == null) "Oferta não vista: corrida iniciada sem captura da oferta" else "",
        )
        currentTripId = tripDao.insert(trip).also { stats.enqueue(StatsSync.Kind.TRIP, it) } // o servidor vê a corrida aberta
    }

    private suspend fun complete(trip: TripEntity, finalCents: Long?, at: Long): TripEntity {
        val offerStatus = trip.offerId?.let { offerDao.byId(it)?.status }
        val decision = TripValuePolicy.resolve(offerStatus, trip.estimatedCents, finalCents)
        return trip.copy(
            state = TripState.COMPLETED,
            endedAt = trip.endedAt ?: at,
            startedAt = trip.startedAt ?: trip.pickupArrivedAt ?: trip.acceptedAt,
            finalCents = finalCents ?: trip.finalCents,
            valueSource = when {
                finalCents != null -> ValueSource.COMPLETION_SCREEN
                trip.estimatedCents != null -> ValueSource.OFFER_ESTIMATE
                else -> ValueSource.NONE
            },
            status = decision.status,
            reasons = (listOf(trip.reasons) + decision.reasons).filter { it.isNotBlank() }.joinToString(" | "),
        )
    }

    private suspend fun updateCurrent(transform: suspend (TripEntity) -> TripEntity) {
        val id = currentTripId ?: return
        val trip = tripDao.byId(id) ?: return
        tripDao.update(transform(trip))
    }

    /** Estado ao vivo: a corrida em andamento (de qualquer plataforma); senão, a última movimentação. */
    private fun publish() {
        val (platform, ctx) = contexts.entries.firstOrNull { it.value.state.isActiveTrip }?.toPair()
            ?: contexts.entries.maxByOrNull { it.value.lastSignalAt }?.toPair() ?: (null to TripContext())
        _live.value = LiveTrip(
            state = ctx.state,
            platform = platform,
            tripId = platform?.let { currentIds[it] },
            segment = TripStateMachine.segmentFor(ctx.state),
        )
    }

}
