package com.innexar.meuganho.domain.trip

import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState

/** Sinais derivados das telas classificadas pelos parsers. */
sealed interface TripSignal {
    val platform: Platform

    /** [offerId] null = várias ofertas na tela (Radar): não dá para saber qual será aceita. */
    data class OfferShown(override val platform: Platform, val offerId: Long?) : TripSignal
    data class HeadingToPickup(override val platform: Platform) : TripSignal
    data class WaitingPassenger(override val platform: Platform) : TripSignal
    data class InTrip(override val platform: Platform) : TripSignal
    data class Completed(override val platform: Platform, val finalCents: Long?) : TripSignal
    data class Cancelled(override val platform: Platform) : TripSignal
    data class Idle(override val platform: Platform) : TripSignal

    /** App da plataforma navegando (rota na tela) sem dizer a fase: há corrida em andamento. */
    data class Navigating(override val platform: Platform, val moving: Boolean = true) : TripSignal

    /** Relógio (1x/min, serviço do turno): detecta o fim de corrida pelo silêncio das telas de corrida. */
    data class Tick(override val platform: Platform) : TripSignal
}

/** Efeitos que o [TripTracker] aplica no banco. A máquina em si não faz I/O. */
sealed interface TripEffect {
    data class StartTrip(val platform: Platform, val offerId: Long?, val state: TripState, val at: Long) : TripEffect
    data class UpdateTripState(val state: TripState, val at: Long) : TripEffect
    data class CompleteTrip(val finalCents: Long?, val at: Long) : TripEffect
    data class SetCompletedValue(val finalCents: Long) : TripEffect
    data class CancelTrip(val at: Long) : TripEffect
    data class AbandonTrip(val at: Long) : TripEffect
    data class ResolveOffer(val offerId: Long, val outcome: OfferOutcome) : TripEffect
    /** A corrida encerrada há pouco continua (a tela de corrida voltou): desfaz o fim, no estado dado. */
    data class ReopenTrip(val state: TripState, val at: Long) : TripEffect
}

data class TripContext(
    val state: TripState = TripState.IDLE,
    val platform: Platform? = null,
    /** Oferta atualmente na tela (estado OFFERED). */
    val offerId: Long? = null,
    val offerShownAt: Long? = null,
    /** Oferta recebida durante uma corrida ativa (Uber envia a próxima antes do fim). */
    val queuedOfferId: Long? = null,
    val queuedPlatform: Platform? = null,
    val lastSignalAt: Long = 0,
    val completedHasValue: Boolean = true,
    /** Início da corrida atual (aceite) e fim da última: para decidir concluída x cancelada. */
    val tripStartedAt: Long? = null,
    /** Última tela de corrida (navegação, embarque, em viagem) desta plataforma. */
    val lastTripScreenAt: Long? = null,
    /** Desde quando está EM VIAGEM (tela de embarque depois de 1 min em viagem = corrida nova). */
    val inTripSince: Long? = null,
    /** Quando a oferta da fila chegou (a fila vence: oferta velha não vira a próxima corrida). */
    val queuedAt: Long? = null,
    val lastEndedAt: Long? = null,
)

data class Transition(val context: TripContext, val effects: List<TripEffect>)
