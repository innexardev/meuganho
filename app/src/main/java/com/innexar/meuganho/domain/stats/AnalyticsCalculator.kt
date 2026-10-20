package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.domain.financial.Rates
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState
import java.time.Instant
import java.time.ZoneId

data class Bucket(val key: Int, val grossCents: Long, val onlineSeconds: Long, val paidMeters: Long, val trips: Int) {
    val centsPerHour: Long? get() = Rates.centsPerHour(grossCents, onlineSeconds)
    val centsPerKm: Long? get() = Rates.centsPerKm(grossCents, paidMeters)
}

data class PlatformComparison(
    val platform: Platform,
    val trips: Int,
    val grossCents: Long,
    val averageTicketCents: Long?,
    val centsPerKm: Long?,
    val centsPerHourInTrip: Long?,
    val averageTripSeconds: Long?,
    val averageTripMeters: Long?,
)

/**
 * Inteligência local: tudo calculado no aparelho a partir do histórico.
 *
 * R$/h por horário usa tempo ONLINE naquele horário (dividindo cada turno em
 * fatias de hora), não só tempo em corrida, porque é o que decide se vale a pena
 * estar na rua naquela hora.
 */
object AnalyticsCalculator {

    fun byHourOfDay(trips: List<TripFacts>, shifts: List<ShiftFacts>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Bucket> =
        buckets(0..23, trips, shifts, now) { Instant.ofEpochMilli(it).atZone(zone).hour }

    fun byDayOfWeek(trips: List<TripFacts>, shifts: List<ShiftFacts>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Bucket> =
        buckets(1..7, trips, shifts, now) { Instant.ofEpochMilli(it).atZone(zone).dayOfWeek.value }

    fun comparePlatforms(trips: List<TripFacts>): List<PlatformComparison> =
        trips.filter { it.state == TripState.COMPLETED && it.status.countsInStats }
            .groupBy { it.platform }
            .map { (platform, list) ->
                val gross = list.sumOf { it.valueCents ?: 0 }
                val meters = list.sumOf { it.paidMeters }
                val inTrip = TripTimes.of(list).inTrip
                PlatformComparison(
                    platform = platform,
                    trips = list.size,
                    grossCents = gross,
                    averageTicketCents = gross / list.size,
                    centsPerKm = Rates.centsPerKm(gross, meters),
                    centsPerHourInTrip = Rates.centsPerHour(gross, inTrip),
                    averageTripSeconds = inTrip / list.size,
                    averageTripMeters = meters / list.size,
                )
            }
            .sortedByDescending { it.grossCents }

    private fun buckets(
        keys: IntRange,
        trips: List<TripFacts>,
        shifts: List<ShiftFacts>,
        now: Long,
        keyOf: (Long) -> Int,
    ): List<Bucket> {
        val gross = LongArray(keys.last + 1)
        val meters = LongArray(keys.last + 1)
        val count = IntArray(keys.last + 1)
        val online = LongArray(keys.last + 1)
        trips.filter { it.state == TripState.COMPLETED && it.status.countsInStats }.forEach {
            val key = keyOf(it.endedAt ?: it.acceptedAt)
            gross[key] += it.valueCents ?: 0
            meters[key] += it.paidMeters
            count[key]++
        }
        shifts.forEach { shift ->
            var cursor = shift.startedAt
            val end = shift.endedAt ?: now
            // Fatias de no máximo 1h, alinhadas ao início da hora cheia.
            while (cursor < end) {
                val next = minOf(end, (cursor / HOUR_MS + 1) * HOUR_MS)
                online[keyOf(cursor)] += (next - cursor) / 1000
                cursor = next
            }
        }
        return keys.map { Bucket(it, gross[it], online[it], meters[it], count[it]) }
    }

    private const val HOUR_MS = 3_600_000L
}
