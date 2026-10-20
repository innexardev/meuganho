package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.core.time.DateRange
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.financial.Rates
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState

object SummaryCalculator {

    fun summarize(
        range: DateRange,
        trips: List<TripFacts>,
        shifts: List<ShiftFacts>,
        fuelPurchasedCents: Long,
        expensesCents: Long,
        costModel: CostModel,
        now: Long,
    ): PeriodSummary {
        val paid = trips.filter { it.countsAsEarning() && it.status.countsInStats }
        val pending = trips.filter { it.status.isPending && it.state != TripState.CANCELLED }
        val gross = paid.sumOf { it.valueCents ?: 0 }

        val times = TripTimes.of(trips)
        val worked = shifts.sumOf { clampedDuration(it.startedAt, it.endedAt ?: now, range) }
        val online = shifts.sumOf { s ->
            val span = clampedDuration(s.startedAt, s.endedAt ?: now, range)
            val paused = (s.pausedMillis + (s.pausedAt?.let { now - it } ?: 0)) / 1000
            (span - paused).coerceAtLeast(0)
        }
        val busy = times.toPickup + times.waiting + times.inTrip

        val tripPaidMeters = trips.sumOf { it.paidMeters }
        val tripPickupMeters = trips.sumOf { it.pickupMeters }
        val gpsMeters = shifts.sumOf { it.gpsMeters }
        val odometerMeters = odometerMeters(shifts)
        // Prioridade para km total: odômetro (medido) > GPS do turno > soma das corridas.
        val totalMeters = maxOf(odometerMeters ?: gpsMeters.takeIf { it > 0 } ?: 0L, tripPaidMeters + tripPickupMeters)

        val operationalCost = costModel.operationalCostCents(totalMeters)
        return PeriodSummary(
            tripCount = paid.size,
            grossCents = gross,
            pendingCount = pending.size,
            pendingCents = pending.sumOf { it.valueCents ?: 0 },
            byPlatform = byPlatform(paid),
            workedSeconds = worked,
            onlineSeconds = online,
            toPickupSeconds = times.toPickup,
            waitingSeconds = times.waiting,
            inTripSeconds = times.inTrip,
            idleSeconds = (online - busy).coerceAtLeast(0),
            totalMeters = totalMeters,
            paidMeters = tripPaidMeters,
            pickupMeters = tripPickupMeters,
            emptyMeters = (totalMeters - tripPaidMeters).coerceAtLeast(0),
            gpsMeters = gpsMeters,
            odometerMeters = odometerMeters,
            fuelEstimateCents = costModel.fuelCostCents(totalMeters),
            fuelPurchasedCents = fuelPurchasedCents,
            expensesCents = expensesCents,
            operationalCostCents = operationalCost,
            cashCents = gross - fuelPurchasedCents - expensesCents,
            operationalResultCents = gross - operationalCost - expensesCents,
            centsPerHourOnline = Rates.centsPerHour(gross, online),
            centsPerHourInTrip = Rates.centsPerHour(gross, times.inTrip),
            centsPerKm = Rates.centsPerKm(gross, totalMeters),
        )
    }

    private fun TripFacts.countsAsEarning(): Boolean =
        state == TripState.COMPLETED || (state == TripState.CANCELLED && (valueCents ?: 0) > 0)

    private fun byPlatform(trips: List<TripFacts>): Map<Platform, PlatformSlice> =
        trips.groupBy { it.platform }.mapValues { (_, list) ->
            PlatformSlice(
                tripCount = list.size,
                grossCents = list.sumOf { it.valueCents ?: 0 },
                paidMeters = list.sumOf { it.paidMeters },
                inTripSeconds = TripTimes.of(list).inTrip,
            )
        }

    /** Só usa odômetro se TODOS os turnos do período têm início e fim informados. */
    private fun odometerMeters(shifts: List<ShiftFacts>): Long? {
        if (shifts.isEmpty()) return null
        if (shifts.any { it.startOdometerKm == null || it.endOdometerKm == null }) return null
        val km = shifts.sumOf { (it.endOdometerKm!! - it.startOdometerKm!!).coerceAtLeast(0) }
        return km * 1000L
    }

    private fun clampedDuration(start: Long, end: Long, range: DateRange): Long {
        val from = maxOf(start, range.startMillis)
        val to = minOf(end, range.endMillis)
        return ((to - from) / 1000).coerceAtLeast(0)
    }
}
