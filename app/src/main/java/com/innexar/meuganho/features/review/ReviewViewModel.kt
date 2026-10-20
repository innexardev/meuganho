package com.innexar.meuganho.features.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.remote.StatsSync
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.TripState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewState(val offers: List<OfferEntity> = emptyList(), val trips: List<TripEntity> = emptyList())

/**
 * Revisão fora do volante. Confirmar ou corrigir NUNCA apaga o valor capturado:
 * a correção fica em correctedCents, preservando a auditoria.
 */
class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    private val offerDao = container.database.offerDao()
    private val tripDao = container.database.tripDao()

    val state: StateFlow<ReviewState> = combine(offerDao.observeNeedingReview(), tripDao.observeNeedingReview()) { o, t ->
        ReviewState(o, t)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewState())

    fun confirmOffer(offer: OfferEntity, correctedCents: Long?) = viewModelScope.launch {
        offerDao.review(offer.id, DataStatus.CONFIRMED, correctedCents, System.currentTimeMillis())
        container.statsSync.enqueue(StatsSync.Kind.OFFER, offer.id)
        // A corrida aceita a partir desta oferta usava o valor antigo como estimativa: atualiza junto.
        if (correctedCents != null) {
            tripDao.byOfferId(offer.id)?.let { trip ->
                tripDao.update(trip.copy(estimatedCents = correctedCents))
                container.statsSync.enqueue(StatsSync.Kind.TRIP, trip.id)
            }
        }
    }

    fun discardOffer(offer: OfferEntity) = viewModelScope.launch {
        offerDao.review(offer.id, DataStatus.INVALID, null, System.currentTimeMillis())
        container.statsSync.enqueue(StatsSync.Kind.OFFER, offer.id)
    }

    fun confirmTrip(trip: TripEntity, correctedCents: Long?) = viewModelScope.launch {
        val state = if (trip.state == TripState.UNKNOWN) TripState.COMPLETED else trip.state
        tripDao.update(trip.copy(status = DataStatus.CONFIRMED, correctedCents = correctedCents ?: trip.correctedCents,
            state = state, reviewedAt = System.currentTimeMillis()))
        container.statsSync.enqueue(StatsSync.Kind.TRIP, trip.id)
    }

    fun discardTrip(trip: TripEntity) = viewModelScope.launch {
        tripDao.update(trip.copy(status = DataStatus.INVALID, reviewedAt = System.currentTimeMillis()))
        container.statsSync.enqueue(StatsSync.Kind.TRIP, trip.id)
    }
}
