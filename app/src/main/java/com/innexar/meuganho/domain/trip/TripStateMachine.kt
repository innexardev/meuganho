package com.innexar.meuganho.domain.trip

import com.innexar.meuganho.domain.model.DriveSegment
import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.trip.TripEffect.AbandonTrip
import com.innexar.meuganho.domain.trip.TripEffect.CancelTrip
import com.innexar.meuganho.domain.trip.TripEffect.CompleteTrip
import com.innexar.meuganho.domain.trip.TripEffect.ResolveOffer
import com.innexar.meuganho.domain.trip.TripEffect.SetCompletedValue
import com.innexar.meuganho.domain.trip.TripEffect.StartTrip
import com.innexar.meuganho.domain.trip.TripEffect.UpdateTripState

/**
 * Máquina de estados única da corrida. Nenhuma tela ou serviço decide transições;
 * todos enviam [TripSignal] e aplicam os [TripEffect] devolvidos.
 *
 * Função pura: (contexto, sinal, agora) -> (novo contexto, efeitos). Isso permite
 * testar todo o ciclo sem Android. Ver docs/TRIP_STATE_MACHINE.md.
 */
object TripStateMachine {

    /** Tempo que a oferta fica na tela; depois disso, uma tela ociosa significa "não aceita". */
    const val OFFER_DECISION_WINDOW_MS = 25_000L

    /** Corrida ativa sem nenhum sinal por 3h foi perdida (app fechado, celular reiniciado). */
    const val ABANDON_AFTER_MS = 3 * 60 * 60 * 1000L

    const val LIKELY_COMPLETED_AFTER_MS = 8 * 60_000L
    const val RESIDUAL_ROUTE_MS = 90_000L

    /** Oferta na fila (recebida durante uma corrida) vale por 10 min; depois, não vira a próxima corrida. */
    const val QUEUE_TTL_MS = 10 * 60_000L

    const val NEW_PICKUP_AFTER_MS = 60_000L

    /** Navegação parada por 90 s antes de uma oferta nova = a corrida anterior já tinha acabado. */
    const val NAV_GAP_MS = 90_000L

    /** Em viagem e nenhuma tela de corrida por 6 min (a navegação sumiu): a corrida acabou. */
    const val SILENT_END_MS = 6 * 60_000L

    /** Tela de corrida até 5 min depois de um fim sem valor oficial (e sem oferta nova): o fim foi engano. */
    const val REOPEN_WITHIN_MS = 5 * 60_000L

    fun reduce(context: TripContext, signal: TripSignal, now: Long): Transition {
        val effects = mutableListOf<TripEffect>()
        var ctx = context

        if (ctx.state.isActiveTrip && ctx.lastSignalAt > 0 && now - ctx.lastSignalAt > ABANDON_AFTER_MS) {
            effects += AbandonTrip(now)
            ctx = ctx.copy(state = TripState.UNKNOWN, platform = null)
        }

        // Durante uma corrida, telas de outra plataforma não mudam o estado.
        // A única exceção é uma oferta, que fica na fila para a próxima corrida.
        if (ctx.state.isActiveTrip && signal.platform != ctx.platform && signal !is TripSignal.OfferShown) {
            return Transition(ctx.copy(lastSignalAt = now), effects)
        }

        // Relógio: não é sinal de tela (não conta para abandono nem para "última atividade").
        if (signal is TripSignal.Tick) return Transition(onTick(ctx, now, effects), effects)
        // Fila vencida: a oferta não foi aceita.
        if (ctx.queuedOfferId != null && now - (ctx.queuedAt ?: now) > QUEUE_TTL_MS) {
            effects += ResolveOffer(ctx.queuedOfferId, OfferOutcome.NOT_ACCEPTED)
            ctx = ctx.copy(queuedOfferId = null, queuedPlatform = null, queuedAt = null)
        }
        val tripScreen = signal is TripSignal.Navigating || signal is TripSignal.HeadingToPickup ||
            signal is TripSignal.WaitingPassenger || signal is TripSignal.InTrip
        // Corrida emendada: em viagem, a navegação parou ANTES de chegar a oferta da fila e voltou depois
        // (simulação 30/09/2026: a corrida A acabou em silêncio, a oferta B foi aceita e virava parte de A).
        // A terminou na última tela; B começa agora com a oferta da fila.
        if (tripScreen && ctx.state == TripState.IN_TRIP && ctx.queuedOfferId != null && ctx.queuedPlatform == signal.platform) {
            val last = ctx.lastTripScreenAt
            val queued = ctx.queuedAt
            if (last != null && queued != null && queued - last >= NAV_GAP_MS) {
                effects += CompleteTrip(null, last)
                ctx = ctx.copy(state = TripState.COMPLETED, completedHasValue = false, lastEndedAt = last)
                val target = when (signal) {
                    is TripSignal.WaitingPassenger -> TripState.WAITING_PASSENGER
                    is TripSignal.InTrip -> TripState.IN_TRIP
                    else -> TripState.TO_PICKUP
                }
                ctx = onTripScreen(ctx.copy(lastEndedAt = null), signal, target, now, effects).copy(lastTripScreenAt = now)
                return Transition(ctx.copy(lastSignalAt = now), effects)
            }
        }
        if (tripScreen) ctx = ctx.copy(lastTripScreenAt = now)

        ctx = when (signal) {
            is TripSignal.OfferShown -> onOffer(ctx, signal, now, effects)
            is TripSignal.HeadingToPickup -> onTripScreen(ctx, signal, TripState.TO_PICKUP, now, effects)
            is TripSignal.WaitingPassenger -> onTripScreen(ctx, signal, TripState.WAITING_PASSENGER, now, effects)
            is TripSignal.InTrip -> onTripScreen(ctx, signal, TripState.IN_TRIP, now, effects)
            is TripSignal.Completed -> onCompleted(ctx, signal, now, effects)
            is TripSignal.Cancelled -> onCancelled(ctx, now, effects)
            is TripSignal.Idle -> onIdle(ctx, now, effects)
            is TripSignal.Navigating -> onNavigating(ctx, signal, now, effects)
            is TripSignal.Tick -> ctx
        }
        return Transition(trackInTrip(context, ctx, now).copy(lastSignalAt = now), effects)
    }

    private fun trackInTrip(before: TripContext, after: TripContext, now: Long): TripContext = when {
        after.state != TripState.IN_TRIP -> after.copy(inTripSince = null)
        before.state != TripState.IN_TRIP || after.inTripSince == null -> after.copy(inTripSince = now)
        else -> after
    }

    /**
     * Navegando sem dizer a fase. Depois de uma oferta = ACEITE; depois de "aguardando o passageiro" =
     * passageiro embarcou (EM CORRIDA); logo após o fim de uma corrida (90 s) = rota residual, ignora.
     */
    private fun onNavigating(ctx: TripContext, s: TripSignal.Navigating, now: Long, fx: MutableList<TripEffect>): TripContext = when {
        // Parado (sinal, embarque): a corrida segue viva, mas parado no embarque NÃO é "passageiro embarcou".
        ctx.state == TripState.WAITING_PASSENGER && !s.moving -> ctx
        ctx.state == TripState.WAITING_PASSENGER -> {
            fx += UpdateTripState(TripState.IN_TRIP, now)
            ctx.copy(state = TripState.IN_TRIP)
        }
        ctx.state.isActiveTrip -> ctx
        ctx.lastEndedAt?.let { now - it < RESIDUAL_ROUTE_MS } == true && ctx.state != TripState.OFFERED -> ctx
        reopenable(ctx, now) -> reopen(ctx, if (ctx.state == TripState.COMPLETED) TripState.IN_TRIP else TripState.TO_PICKUP, now, fx)
        else -> onTripScreen(ctx, s, TripState.TO_PICKUP, now, fx)
    }

    /**
     * Encerrada por engano? Cancelada, ou concluída sem valor (fim deduzido), há menos de 5 min, sem
     * oferta nova no meio. Real/simulação (30/09/2026): um "voltou à busca" falso durante a corrida
     * encerrava a corrida e a navegação seguinte abria OUTRA, com a oferta errada.
     */
    private fun reopenable(ctx: TripContext, now: Long): Boolean =
        (ctx.state == TripState.CANCELLED || (ctx.state == TripState.COMPLETED && !ctx.completedHasValue)) &&
            ctx.lastEndedAt?.let { now - it <= REOPEN_WITHIN_MS } == true

    private fun reopen(ctx: TripContext, state: TripState, now: Long, fx: MutableList<TripEffect>): TripContext {
        fx += TripEffect.ReopenTrip(state, now)
        return ctx.copy(state = state, lastEndedAt = null, completedHasValue = true)
    }

    /**
     * Em viagem e a navegação sumiu há [SILENT_END_MS]: acabou (a 99 não mostra tela de fim legível;
     * real 30/09/2026: corrida aberta para sempre). O fim registrado é o da última tela de corrida.
     */
    private fun onTick(ctx: TripContext, now: Long, fx: MutableList<TripEffect>): TripContext {
        val last = ctx.lastTripScreenAt ?: return ctx
        if (ctx.state != TripState.IN_TRIP || now - last < SILENT_END_MS) return ctx
        fx += CompleteTrip(null, last)
        return ctx.copy(state = TripState.COMPLETED, completedHasValue = false, lastEndedAt = last)
    }

    fun segmentFor(state: TripState): DriveSegment = when (state) {
        TripState.TO_PICKUP, TripState.WAITING_PASSENGER -> DriveSegment.PICKUP
        TripState.IN_TRIP -> DriveSegment.PAID
        else -> DriveSegment.EMPTY
    }

    private fun onOffer(ctx: TripContext, s: TripSignal.OfferShown, now: Long, fx: MutableList<TripEffect>): TripContext {
        if (ctx.state.isActiveTrip) {
            ctx.queuedOfferId?.takeIf { it != s.offerId }?.let { fx += ResolveOffer(it, OfferOutcome.NOT_ACCEPTED) }
            return ctx.copy(queuedOfferId = s.offerId, queuedPlatform = s.offerId?.let { s.platform }, queuedAt = now)
        }
        if (ctx.state == TripState.OFFERED && ctx.offerId == s.offerId && ctx.platform == s.platform) {
            return if (s.offerId == null) ctx.copy(offerShownAt = now) else ctx
        }
        if (ctx.state == TripState.OFFERED) ctx.offerId?.let { fx += ResolveOffer(it, OfferOutcome.NOT_ACCEPTED) }
        return ctx.copy(state = TripState.OFFERED, platform = s.platform, offerId = s.offerId, offerShownAt = now)
    }

    /** Telas de corrida em andamento: avançam o estado ou iniciam uma corrida que não vimos começar. */
    private fun onTripScreen(
        ctx: TripContext,
        s: TripSignal,
        target: TripState,
        now: Long,
        fx: MutableList<TripEffect>,
    ): TripContext {
        if (ctx.state == TripState.IN_TRIP && target == TripState.TO_PICKUP &&
            ctx.queuedOfferId != null && ctx.queuedPlatform == s.platform
        ) {
            // Corrida encadeada: a Uber pula o resumo e já navega até o próximo passageiro.
            fx += CompleteTrip(null, now)
            return onTripScreen(ctx.copy(state = TripState.COMPLETED, completedHasValue = false), s, target, now, fx)
        }
        // Tela de EMBARQUE (explícita, não só navegação) depois de 1 min em viagem: a corrida anterior acabou
        // e esta é outra (real 01/10/2026, "Solicitações seguidas" da 99: duas corridas viravam uma).
        if (ctx.state == TripState.IN_TRIP && target.ordinal < TripState.IN_TRIP.ordinal &&
            (s is TripSignal.HeadingToPickup || s is TripSignal.WaitingPassenger) &&
            now - (ctx.inTripSince ?: now) >= NEW_PICKUP_AFTER_MS
        ) {
            fx += CompleteTrip(null, ctx.lastTripScreenAt?.takeIf { it < now } ?: now)
            return onTripScreen(ctx.copy(state = TripState.COMPLETED, completedHasValue = false, lastEndedAt = null, inTripSince = null), s, target, now, fx)
        }
        if (ctx.state.isActiveTrip) {
            // Só avança; telas antigas que reaparecem (ex.: voltar ao mapa de busca) não retrocedem.
            return if (target.ordinal > ctx.state.ordinal) {
                fx += UpdateTripState(target, now)
                ctx.copy(state = target)
            } else {
                ctx
            }
        }
        if (reopenable(ctx, now)) return reopen(ctx, maxOf(target, if (ctx.state == TripState.COMPLETED) TripState.IN_TRIP else target), now, fx)
        val offerId = acceptedOfferId(ctx, s, fx)
        fx += StartTrip(s.platform, offerId, target, now)
        val fromQueue = offerId != null && offerId == ctx.queuedOfferId
        return ctx.copy(
            tripStartedAt = now,
            state = target,
            platform = s.platform,
            offerId = null,
            offerShownAt = null,
            queuedOfferId = if (fromQueue) null else ctx.queuedOfferId,
            queuedPlatform = if (fromQueue) null else ctx.queuedPlatform,
        )
    }

    /** Descobre qual oferta foi aceita: a que estava na tela ou a da fila, desde que da mesma plataforma. */
    private fun acceptedOfferId(ctx: TripContext, s: TripSignal, fx: MutableList<TripEffect>): Long? {
        if (ctx.state == TripState.OFFERED && ctx.offerId != null) {
            return if (ctx.platform == s.platform) {
                fx += ResolveOffer(ctx.offerId, OfferOutcome.ACCEPTED)
                ctx.offerId
            } else {
                fx += ResolveOffer(ctx.offerId, OfferOutcome.NOT_ACCEPTED)
                null
            }
        }
        if (ctx.queuedOfferId != null && ctx.queuedPlatform == s.platform) {
            fx += ResolveOffer(ctx.queuedOfferId, OfferOutcome.ACCEPTED)
            return ctx.queuedOfferId
        }
        return null
    }

    private fun onCompleted(ctx: TripContext, s: TripSignal.Completed, now: Long, fx: MutableList<TripEffect>): TripContext {
        if (ctx.state.isActiveTrip) {
            fx += CompleteTrip(s.finalCents, now)
            return ctx.copy(state = TripState.COMPLETED, completedHasValue = s.finalCents != null, lastEndedAt = now)
        }
        // A tela de resumo às vezes aparece depois, já com o valor final.
        if (ctx.state == TripState.COMPLETED && !ctx.completedHasValue && s.finalCents != null) {
            fx += SetCompletedValue(s.finalCents)
            return ctx.copy(completedHasValue = true)
        }
        return ctx
    }

    private fun onCancelled(ctx: TripContext, now: Long, fx: MutableList<TripEffect>): TripContext = when {
        ctx.state.isActiveTrip -> {
            fx += CancelTrip(now)
            ctx.copy(state = TripState.CANCELLED, lastEndedAt = now)
        }
        ctx.state == TripState.OFFERED -> {
            ctx.offerId?.let { fx += ResolveOffer(it, OfferOutcome.NOT_ACCEPTED) }
            ctx.copy(state = TripState.REJECTED, offerId = null, offerShownAt = null)
        }
        else -> ctx
    }

    private fun onIdle(ctx: TripContext, now: Long, fx: MutableList<TripEffect>): TripContext = when (ctx.state) {
        TripState.OFFERED -> {
            val shownAt = ctx.offerShownAt ?: now
            if (now - shownAt >= OFFER_DECISION_WINDOW_MS) {
                ctx.offerId?.let { fx += ResolveOffer(it, OfferOutcome.NOT_ACCEPTED) }
                ctx.copy(state = TripState.REJECTED, offerId = null, offerShownAt = null)
            } else {
                ctx // Piscadas de tela durante a oferta não contam como recusa.
            }
        }
        // Voltou à tela de busca sem vermos o resumo: a corrida terminou, valor final desconhecido.
        TripState.IN_TRIP -> {
            fx += CompleteTrip(null, now)
            ctx.copy(state = TripState.COMPLETED, completedHasValue = false, lastEndedAt = now)
        }
        // Voltou à busca sem vermos a viagem começar. Real (29/09/2026): o Uber muitas vezes só navega,
        // sem "Destino de". Até 8 min do aceite é cancelamento; depois disso a corrida quase certamente
        // aconteceu (o valor vem do total do dia da plataforma).
        TripState.TO_PICKUP, TripState.WAITING_PASSENGER -> {
            if (now - (ctx.tripStartedAt ?: now) > LIKELY_COMPLETED_AFTER_MS) {
                fx += CompleteTrip(null, now)
                ctx.copy(state = TripState.COMPLETED, completedHasValue = false, lastEndedAt = now)
            } else {
                fx += CancelTrip(now)
                ctx.copy(state = TripState.CANCELLED, lastEndedAt = now)
            }
        }
        else -> ctx
    }
}
