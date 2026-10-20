package com.innexar.meuganho.domain

import com.innexar.meuganho.core.time.DateRange
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.stats.Reconciliation
import com.innexar.meuganho.domain.stats.ShiftFacts
import com.innexar.meuganho.domain.stats.SummaryCalculator
import com.innexar.meuganho.domain.stats.TripFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryCalculatorTest {
    private val hour = 3_600_000L
    private val range = DateRange(0, 24 * hour)
    private val cost = CostModel(600, 10.0, 10.0, 15.0, 0.0)

    private fun trip(
        value: Long?, status: DataStatus = DataStatus.AUTO_ACCEPTED, platform: Platform = Platform.UBER,
        start: Long = hour, state: TripState = TripState.COMPLETED, paid: Long = 10_000, pickup: Long = 2_000,
    ) = TripFacts(platform, state, status, value, acceptedAt = start, pickupArrivedAt = start + 300_000,
        startedAt = start + 420_000, endedAt = start + 1_620_000, pickupMeters = pickup, paidMeters = paid)

    private val shift = ShiftFacts(0, 4 * hour, pausedMillis = 0, startOdometerKm = null, endOdometerKm = null, gpsMeters = 60_000)

    @Test fun `pendente fica fora do bruto mas aparece separado`() {
        val s = SummaryCalculator.summarize(
            range, listOf(trip(2000), trip(3000, platform = Platform.NINETY_NINE), trip(2850, status = DataStatus.NEEDS_REVIEW)),
            listOf(shift), 0, 0, cost, now = 5 * hour,
        )
        assertEquals(5000L, s.grossCents)
        assertEquals(2, s.tripCount)
        assertEquals(1, s.pendingCount)
        assertEquals(2850L, s.pendingCents)
        assertEquals(2000L, s.byPlatform[Platform.UBER]?.grossCents)
        assertEquals(3000L, s.byPlatform[Platform.NINETY_NINE]?.grossCents)
    }

    @Test fun `inválidos e cancelados sem taxa não contam`() {
        val s = SummaryCalculator.summarize(
            range, listOf(trip(2000), trip(9999, status = DataStatus.INVALID), trip(null, state = TripState.CANCELLED)),
            listOf(shift), 0, 0, cost, now = 5 * hour,
        )
        assertEquals(2000L, s.grossCents)
        assertEquals(1, s.tripCount)
    }

    @Test fun `separa tempos e calcula R$ por hora online e em corrida`() {
        val s = SummaryCalculator.summarize(range, listOf(trip(4000)), listOf(shift), 0, 0, cost, now = 5 * hour)
        assertEquals(4 * 3600L, s.onlineSeconds)
        assertEquals(300L, s.toPickupSeconds)
        assertEquals(120L, s.waitingSeconds)
        assertEquals(1200L, s.inTripSeconds)
        assertEquals(4 * 3600L - 1620, s.idleSeconds)
        assertEquals(1000L, s.centsPerHourOnline) // R$ 40 em 4 h
        assertEquals(12_000L, s.centsPerHourInTrip) // R$ 40 em 20 min
    }

    @Test fun `odômetro tem prioridade sobre GPS e separa km vazio`() {
        val withOdometer = shift.copy(startOdometerKm = 1000, endOdometerKm = 1080)
        val s = SummaryCalculator.summarize(range, listOf(trip(4000, paid = 30_000)), listOf(withOdometer), 0, 0, cost, now = 5 * hour)
        assertEquals(80_000L, s.totalMeters)
        assertEquals(50_000L, s.emptyMeters)
        assertEquals(60_000L, s.gpsMeters)
    }

    @Test fun `caixa e resultado são visões diferentes`() {
        val s = SummaryCalculator.summarize(range, listOf(trip(10_000)), listOf(shift), fuelPurchasedCents = 5_000,
            expensesCents = 1_500, costModel = cost, now = 5 * hour)
        assertEquals(10_000L - 5_000 - 1_500, s.cashCents)
        // 60 km * (R$ 0,60 + R$ 0,25) = R$ 51,00
        assertEquals(5_100L, s.operationalCostCents)
        assertEquals(10_000L - 5_100 - 1_500, s.operationalResultCents)
    }

    @Test fun `turno aberto usa o horário atual e pausa sai do online`() {
        val open = ShiftFacts(0, null, pausedMillis = hour, startOdometerKm = null, endOdometerKm = null, gpsMeters = 0)
        val s = SummaryCalculator.summarize(range, emptyList(), listOf(open), 0, 0, cost, now = 3 * hour)
        assertEquals(3 * 3600L, s.workedSeconds)
        assertEquals(2 * 3600L, s.onlineSeconds)
        assertNull(s.centsPerHourOnline?.takeIf { it > 0 })
    }

    @Test fun `pausa em andamento (automática) já sai do online`() {
        val paused = ShiftFacts(0, null, pausedMillis = 0, pausedAt = 2 * hour, startOdometerKm = null, endOdometerKm = null, gpsMeters = 0)
        val s = SummaryCalculator.summarize(range, emptyList(), listOf(paused), 0, 0, cost, now = 5 * hour)
        assertEquals(2 * 3600L, s.onlineSeconds)
    }

    @Test fun `reconciliação com o total do app`() {
        val s = SummaryCalculator.summarize(range, listOf(trip(18_000)), listOf(shift), 0, 0, cost, now = 5 * hour)
        val ok = Reconciliation.of(s.byPlatform, mapOf(Platform.UBER to 18_050L)).single()
        assertEquals(true, ok.reconciled)
        val missing = Reconciliation.of(s.byPlatform, mapOf(Platform.UBER to 20_700L)).single()
        assertEquals(false, missing.reconciled)
        assertEquals(2_700L, missing.differenceCents)
        // Sem leitura do app: indefinido, nunca "ok".
        assertNull(Reconciliation.of(s.byPlatform, emptyMap()).single().reconciled)
    }
}
