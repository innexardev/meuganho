package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.ninetynine.NinetyNineParserV1
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.validation.OfferAssessment
import org.junit.Assert.assertEquals
import org.junit.Test

/** Telas REAIS da 99 (prints de 28/09/2026). */
class NinetyNineRealScreensTest {
    private val parser = NinetyNineParserV1()

    @Test fun `oferta em dinheiro sem botão visível é reconhecida e confirmada pelo R$ por km`() {
        val snap = Fixtures.ninetyNine("real_offer_cash")
        assertEquals(ScreenType.OFFER, parser.classify(snap))
        val o = parser.parseOffer(snap)!!
        assertEquals(1210L, o.offeredCents.value)
        assertEquals(2600, o.pickupMeters.value)
        assertEquals(7100, o.tripMeters.value)
        assertEquals(21 * 60, o.totalSeconds)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `Negocia usa o valor do botão Aceitar por e ignora as contrapropostas`() {
        val snap = Fixtures.ninetyNine("real_negocia")
        assertEquals(ScreenType.OFFER, parser.classify(snap))
        val offers = parser.parseOffers(snap)
        assertEquals(1, offers.size)
        assertEquals(1307L, offers[0].offeredCents.value)
        assertEquals(12_100, offers[0].totalMeters)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(offers[0], null).status)
    }

    @Test fun `leitura por OCR começa em MEDIUM e a prova do R$ por km sobe o preço`() {
        val snap = Fixtures.ninetyNine("real_offer_cash").copy(channel = CaptureChannel.OCR)
        val o = parser.parseOffer(snap)!!
        assertEquals(Confidence.HIGH, o.offeredCents.confidence) // confirmado pelo R$1,25/km
        assertEquals(Confidence.MEDIUM, o.tripMeters.confidence)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }

    @Test fun `OCR de Negocia sem prova fica em MEDIUM e ainda é aceito`() {
        val snap = ScreenSnapshot.fromText("com.app99.driver", Fixtures.ninetyNine("real_negocia").lines.joinToString("\n") { it.text })
            .copy(channel = CaptureChannel.OCR)
        val o = parser.parseOffer(snap)!!
        assertEquals(Confidence.MEDIUM, o.criticalConfidence)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(o, null).status)
    }
}

/** Corrida da 99 (telas reais de 29/09/2026). */
class NinetyNineTripScreensTest {
    private val parser = NinetyNineParserV1()

    @Test fun `mapa da 99 com o carro andando é navegação (Acessibilidade)`() {
        assertEquals(ScreenType.NAVIGATING, parser.classify(Fixtures.ninetyNine("real_nav_map")))
    }

    @Test fun `mapa da 99 parado não é navegação`() {
        val stopped = Fixtures.ninetyNine("real_nav_map").let { s ->
            s.copy(lines = s.lines.map { if (it.viewId?.contains("current_speed_tv") == true) it.copy(text = "0") else it })
        }
        assertEquals(false, parser.classify(stopped) == ScreenType.NAVIGATING)
    }

    @Test fun `barra recolhida com chegada prevista é navegação (OCR)`() {
        assertEquals(ScreenType.NAVIGATING, parser.classify(Fixtures.ninetyNine("real_nav_bar")))
    }

    @Test fun `aguardando passageiro continua aguardando`() {
        assertEquals(ScreenType.WAITING_PASSENGER, parser.classify(Fixtures.ninetyNine("real_waiting")))
    }

    @Test fun `central de ganhos lê o ganho do dia`() {
        val snap = Fixtures.ninetyNine("real_daily_earnings")
        assertEquals(ScreenType.DAILY_EARNINGS, parser.classify(snap))
        assertEquals(13458L, parser.parseDailyTotal(snap)?.value)
    }
}
