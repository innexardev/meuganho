package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.domain.model.TripState

/** Tempo por fase, derivado dos timestamps de cada corrida. */
data class TripTimes(val toPickup: Long, val waiting: Long, val inTrip: Long) {
    companion object {
        fun of(trips: List<TripFacts>): TripTimes {
            var toPickup = 0L
            var waiting = 0L
            var inTrip = 0L
            for (t in trips) {
                val arrived = t.pickupArrivedAt ?: t.startedAt
                toPickup += seconds(t.acceptedAt, arrived ?: t.endedAt)
                if (t.pickupArrivedAt != null) waiting += seconds(t.pickupArrivedAt, t.startedAt ?: t.endedAt)
                if (t.state == TripState.COMPLETED && t.startedAt != null) inTrip += seconds(t.startedAt, t.endedAt)
            }
            return TripTimes(toPickup, waiting, inTrip)
        }

        private fun seconds(from: Long?, to: Long?): Long =
            if (from == null || to == null) 0 else ((to - from) / 1000).coerceAtLeast(0)
    }
}
