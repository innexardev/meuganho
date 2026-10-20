package com.innexar.meuganho.engine

import com.innexar.meuganho.data.local.dao.ShiftDao
import com.innexar.meuganho.data.local.dao.TripDao
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.remote.StatsSync
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.PlatformTripRecord
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import java.time.ZoneId
import kotlin.math.abs

/**
 * Concilia o histórico OFICIAL da plataforma com as corridas do app:
 * - corrida do app no mesmo horário (±25 min) -> recebe o valor oficial, vira concluída e confirmada
 *   (inclusive as que a leitura marcou como canceladas por engano);
 * - corrida que o app não viu -> é criada;
 * - já importada (mesmo valor, ±2 min) -> ignorada: abrir o histórico de novo não duplica.
 */
class HistoryImporter(
    private val tripDao: TripDao,
    private val shiftDao: ShiftDao,
    private val stats: StatsSync,
) {
    data class Result(val updated: Int, val created: Int)

    suspend fun import(records: List<PlatformTripRecord>): Result {
        var updated = 0
        var created = 0
        val used = mutableSetOf<Long>() // dois lançamentos no mesmo minuto são duas corridas
        for (r in records.sortedBy { it.date.atTime(it.time) }) {
            val at = r.date.atTime(r.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Histórico do Uber: horário do início. Extrato da 99: horário do fim/pagamento.
            val nearby = if (r.timeIsEnd) {
                tripDao.platformTripsBetween(r.platform.name, at - END_LOOKBACK_MS, at + WINDOW_MS)
                    .filter { abs((it.endedAt ?: it.acceptedAt) - at) <= WINDOW_MS }
            } else {
                tripDao.platformTripsBetween(r.platform.name, at - WINDOW_MS, at + WINDOW_MS)
            }.filter { it.id !in used }
            fun ref(t: TripEntity) = if (r.timeIsEnd) (t.endedAt ?: t.acceptedAt) else t.acceptedAt

            if (r.cashFeeCents != null) {
                val tag = "extrato da 99 (taxa ${com.innexar.meuganho.core.money.Money.format(r.cashFeeCents)})"
                val already = nearby.firstOrNull { it.reasons.contains(tag) && abs(ref(it) - at) <= SAME_MS }
                if (already != null) { used += already.id; continue }
                val match = nearby.filter { it.valueSource != ValueSource.PLATFORM_HISTORY }.minByOrNull { abs(ref(it) - at) }
                if (match != null) {
                    used += match.id
                    tripDao.update(match.copy(
                        state = TripState.COMPLETED,
                        endedAt = match.endedAt ?: at,
                        status = if (match.effectiveCents != null) DataStatus.AUTO_ACCEPTED else DataStatus.NEEDS_REVIEW,
                        reasons = listOf(match.reasons, "Corrida em dinheiro conferida no $tag").filter { it.isNotBlank() }.joinToString(" | "),
                    ))
                    stats.enqueue(StatsSync.Kind.TRIP, match.id)
                    updated++
                } else {
                    val id = tripDao.insert(TripEntity(
                        shiftId = null, offerId = null, platform = r.platform, state = TripState.COMPLETED,
                        status = DataStatus.NEEDS_REVIEW, acceptedAt = at, endedAt = at,
                        reasons = "Corrida em dinheiro no $tag: informe quanto recebeu",
                    ))
                    used += id
                    stats.enqueue(StatsSync.Kind.TRIP, id)
                    created++
                }
                continue
            }

            val already = nearby.firstOrNull {
                it.valueSource == ValueSource.PLATFORM_HISTORY && it.finalCents == r.valueCents && abs(ref(it) - at) <= SAME_MS
            }
            if (already != null) { used += already.id; continue }
            val match = nearby.filter { it.valueSource != ValueSource.PLATFORM_HISTORY }.minByOrNull { abs(ref(it) - at) }
            if (match != null) {
                used += match.id
                tripDao.update(confirm(match, r, at))
                stats.enqueue(StatsSync.Kind.TRIP, match.id)
                updated++
            } else {
                val acceptedAt = if (r.timeIsEnd) at - r.durationSeconds * 1000L else at
                val id = tripDao.insert(confirm(TripEntity(
                    shiftId = null, // corrida do histórico: pode ser de outro dia; não prende ao turno atual
                    offerId = null, platform = r.platform, state = TripState.COMPLETED, status = DataStatus.CONFIRMED,
                    acceptedAt = acceptedAt,
                ), r, at).copy(reasons = "Importada do histórico de ganhos da plataforma"))
                used += id
                stats.enqueue(StatsSync.Kind.TRIP, id)
                created++
            }
        }
        return Result(updated, created)
    }

    private fun confirm(t: TripEntity, r: PlatformTripRecord, at: Long): TripEntity {
        val start = t.startedAt ?: if (r.timeIsEnd) (t.pickupArrivedAt ?: t.acceptedAt) else at
        return t.copy(
            state = TripState.COMPLETED,
            status = DataStatus.CONFIRMED,
            finalCents = r.valueCents,
            correctedCents = null,
            valueSource = ValueSource.PLATFORM_HISTORY,
            startedAt = start,
            endedAt = when {
                r.timeIsEnd -> at
                r.durationSeconds > 0 -> start + r.durationSeconds * 1000L
                else -> t.endedAt ?: start
            },
            manualMeters = r.meters.takeIf { it > 0 } ?: t.manualMeters,
            reasons = if (r.timeIsEnd) "Conferida no extrato da ${r.platform.label} (${r.category})" else "Conferida no histórico de ganhos (${r.category})",
        )
    }

    private companion object {
        const val WINDOW_MS = 25 * 60_000L
        const val SAME_MS = 2 * 60_000L
        /** Extrato (horário do fim): a corrida pode ter começado até 3 h antes. */
        const val END_LOOKBACK_MS = 3 * 3_600_000L
    }
}
