package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.uber.UberParserV1
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.validation.OfferAssessment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regressão do parser Uber: cada fixture representa um layout real (anonimizado).
 * Se o Uber mudar a interface, o teste da fixture nova diz exatamente o que quebrou.
 */
class UberParserTest {
    private val parser = UberParserV1()

    private fun offer(name: String) = requireNotNull(parser.parseOffer(Fixtures.uber(name))) { "sem oferta em $name" }

    @Test fun `oferta padrão é lida com alta confiança e aceita automaticamente`() {
        val snap = Fixtures.uber("offer_standard")
        assertEquals(ScreenType.OFFER, parser.classify(snap))
        val o = offer("offer_standard")
        assertEquals(2870L, o.offeredCents.value)
        assertEquals(1800, o.pickupMeters.value)
        assertEquals(240, o.pickupSeconds.value)
        assertEquals(12_400, o.tripMeters.value)
        assertEquals(1440, o.tripSeconds.value)
        assertEquals("UberX", o.category)
        assertEquals(Confidence.HIGH, o.criticalConfidence)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `banner de promoção não é confundido com o preço`() {
        val o = offer("offer_promo_banner")
        assertEquals(1840L, o.offeredCents.value)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `dinâmica é separada do preço`() {
        val o = offer("offer_surge")
        assertEquals(3210L, o.offeredCents.value)
        assertEquals(650L, o.surgeCents)
    }

    @Test fun `ids de view são usados como âncora estrutural`() {
        val o = offer("offer_view_ids")
        assertEquals(4100L, o.offeredCents.value)
        assertEquals(900, o.pickupMeters.value)
        assertEquals(21_300, o.tripMeters.value)
        assertEquals(Confidence.HIGH, o.criticalConfidence)
    }

    @Test fun `dois preços na tela vão para revisão em vez de chutar`() {
        val o = offer("offer_two_prices")
        assertEquals(Confidence.LOW, o.offeredCents.confidence)
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(o, null).status)
    }

    @Test fun `números soltos sem âncora vão para revisão`() {
        val o = offer("offer_split_lines")
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(o, null).status)
    }

    @Test fun `vírgula perdida (R$ 2850) é pega pela plausibilidade`() {
        val o = offer("offer_misread_decimal")
        assertEquals(285_000L, o.offeredCents.value)
        val decision = OfferAssessment.assess(o, null)
        assertEquals(DataStatus.NEEDS_REVIEW, decision.status)
    }

    @Test fun `classifica telas do ciclo da corrida`() {
        mapOf(
            "idle" to ScreenType.IDLE,
            "to_pickup" to ScreenType.TO_PICKUP,
            "waiting" to ScreenType.WAITING_PASSENGER,
            "in_trip" to ScreenType.IN_TRIP,
            "trip_completed" to ScreenType.TRIP_COMPLETED,
            "cancelled" to ScreenType.TRIP_CANCELLED,
            "daily_earnings" to ScreenType.DAILY_EARNINGS,
        ).forEach { (fixture, expected) -> assertEquals(fixture, expected, parser.classify(Fixtures.uber(fixture))) }
    }

    @Test fun `lê valor final e total do dia`() {
        val completed = parser.parseCompletedValue(Fixtures.uber("trip_completed"))
        assertNotNull(completed)
        assertEquals(2690L, completed!!.value)
        assertEquals(Confidence.HIGH, completed.confidence)
        assertEquals(18_430L, parser.parseDailyTotal(Fixtures.uber("daily_earnings"))?.value)
    }
}
