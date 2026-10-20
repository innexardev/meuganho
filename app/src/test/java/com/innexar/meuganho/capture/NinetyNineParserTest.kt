package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.ninetynine.NinetyNineParserV1
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.validation.OfferAssessment
import org.junit.Assert.assertEquals
import org.junit.Test

class NinetyNineParserTest {
    private val parser = NinetyNineParserV1()

    @Test fun `oferta Pop é lida e aceita automaticamente`() {
        val snap = Fixtures.ninetyNine("offer_pop")
        assertEquals(ScreenType.OFFER, parser.classify(snap))
        val o = parser.parseOffer(snap)!!
        assertEquals(Platform.NINETY_NINE, o.platform)
        assertEquals(1590L, o.offeredCents.value)
        assertEquals(1200, o.pickupMeters.value)
        assertEquals(7800, o.tripMeters.value)
        assertEquals(17 * 60, o.tripSeconds.value)
        assertEquals(Confidence.HIGH, o.criticalConfidence)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `faixa de preço vai para revisão com o menor valor`() {
        val o = parser.parseOffer(Fixtures.ninetyNine("offer_price_range"))!!
        assertEquals(1800L, o.offeredCents.value)
        assertEquals(Confidence.LOW, o.offeredCents.confidence)
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(o, null).status)
    }

    @Test fun `valor final na mesma linha da palavra-chave`() {
        val snap = Fixtures.ninetyNine("trip_completed")
        assertEquals(ScreenType.TRIP_COMPLETED, parser.classify(snap))
        assertEquals(1640L, parser.parseCompletedValue(snap)?.value)
    }

    @Test fun `tela ociosa`() {
        assertEquals(ScreenType.IDLE, parser.classify(Fixtures.ninetyNine("idle")))
    }
}
