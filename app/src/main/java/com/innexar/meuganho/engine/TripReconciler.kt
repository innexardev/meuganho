package com.innexar.meuganho.engine

import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.local.dao.OfferDao
import com.innexar.meuganho.data.local.dao.TripDao
import com.innexar.meuganho.data.remote.StatsSync
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import com.innexar.meuganho.domain.trip.OfferMatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Conciliador: roda de tempos em tempos e depois de cada leitura oficial. Não depende de ter visto
 * cada tela na ordem; cruza as pistas que ficaram gravadas.
 *
 * 1. Corrida sem oferta ligada -> procura a oferta (mesmo valor oficial, ou a última antes do aceite)
 *    e marca a oferta como ACEITA. Real (29/09/2026): 6 corridas do Uber, só 1 oferta marcada aceita.
 * 2. "Cancelada" com km rodados COM passageiro (GPS no trecho pago) -> foi feita: vira concluída,
 *    para revisão, com o valor da oferta.
 */
class TripReconciler(
    private val tripDao: TripDao,
    private val offerDao: OfferDao,
    private val stats: StatsSync,
) {
    private val mutex = Mutex()

    data class Result(val linked: Int, val revived: Int)

    suspend fun run(now: Long): Result = mutex.withLock {
        val since = now - LOOKBACK_MS
        var linked = 0
        var revived = 0

        for (trip in tripDao.withoutOfferSince(since)) {
            if (trip.state != TripState.COMPLETED) continue
            val official = trip.finalCents.takeIf {
                trip.valueSource == ValueSource.PLATFORM_HISTORY || trip.valueSource == ValueSource.PLATFORM_TOTAL ||
                    trip.valueSource == ValueSource.COMPLETION_SCREEN
            }
            val candidates = offerDao.unlinkedBetween(
                trip.platform.name, trip.acceptedAt - OfferMatcher.OFFICIAL_BEFORE_MS, trip.acceptedAt + OfferMatcher.AFTER_MS,
            ).map { OfferMatcher.Candidate(it.id, it.detectedAt, it.effectiveCents) }
            val match = OfferMatcher.match(trip.acceptedAt, official, candidates) ?: continue
            val offer = offerDao.byId(match.offerId) ?: continue
            tripDao.update(trip.copy(
                offerId = offer.id,
                estimatedCents = trip.estimatedCents ?: offer.effectiveCents,
                estPickupMeters = trip.estPickupMeters ?: offer.pickupMeters,
                estPickupSeconds = trip.estPickupSeconds ?: offer.pickupSeconds,
                estTripMeters = trip.estTripMeters ?: offer.tripMeters,
                estTripSeconds = trip.estTripSeconds ?: offer.tripSeconds,
                valueSource = if (trip.valueSource == ValueSource.NONE && offer.effectiveCents != null) ValueSource.OFFER_ESTIMATE else trip.valueSource,
                reasons = listOf(trip.reasons, "Oferta ligada pelo conciliador").filter { it.isNotBlank() }.joinToString(" | "),
            ))
            offerDao.setOutcome(offer.id, OfferOutcome.ACCEPTED)
            stats.enqueue(StatsSync.Kind.TRIP, trip.id)
            stats.enqueue(StatsSync.Kind.OFFER, offer.id)
            linked++
        }

        // Ligada à oferta ERRADA: o valor oficial não bate com ela, mas bate com outra oferta daquele momento
        // (várias ofertas da 99 durante a corrida; simulação 30/09/2026). Religa pela fonte oficial.
        for (trip in tripDao.officialWithOfferSince(since)) {
            val official = trip.finalCents ?: continue
            val current = trip.offerId?.let { offerDao.byId(it) } ?: continue
            val cents = current.effectiveCents ?: continue
            if (OfferMatcher.sameValue(cents, official)) continue
            val candidates = offerDao.unlinkedBetween(
                trip.platform.name, trip.acceptedAt - OfferMatcher.OFFICIAL_BEFORE_MS, trip.acceptedAt + OfferMatcher.AFTER_MS,
            ).map { OfferMatcher.Candidate(it.id, it.detectedAt, it.effectiveCents) }
            val match = OfferMatcher.match(trip.acceptedAt, official, candidates) ?: continue
            val offer = offerDao.byId(match.offerId) ?: continue
            tripDao.update(trip.copy(
                offerId = offer.id, estimatedCents = offer.effectiveCents,
                estPickupMeters = offer.pickupMeters, estPickupSeconds = offer.pickupSeconds,
                estTripMeters = offer.tripMeters, estTripSeconds = offer.tripSeconds,
                reasons = listOf(trip.reasons, "Oferta corrigida pelo valor oficial").filter { it.isNotBlank() }.joinToString(" | "),
            ))
            offerDao.setOutcome(current.id, OfferOutcome.NOT_ACCEPTED)
            offerDao.setOutcome(offer.id, OfferOutcome.ACCEPTED)
            stats.enqueue(StatsSync.Kind.TRIP, trip.id)
            stats.enqueue(StatsSync.Kind.OFFER, current.id)
            stats.enqueue(StatsSync.Kind.OFFER, offer.id)
            linked++
        }

        for (trip in tripDao.cancelledSince(since)) {
            if (trip.gpsPaidMeters < PAID_METERS_DONE) continue
            tripDao.update(trip.copy(
                state = TripState.COMPLETED,
                status = DataStatus.NEEDS_REVIEW,
                reasons = listOf(trip.reasons, "GPS registrou ${trip.gpsPaidMeters / 1000.0} km com passageiro: a corrida foi feita, confira o valor")
                    .filter { it.isNotBlank() }.joinToString(" | "),
            ))
            stats.enqueue(StatsSync.Kind.TRIP, trip.id)
            revived++
        }
        // Radar sem valor oficial: as ofertas ficariam "pendentes" para sempre. Continuam ligáveis depois.
        offerDao.resolveStalePending(now - 2 * 3_600_000L)
        if (linked + revived > 0) AppLog.d(LogCategory.TRIP, "Conciliador: $linked ofertas ligadas, $revived canceladas corrigidas")
        Result(linked, revived)
    }

    private companion object {
        const val LOOKBACK_MS = 36 * 3_600_000L
        const val PAID_METERS_DONE = 1_000L
    }
}
