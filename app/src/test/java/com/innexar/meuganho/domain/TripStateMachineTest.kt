package com.innexar.meuganho.domain

import com.innexar.meuganho.domain.model.DriveSegment
import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.Platform.NINETY_NINE
import com.innexar.meuganho.domain.model.Platform.UBER
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.trip.TripContext
import com.innexar.meuganho.domain.trip.TripEffect
import com.innexar.meuganho.domain.trip.TripSignal
import com.innexar.meuganho.domain.trip.TripStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStateMachineTest {

    /** Aplica sinais em sequência e acumula os efeitos. */
    private class Run(var ctx: TripContext = TripContext()) {
        val effects = mutableListOf<TripEffect>()
        fun send(signal: TripSignal, at: Long): Run {
            val t = TripStateMachine.reduce(ctx, signal, at)
            ctx = t.context
            effects += t.effects
            return this
        }
    }

    @Test fun `ciclo completo oferta - busca - espera - corrida - concluída`() {
        val r = Run()
            .send(TripSignal.OfferShown(UBER, 7), 0)
            .send(TripSignal.HeadingToPickup(UBER), 3_000)
            .send(TripSignal.WaitingPassenger(UBER), 300_000)
            .send(TripSignal.InTrip(UBER), 420_000)
            .send(TripSignal.Completed(UBER, 2_690), 1_800_000)
        assertEquals(TripState.COMPLETED, r.ctx.state)
        assertTrue(r.effects.contains(TripEffect.ResolveOffer(7, OfferOutcome.ACCEPTED)))
        assertTrue(r.effects.contains(TripEffect.StartTrip(UBER, 7, TripState.TO_PICKUP, 3_000)))
        assertTrue(r.effects.contains(TripEffect.UpdateTripState(TripState.WAITING_PASSENGER, 300_000)))
        assertTrue(r.effects.contains(TripEffect.UpdateTripState(TripState.IN_TRIP, 420_000)))
        assertEquals(TripEffect.CompleteTrip(2_690, 1_800_000), r.effects.last())
    }

    @Test fun `piscada de tela durante a oferta não conta como recusa`() {
        val r = Run().send(TripSignal.OfferShown(UBER, 1), 0).send(TripSignal.Idle(UBER), 5_000)
        assertEquals(TripState.OFFERED, r.ctx.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test fun `oferta que some depois da janela vira não aceita`() {
        val r = Run().send(TripSignal.OfferShown(UBER, 1), 0)
            .send(TripSignal.Idle(UBER), TripStateMachine.OFFER_DECISION_WINDOW_MS + 1)
        assertEquals(TripState.REJECTED, r.ctx.state)
        assertEquals(listOf(TripEffect.ResolveOffer(1, OfferOutcome.NOT_ACCEPTED)), r.effects)
    }

    @Test fun `nova oferta substitui a anterior, que vira não aceita`() {
        val r = Run().send(TripSignal.OfferShown(UBER, 1), 0).send(TripSignal.OfferShown(UBER, 2), 10_000)
        assertEquals(2L, r.ctx.offerId)
        assertEquals(listOf(TripEffect.ResolveOffer(1, OfferOutcome.NOT_ACCEPTED)), r.effects)
    }

    @Test fun `corrida encadeada usa a oferta recebida durante a corrida`() {
        val r = Run()
            .send(TripSignal.OfferShown(UBER, 1), 0)
            .send(TripSignal.HeadingToPickup(UBER), 1_000)
            .send(TripSignal.InTrip(UBER), 200_000)
            .send(TripSignal.Navigating(UBER), 880_000) // navegação contínua (eventos o tempo todo)
            .send(TripSignal.OfferShown(UBER, 2), 900_000) // próxima corrida chega durante a atual
            .send(TripSignal.HeadingToPickup(UBER), 1_000_000) // Uber pula o resumo
        assertEquals(TripState.TO_PICKUP, r.ctx.state)
        assertTrue(r.effects.contains(TripEffect.CompleteTrip(null, 1_000_000)))
        assertTrue(r.effects.contains(TripEffect.StartTrip(UBER, 2, TripState.TO_PICKUP, 1_000_000)))
        assertTrue(r.effects.contains(TripEffect.ResolveOffer(2, OfferOutcome.ACCEPTED)))
    }

    @Test fun `tela de outra plataforma não mexe na corrida ativa`() {
        val r = Run().send(TripSignal.HeadingToPickup(UBER), 0).send(TripSignal.Idle(NINETY_NINE), 5_000)
        assertEquals(TripState.TO_PICKUP, r.ctx.state)
    }

    @Test fun `voltar à busca antes de iniciar é cancelamento`() {
        val r = Run().send(TripSignal.HeadingToPickup(UBER), 0).send(TripSignal.Idle(UBER), 60_000)
        assertEquals(TripState.CANCELLED, r.ctx.state)
        assertTrue(r.effects.last() is TripEffect.CancelTrip)
    }

    @Test fun `tela final vista depois completa o valor`() {
        val r = Run().send(TripSignal.InTrip(UBER), 0)
            .send(TripSignal.Idle(UBER), 600_000) // voltou ao mapa sem resumo
            .send(TripSignal.Completed(UBER, 1_990), 610_000) // resumo aberto depois
        assertEquals(TripEffect.SetCompletedValue(1_990), r.effects.last())
    }

    @Test fun `corrida sem sinais por 3h é abandonada`() {
        // Começa em t=1 s: lastSignalAt = 0 significa "nenhum sinal ainda".
        val r = Run().send(TripSignal.InTrip(UBER), 1_000)
            .send(TripSignal.OfferShown(UBER, 9), 1_000 + TripStateMachine.ABANDON_AFTER_MS + 1)
        assertTrue(r.effects.any { it is TripEffect.AbandonTrip })
        assertEquals(TripState.OFFERED, r.ctx.state)
    }

    @Test fun `telas antigas não fazem o estado voltar`() {
        val r = Run().send(TripSignal.HeadingToPickup(UBER), 0).send(TripSignal.InTrip(UBER), 1_000)
            .send(TripSignal.WaitingPassenger(UBER), 2_000)
        assertEquals(TripState.IN_TRIP, r.ctx.state)
    }

    @Test fun `segmentos de GPS por estado`() {
        assertEquals(DriveSegment.PICKUP, TripStateMachine.segmentFor(TripState.TO_PICKUP))
        assertEquals(DriveSegment.PICKUP, TripStateMachine.segmentFor(TripState.WAITING_PASSENGER))
        assertEquals(DriveSegment.PAID, TripStateMachine.segmentFor(TripState.IN_TRIP))
        assertEquals(DriveSegment.EMPTY, TripStateMachine.segmentFor(TripState.OFFERED))
    }

    // ---- Real 29/09/2026: o Uber navega sem "Encontro com"/"Destino de" ----

    @Test fun `navegando logo após a oferta é aceite e inicia a corrida`() {
        val r = Run().send(TripSignal.OfferShown(UBER, 11), 0).send(TripSignal.Navigating(UBER), 4_000)
        assertEquals(TripState.TO_PICKUP, r.ctx.state)
        assertTrue(r.effects.contains(TripEffect.ResolveOffer(11, OfferOutcome.ACCEPTED)))
    }

    @Test fun `navegando depois de aguardar o passageiro é embarque`() {
        val r = Run().send(TripSignal.HeadingToPickup(UBER), 0).send(TripSignal.WaitingPassenger(UBER), 60_000)
            .send(TripSignal.Navigating(UBER), 180_000)
        assertEquals(TripState.IN_TRIP, r.ctx.state)
    }

    @Test fun `rota residual logo após o fim não abre corrida nova`() {
        val r = Run().send(TripSignal.HeadingToPickup(UBER), 0).send(TripSignal.InTrip(UBER), 60_000)
            .send(TripSignal.Completed(UBER, null), 900_000).send(TripSignal.Navigating(UBER), 930_000)
        assertEquals(TripState.COMPLETED, r.ctx.state)
        assertEquals(1, r.effects.count { it is TripEffect.StartTrip })
    }

    @Test fun `volta à busca muito depois do aceite conta como concluída, não cancelada`() {
        val r = Run().send(TripSignal.Navigating(UBER), 0).send(TripSignal.Idle(UBER), 15 * 60_000L)
        assertTrue(r.effects.any { it is TripEffect.CompleteTrip })
        assertTrue(r.effects.none { it is TripEffect.CancelTrip })
    }

    @Test fun `volta à busca logo após o aceite é cancelamento`() {
        val r = Run().send(TripSignal.Navigating(UBER), 0).send(TripSignal.Idle(UBER), 3 * 60_000L)
        assertTrue(r.effects.any { it is TripEffect.CancelTrip })
    }

    @Test fun `contextos separados por app - oferta da 99 não derruba o aceite do Uber`() {
        // Como o TripTracker faz: um Run por plataforma.
        val uber = Run().send(TripSignal.OfferShown(UBER, 20), 0)
        val nn = Run().send(TripSignal.OfferShown(NINETY_NINE, 21), 1_000)
        uber.send(TripSignal.Navigating(UBER), 3_000)
        assertTrue(uber.effects.contains(TripEffect.ResolveOffer(20, OfferOutcome.ACCEPTED)))
        assertEquals(TripState.OFFERED, nn.ctx.state)
    }

    // ---- Real 30/09/2026 ----

    @Test fun `oferta velha na fila não vira a próxima corrida`() {
        val r = Run().send(TripSignal.HeadingToPickup(UBER), 0)
            .send(TripSignal.InTrip(UBER), 60_000)
            .send(TripSignal.OfferShown(UBER, 463), 120_000)       // chegou durante a corrida
            .send(TripSignal.Completed(UBER, 1_981), 600_000)      // corrida terminou; ninguém aceitou a 463
            .send(TripSignal.OfferShown(UBER, 470), 70 * 60_000L)  // 1 h depois, nova oferta
            .send(TripSignal.HeadingToPickup(UBER), 70 * 60_000L + 25_000)
        assertTrue(r.effects.contains(TripEffect.ResolveOffer(470, OfferOutcome.ACCEPTED)))
        assertTrue(r.effects.contains(TripEffect.ResolveOffer(463, OfferOutcome.NOT_ACCEPTED)))
        assertTrue(r.effects.none { it == TripEffect.ResolveOffer(463, OfferOutcome.ACCEPTED) })
    }

    @Test fun `navegação some por 6 min em viagem - concluída no horário da última tela`() {
        var r = Run().send(TripSignal.Navigating(NINETY_NINE), 0)
            .send(TripSignal.WaitingPassenger(NINETY_NINE), 5 * 60_000L)
            .send(TripSignal.Navigating(NINETY_NINE), 7 * 60_000L)
            .send(TripSignal.Navigating(NINETY_NINE), 20 * 60_000L)
        r = r.send(TripSignal.Tick(NINETY_NINE), 23 * 60_000L)
        assertEquals(TripState.IN_TRIP, r.ctx.state)                 // 3 min de silêncio: ainda em viagem
        r = r.send(TripSignal.Tick(NINETY_NINE), 27 * 60_000L)
        assertEquals(TripState.COMPLETED, r.ctx.state)
        assertEquals(TripEffect.CompleteTrip(null, 20 * 60_000L), r.effects.last())
    }

    @Test fun `relógio não mexe em quem está buscando ou esperando`() {
        val r = Run().send(TripSignal.WaitingPassenger(NINETY_NINE), 0).send(TripSignal.Tick(NINETY_NINE), 30 * 60_000L)
        assertEquals(TripState.WAITING_PASSENGER, r.ctx.state)
    }

    @Test fun `navegação parou antes da oferta nova - a anterior acabou e a nova começa com a oferta certa`() {
        val r = Run().send(TripSignal.OfferShown(NINETY_NINE, 1), 0)
            .send(TripSignal.Navigating(NINETY_NINE), 5_000)
            .send(TripSignal.WaitingPassenger(NINETY_NINE), 300_000)
            .send(TripSignal.Navigating(NINETY_NINE), 400_000)
            .send(TripSignal.Navigating(NINETY_NINE), 1_200_000)        // última tela da corrida 1
            .send(TripSignal.OfferShown(NINETY_NINE, 2), 1_380_000)     // 3 min de silêncio, oferta nova
            .send(TripSignal.Navigating(NINETY_NINE), 1_386_000)        // aceitou: navegação volta
        assertTrue(r.effects.contains(TripEffect.CompleteTrip(null, 1_200_000)))
        assertTrue(r.effects.contains(TripEffect.ResolveOffer(2, OfferOutcome.ACCEPTED)))
        assertTrue(r.effects.contains(TripEffect.StartTrip(NINETY_NINE, 2, TripState.TO_PICKUP, 1_386_000)))
    }
}
