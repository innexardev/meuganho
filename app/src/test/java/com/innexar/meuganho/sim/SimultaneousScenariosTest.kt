package com.innexar.meuganho.sim

import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.PlatformTripRecord
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import com.innexar.meuganho.sim.Simulator.Companion.ninetyNine
import com.innexar.meuganho.sim.Simulator.Companion.ninetyNineEarnings
import com.innexar.meuganho.sim.Simulator.Companion.uber
import com.innexar.meuganho.sim.Simulator.Companion.uberHome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/**
 * Turnos simulados com Uber e 99 AO MESMO TEMPO, telas reais, pipeline real.
 * Cada cenário reproduz uma situação que falhou na rua (29/09/2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SimultaneousScenariosTest {
    private val sim = Simulator()

    @After fun tearDown() = sim.close()

    private val s = 1_000L
    private val min = 60_000L

    @Test fun `oferta da 99 chega logo depois do aceite no Uber - as duas contas ficam certas`() {
        sim.see(uberHome(8_814))                                   // total do dia antes: R$ 88,14
            .wait(10 * s).see(uber("real_exclusive_long"))         // Uber R$ 35,93
            .wait(1 * s).see(ninetyNine("real_offer_cash"))        // 99 R$ 12,10 por cima
            .wait(3 * s).see(uber("real_nav_no_label"))            // Uber navegando = aceitou
            .wait(40 * s).see(ninetyNine("idle"))                  // 99 sumiu sem aceite
            .wait(5 * min).see(uber("real_trip_waiting"))
            .wait(2 * min).see(uber("real_nav_guest"))             // navegando de novo = embarcou
            .wait(25 * min).see(uberHome(8_814 + 3_593))           // total subiu R$ 35,93

        val offers = sim.offers()
        val uberOffer = offers.single { it.platform == Platform.UBER }
        val nnOffer = offers.single { it.platform == Platform.NINETY_NINE }
        assertEquals(OfferOutcome.ACCEPTED, uberOffer.outcome)
        assertEquals(OfferOutcome.NOT_ACCEPTED, nnOffer.outcome)

        val trip = sim.trips().single()
        assertEquals(Platform.UBER, trip.platform)
        assertEquals(TripState.COMPLETED, trip.state)
        assertEquals(uberOffer.id, trip.offerId)
        assertEquals(3_593L, trip.finalCents)
        assertEquals(ValueSource.PLATFORM_TOTAL, trip.valueSource)
        assertNotNull("embarque registrado", trip.startedAt)
    }

    @Test fun `Radar com 2 ofertas e 99 tocando junto - corrida ligada à oferta certa pelo valor oficial`() {
        sim.see(uber("real_radar_stack"))                          // R$ 5,51 e R$ 9,72 juntas
            .wait(500).see(ninetyNine("real_offer_cash"))          // 99 R$ 12,10
            .wait(2 * s).see(ninetyNine("offer_pop", ocr = false)) // 99 R$ 15,90
            .wait(4 * s).see(uber("real_nav_no_label"))            // aceitou uma do Radar (qual?)
        val live = sim.trips().single()
        assertEquals("várias no Radar: não chuta a oferta", null, live.offerId)

        sim.wait(20 * min).see(uber("real_home_online"))           // voltou à busca
        // Histórico oficial: a corrida foi a de R$ 9,72.
        val at = Instant.ofEpochMilli(live.acceptedAt).atZone(ZoneId.systemDefault())
        runBlocking {
            sim.history.import(listOf(PlatformTripRecord(Platform.UBER, at.toLocalDate(), at.toLocalTime().withSecond(0).withNano(0),
                972, "UberX", 15 * 60, 6_500)))
            sim.reconciler.run(sim.now)
        }
        val trip = sim.trips().single { it.platform == Platform.UBER }
        val radar972 = sim.offers().single { it.platform == Platform.UBER && it.offeredCents == 972L }
        val radar551 = sim.offers().single { it.platform == Platform.UBER && it.offeredCents == 551L }
        assertEquals(radar972.id, trip.offerId)
        assertEquals(OfferOutcome.ACCEPTED, radar972.outcome)
        assertTrue(radar551.outcome != OfferOutcome.ACCEPTED)
        assertEquals(972L, trip.finalCents)
        assertEquals(DataStatus.CONFIRMED, trip.status)
        assertEquals("as duas da 99 não foram aceitas", 0, sim.offers().count { it.platform == Platform.NINETY_NINE && it.outcome == OfferOutcome.ACCEPTED })
    }

    @Test fun `corrida da 99 pelo mapa enquanto ofertas do Uber não param de chegar`() {
        sim.see(ninetyNineEarnings(8_983))                         // ganho do dia 99: R$ 89,83
            .wait(1 * min).see(ninetyNine("real_offer_cash"))      // 99 R$ 12,10
            .wait(4 * s).see(ninetyNine("real_nav_map", ocr = false)) // mapa da 99 andando = aceitou
            .wait(30 * s).see(uber("real_exclusive_short"))        // Uber toca no meio da corrida da 99
            .wait(40 * s).see(uber("real_home_online"))
            .wait(2 * min).see(uber("real_exclusive_long"))
            .wait(40 * s).see(uber("real_home_online"))
            .wait(3 * min).see(ninetyNine("real_waiting"))
            .wait(2 * min).see(ninetyNine("real_nav_map", ocr = false)) // andando de novo = embarcou
            .wait(15 * min).see(ninetyNineEarnings(8_983 + 1_210))

        val trip = sim.trips().single()
        assertEquals(Platform.NINETY_NINE, trip.platform)
        assertEquals(TripState.COMPLETED, trip.state)
        assertEquals(1_210L, trip.finalCents)
        assertEquals(ValueSource.PLATFORM_TOTAL, trip.valueSource)
        val offers = sim.offers()
        assertEquals(OfferOutcome.ACCEPTED, offers.single { it.platform == Platform.NINETY_NINE }.outcome)
        assertTrue("ofertas do Uber durante a 99 não viram aceite", offers.filter { it.platform == Platform.UBER }.none { it.outcome == OfferOutcome.ACCEPTED })
        assertEquals(2, offers.count { it.platform == Platform.UBER })
    }

    @Test fun `rajada de ofertas alternadas - nenhuma se perde`() {
        // Radar com 2 cartões: o de R$ 5,51 é o mesmo do cartão isolado (não duplica); o de R$ 9,72 é novo.
        val uberFixtures = listOf("real_exclusive_long", "real_exclusive_short", "real_radar_card", "real_priority", "real_radar_stack")
        val nnFixtures = listOf("real_offer_cash", "offer_pop", "real_negocia")
        uberFixtures.forEachIndexed { i, f ->
            sim.see(uber(f)).wait(400)
            nnFixtures.getOrNull(i)?.let { sim.see(ninetyNine(it)).wait(400) }
        }
        val offers = sim.offers()
        assertEquals(listOf(3_593L, 711L, 551L, 1_177L, 972L), offers.filter { it.platform == Platform.UBER }.map { it.offeredCents })
        assertEquals("99", nnFixtures.size, offers.count { it.platform == Platform.NINETY_NINE })
    }

    @Test fun `rajada pela fila real - 40 telas em sequência, nada descartado`() {
        val screens = (0 until 40).map { i ->
            val base = if (i % 2 == 0) uber("real_exclusive_long") else ninetyNine("real_offer_cash")
            base.copy(capturedAt = Simulator.START + i * 50L)
        }
        screens.forEach(sim.coordinator::submit)
        val deadline = System.currentTimeMillis() + 10_000
        while (sim.offers().size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        Thread.sleep(300)
        assertEquals(0, sim.diagnostics.platforms.value.values.sumOf { it.queueDropped })
        assertEquals("mesma oferta repetida conta uma vez por app", 2, sim.offers().size)
    }

    @Test fun `toque de oferta da 99 - lida conta acerto, sem leitura conta perda`() {
        sim.sound99().wait(1_500).see(ninetyNine("real_offer_cash"))   // lida
            .wait(40 * s).see(ninetyNine("idle"))
            .sound99().wait(9 * s).see(ninetyNine("idle").replacing("Procurando corridas", "Procurando corridas agora")) // perdida
        val d = sim.diagnostics.platforms.value.getValue(Platform.NINETY_NINE)
        assertEquals(1, d.soundOffersRead)
        assertEquals(1, d.soundOffersMissed)
    }

    @Test fun `cancelada por engano mas com km de passageiro no GPS vira concluída para revisão`() {
        sim.see(uber("real_exclusive_short")).wait(3 * s).see(uber("real_nav_no_label"))
            .wait(2 * min).see(uber("real_home_online"))            // voltou cedo: parece cancelamento
        val cancelled = sim.trips().single()
        assertEquals(TripState.CANCELLED, cancelled.state)
        runBlocking {
            sim.db.tripDao().addPaidMeters(cancelled.id, 2_400)      // mas o GPS andou 2,4 km com passageiro
            sim.reconciler.run(sim.now)
        }
        val fixed = sim.trips().single()
        assertEquals(TripState.COMPLETED, fixed.state)
        assertEquals(DataStatus.NEEDS_REVIEW, fixed.status)
    }

    @Test fun `subida do total somando duas corridas não é jogada numa corrida só`() {
        sim.see(ninetyNineEarnings(1_000))
            .wait(1 * min).see(ninetyNine("real_offer_cash"))
            .wait(3 * s).see(ninetyNine("real_nav_map", ocr = false))
            .wait(20 * min).see(ninetyNineEarnings(1_000 + 1_210 + 2_500)) // soma 2 corridas: R$ 37,10
        val trips = sim.trips()
        val seen = trips.first()
        assertEquals(TripState.COMPLETED, seen.state)
        assertEquals("a vista fica com o valor dela", 1_210L, seen.effectiveCents)
        val unseen = trips.single { it.id != seen.id }
        assertEquals("a sobra é uma corrida não vista", 2_500L, unseen.effectiveCents)
        assertEquals(DataStatus.NEEDS_REVIEW, unseen.status)
    }

    @Test fun `extrato da Conta99 confirma o valor da corrida no cartão e a corrida em dinheiro, sem duplicar`() {
        val f = ScreenFactory(kotlin.random.Random(3))
        sim.see(ninetyNine("real_offer_cash"))                     // oferta R$ 12,10 (dinheiro)
            .wait(4 * s).see(ninetyNine("real_nav_map", ocr = false))
            .wait(20 * min).see(ninetyNine("idle"))
            .wait(2 * min).see(ninetyNine("offer_pop", ocr = false)) // oferta R$ 15,90 (cartão)
            .wait(4 * s).see(ninetyNine("real_nav_map", ocr = false).replacing("17", "25"))
            .wait(25 * min).see(ninetyNine("idle").replacing("Procurando corridas", "Procurando corridas perto"))
        val trips = sim.trips()
        assertEquals(2, trips.size)
        val cash = SimTrip(Platform.NINETY_NINE, f.randomOffer().copy(cents = 1_210, cash = true), trips[0].acceptedAt, endedAt = trips[0].endedAt!!)
        val card = SimTrip(Platform.NINETY_NINE, f.randomOffer().copy(cents = 1_590, cash = false), trips[1].acceptedAt, endedAt = trips[1].endedAt!!,
            finalCents = 1_750) // com gorjeta: o extrato traz o valor real
        val wallet = f.nnWallet(listOf(cash, card))
        sim.wait(1 * min).see(wallet).wait(10 * s).see(wallet.copy(lines = wallet.lines + com.innexar.meuganho.capture.core.ScreenLine("fim")))

        val after = sim.trips()
        assertEquals("sem duplicar ao ler o extrato 2x", 2, after.size)
        assertEquals(1_750L, after[1].effectiveCents)
        assertEquals(ValueSource.PLATFORM_HISTORY, after[1].valueSource)
        assertEquals(1_210L, after[0].effectiveCents)
        assertTrue(after[0].reasons.contains("dinheiro conferida"))
        assertEquals(DataStatus.AUTO_ACCEPTED, after[0].status)
    }

    @Test fun `dia real 30-09 - Uber navegando online e oferta velha na fila não criam corrida falsa`() {
        sim.see(uberHome(0))
            .wait(1 * min).see(uber("real_exclusive_long"))            // aceita
            .wait(14 * s).see(uber("real_online_dest_filter"))         // tela "Procurando viagens" (antes: "em viagem")
            .wait(2 * s).see(uber("real_trip_to_pickup"))
            .wait(10 * min).see(uber("real_trip_in_progress"))
            .wait(12 * min).see(uber("real_exclusive_short"))          // oferta durante a corrida: fica na fila
            .wait(2 * min).see(uberHome(3_593))                        // fim; ninguém aceitou a da fila
            .wait(60 * min).see(uber("real_radar_card"))               // 1 h depois, nova oferta
            .wait(24 * s).see(uber("real_online_nav_searching"))
            .wait(4 * s).see(uber("real_trip_to_pickup"))              // aceitou a nova
        val trips = sim.trips()
        assertEquals(2, trips.size)
        val offers = sim.offers()
        assertEquals(offers.single { it.offeredCents == 551L }.id, trips[1].offerId)
        assertTrue("a oferta velha (R$ 7,11) não foi aceita", offers.single { it.offeredCents == 711L }.outcome != OfferOutcome.ACCEPTED)
        assertEquals(3_593L, trips[0].effectiveCents)
    }

    @Test fun `corrida da 99 com navegação leve termina pelo silêncio`() {
        sim.see(ninetyNine("real_offer_cash"))
            .wait(2 * min).see(ninetyNine("real_to_pickup"))
            .wait(20 * s).see(ninetyNine("real_light_nav", ocr = false))
            .wait(4 * min).see(ninetyNine("real_waiting"))
            .wait(1 * min).see(ninetyNine("real_light_nav", ocr = false).replacing("440 m", "3,5 km"))
            .wait(12 * min).see(ninetyNine("real_light_nav", ocr = false).replacing("440 m", "80 m"))
            .wait(1 * min).see(ninetyNine("real_cash_trip_end"))
        repeat(8) { sim.wait(1 * min).tick() }
        val trip = sim.trips().single()
        assertEquals(TripState.COMPLETED, trip.state)
        assertNotNull(trip.startedAt)
        assertEquals(1_210L, trip.effectiveCents)
    }

    @Test fun `notificação do MeuGanho na imagem da 99 não vira dado da 99`() {
        sim.see(ninetyNine("real_with_own_notification"))
        assertTrue("nenhuma oferta inventada a partir da nossa notificação", sim.offers().none { it.offeredCents == 610L && it.pickupMeters == null })
    }

    /** Navegação contínua por [ms] (no app real a Acessibilidade recebe eventos o tempo todo). */
    private fun Simulator.navFor(ms: Long, stopped: Boolean = false): Simulator {
        var t = 0L
        var n = 0
        while (t < ms) {
            val base = ninetyNine(if (stopped) "real_nav_stopped" else "real_nav_mroad", ocr = false)
            see(base.copy(lines = base.lines + com.innexar.meuganho.capture.core.ScreenLine("${n++}"))).wait(20 * s)
            t += 20 * s
        }
        return this
    }

    @Test fun `dia real 01-10 - Solicitações seguidas - duas corridas da 99 não viram uma`() {
        sim.see(ninetyNine("real_offer_cash"))                                     // R$ 12,10
            .wait(5 * s).see(ninetyNine("real_toast_accepted", ocr = false))      // "Corrida aceita"
            .navFor(3 * min)
            .see(ninetyNine("real_waiting")).wait(1 * min)
            .navFor(2 * min)                                                       // embarcou
            .see(ninetyNine("offer_pop", ocr = false))                             // seguida: próxima oferta (R$ 15,90)
            .navFor(1 * min, stopped = true)                                       // parado no sinal
            .navFor(2 * min)
            .see(ninetyNine("real_rating")).wait(1 * min)                          // fim da 1ª (avaliação)
            .see(ninetyNine("real_to_pickup"))                                     // embarque da 2ª
            .navFor(2 * min)
            .see(ninetyNine("real_waiting").replacing("Iniciar corrida", "Iniciar corrida ")).wait(1 * min)
            .navFor(10 * min)
            .see(ninetyNine("real_rating").replacing("‹texto›?", "Bruno?"))
        val trips = sim.trips()
        assertEquals("duas corridas: " + trips.joinToString { "${it.state} ${it.effectiveCents}" }, 2, trips.size)
        assertTrue(trips.all { it.state == TripState.COMPLETED })
        assertEquals(listOf(1_210L, 1_590L), trips.map { it.effectiveCents })
    }

    @Test fun `ganho do dia na tela inicial da 99 confirma o valor da corrida`() {
        val home = { cents: Long -> ninetyNine("real_home_total").replacing("R$41,60", "R$${cents / 100},${"%02d".format(cents % 100)}") }
        sim.see(home(4_160)).wait(10 * s).see(home(4_160).replacing("Buscando", "Buscando "))   // 2 leituras iguais
            .wait(1 * min).see(ninetyNine("real_offer_cash"))
            .wait(5 * s).see(ninetyNine("real_toast_accepted", ocr = false))
            .wait(4 * min).see(ninetyNine("real_nav_mroad", ocr = false))
            .wait(12 * min).see(ninetyNine("real_rating"))
            .wait(1 * min).see(home(4_160 + 1_300)).wait(10 * s).see(home(4_160 + 1_300).replacing("Buscando", "Buscando "))
        val trip = sim.trips().single()
        assertEquals(1_300L, trip.effectiveCents)                                  // pagou R$ 13,00 (oferta dizia 12,10)
        assertEquals(ValueSource.PLATFORM_TOTAL, trip.valueSource)
    }
}
