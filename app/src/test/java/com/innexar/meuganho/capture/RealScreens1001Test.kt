package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.ninetynine.NinetyNineParserV1
import com.innexar.meuganho.domain.model.CaptureChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Telas reais da 99 de 01/10/2026. */
class RealScreens1001Test {
    private val nn = NinetyNineParserV1()
    private fun ocr(name: String) = Fixtures.ninetyNine(name).copy(channel = CaptureChannel.OCR)

    @Test fun `tela inicial da 99 mostra o ganho do dia`() {
        assertEquals(0L, nn.dayTracker(ocr("real_home_total_zero")))
        assertEquals(4_160L, nn.dayTracker(ocr("real_home_total")))
        assertEquals(4_770L, nn.dayTracker(ocr("real_home_total_promo")))
    }

    @Test fun `cartão de oferta nunca é lido como ganho do dia`() {
        assertNull(nn.dayTracker(ocr("real_offer_cash")))
        assertNull(nn.dayTracker(Fixtures.ninetyNine("real_home_total")))   // só por imagem
    }

    @Test fun `avaliação do passageiro é fim de corrida`() {
        assertEquals(ScreenType.TRIP_COMPLETED, nn.classify(ocr("real_rating")))
    }

    @Test fun `aviso Corrida aceita é aceite`() {
        assertEquals(ScreenType.TO_PICKUP, nn.classify(Fixtures.ninetyNine("real_toast_accepted")))
    }

    @Test fun `terceiro mapa da 99 andando é navegação e parado não indica embarque`() {
        assertEquals(ScreenType.NAVIGATING, nn.classify(Fixtures.ninetyNine("real_nav_mroad")))
        assertEquals(ScreenType.NAVIGATING_STOPPED, nn.classify(Fixtures.ninetyNine("real_nav_stopped")))
    }
}
