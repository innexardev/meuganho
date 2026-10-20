package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.uber.UberParserV1
import com.innexar.meuganho.domain.financial.Rates
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.validation.OfferAssessment
import org.junit.Assert.assertEquals
import org.junit.Test

/** Telas REAIS do Uber (prints de 28/09/2026). Estas são as referências da calibração. */
class UberRealScreensTest {
    private val parser = UberParserV1()

    private fun check(fixture: String, cents: Long, pickupM: Int, tripM: Int, pickupS: Int, tripS: Int, shownPerKm: Long) {
        val snap = Fixtures.uber(fixture)
        assertEquals(fixture, ScreenType.OFFER, parser.classify(snap))
        val o = parser.parseOffer(snap)!!
        assertEquals(cents, o.offeredCents.value)
        assertEquals(pickupM, o.pickupMeters.value)
        assertEquals(tripM, o.tripMeters.value)
        assertEquals(pickupS, o.pickupSeconds.value)
        assertEquals(tripS, o.tripSeconds.value)
        // Nosso R$/km igual ao exibido pelo Uber (mesmo arredondamento).
        assertEquals(shownPerKm, Rates.centsPerKm(cents, o.totalMeters!!.toLong()))
        assertEquals(fixture, DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `oferta exclusiva longa`() = check("real_exclusive_long", 3593, 2800, 20_000, 420, 2580, 158)

    @Test fun `oferta exclusiva curta`() = check("real_exclusive_short", 711, 1800, 2500, 360, 540, 165)

    @Test fun `cartão do Radar de Viagens com botão Selecionar`() = check("real_radar_card", 551, 2500, 700, 480, 180, 172)

    @Test fun `Radar com dois cartões vira duas ofertas`() {
        val offers = parser.parseOffers(Fixtures.uber("real_radar_stack"))
        assertEquals(listOf(551L, 972L), offers.map { it.offeredCents.value })
        assertEquals(listOf(3200, 6500), offers.map { it.totalMeters })
        offers.forEach { assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(it, null).status) }
    }

    @Test fun `Radar sem estrutura de cartões é separado pelo texto`() {
        val text = Fixtures.uber("real_radar_stack").lines.joinToString("\n") { it.text }
        val offers = parser.parseOffers(ScreenSnapshot.fromText("com.ubercab.driver", text))
        assertEquals(listOf(551L, 972L), offers.map { it.offeredCents.value })
        offers.forEach { assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(it, null).status) }
    }

    @Test fun `R$ por km diferente do app manda para revisão`() {
        val text = Fixtures.uber("real_exclusive_short").lines.joinToString("\n") { it.text }.replace("R$1,65/km", "R$2,40/km")
        val o = parser.parseOffer(ScreenSnapshot.fromText("com.ubercab.driver", text))!!
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(o, null).status)
    }
}

/** Amostras reais recebidas pelo servidor de calibração (28/09/2026). */
class UberCalibrationSamplesTest {
    private val parser = UberParserV1()

    @Test fun `duração com "1 h e 25 min" é lida inteira`() {
        val o = parser.parseOffer(Fixtures.uber("real_long_trip_hours"))!!
        assertEquals(16_481L, o.offeredCents.value)
        assertEquals(72_500, o.tripMeters.value)
        assertEquals(85 * 60, o.tripSeconds.value)
        // Sem "R$/km aprox." na tela, mas com exatamente dois pares: confiável.
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `Priority com embarque prioritário é aceita pela prova do R$ por km`() {
        val o = parser.parseOffer(Fixtures.uber("real_priority"))!!
        assertEquals(1177L, o.offeredCents.value)
        assertEquals(192L, o.surgeCents)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `tela inicial do Uber é ociosa`() {
        assertEquals(ScreenType.IDLE, parser.classify(Fixtures.uber("real_home_screen")))
    }
}

/** Ciclo da corrida com telas reais do Uber (28/09/2026): a falha fazia corridas virarem "canceladas". */
class UberTripLifecycleTest {
    private val parser = UberParserV1()

    @Test fun `telas reais do ciclo da corrida`() {
        assertEquals(ScreenType.TO_PICKUP, parser.classify(Fixtures.uber("real_trip_to_pickup")))
        assertEquals(ScreenType.WAITING_PASSENGER, parser.classify(Fixtures.uber("real_trip_waiting")))
        assertEquals(ScreenType.IN_TRIP, parser.classify(Fixtures.uber("real_trip_in_progress")))
        assertEquals(ScreenType.IDLE, parser.classify(Fixtures.uber("real_home_online")))
    }

    @Test fun `sequência real termina em corrida concluída, não cancelada`() {
        var ctx = com.innexar.meuganho.domain.trip.TripContext()
        val effects = mutableListOf<com.innexar.meuganho.domain.trip.TripEffect>()
        val platform = com.innexar.meuganho.domain.model.Platform.UBER
        listOf("real_trip_to_pickup", "real_trip_waiting", "real_trip_in_progress", "real_home_online").forEachIndexed { i, f ->
            val signal = when (parser.classify(Fixtures.uber(f))) {
                ScreenType.TO_PICKUP -> com.innexar.meuganho.domain.trip.TripSignal.HeadingToPickup(platform)
                ScreenType.WAITING_PASSENGER -> com.innexar.meuganho.domain.trip.TripSignal.WaitingPassenger(platform)
                ScreenType.IN_TRIP -> com.innexar.meuganho.domain.trip.TripSignal.InTrip(platform)
                else -> com.innexar.meuganho.domain.trip.TripSignal.Idle(platform)
            }
            val t = com.innexar.meuganho.domain.trip.TripStateMachine.reduce(ctx, signal, 1_000L + i * 300_000L)
            ctx = t.context
            effects += t.effects
        }
        assertEquals(com.innexar.meuganho.domain.model.TripState.COMPLETED, ctx.state)
        assertEquals(false, effects.any { it is com.innexar.meuganho.domain.trip.TripEffect.CancelTrip })
    }
}
