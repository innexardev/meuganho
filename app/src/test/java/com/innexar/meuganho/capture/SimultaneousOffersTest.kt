package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.core.TextPatterns
import com.innexar.meuganho.capture.uber.UberParserV1
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.financial.OfferAnalyzer
import com.innexar.meuganho.domain.financial.OfferComparator
import com.innexar.meuganho.domain.financial.RatingThresholds
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.engine.OverlayOffer
import com.innexar.meuganho.platform.overlay.OverlayFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amostras reais de 28/09/2026 com Uber e 99 tocando ao mesmo tempo. */
class SimultaneousOffersTest {
    private val parser = UberParserV1()
    private val cost = CostModel(619, 11.0, 12.0, 15.0, 0.0)
    private val thresholds = RatingThresholds()

    private fun analysis(cents: Long, meters: Long, seconds: Long, reliable: Boolean = true) =
        OfferAnalyzer.analyze(cents, meters, seconds, reliable, cost, thresholds)

    @Test fun `R$ por km sem centavos é reconhecido e não vira preço`() {
        assertEquals(100L, TextPatterns.platformRatePerKm("R$1/km aprox."))
        // O preço não veio na árvore: melhor nenhuma oferta do que "R$ 1,00".
        val offer = parser.parseOffer(Fixtures.uber("real_radar_rate_no_cents"))
        assertTrue(offer == null || offer.offeredCents.value != 100L)
    }

    @Test fun `bônus no mapa da tela Alta demanda não é oferta`() {
        assertEquals(ScreenType.IDLE, parser.classify(Fixtures.uber("real_high_demand_home")))
    }

    @Test fun `comparação escolhe pela classificação e depois pelo R$ por hora`() {
        val uber = analysis(2260, 11_000, 20 * 60)   // R$ 2,05/km · R$ 67,80/h -> EXCELENTE
        val ninety = analysis(1019, 7_000, 15 * 60)  // R$ 1,46/km -> RUIM
        assertEquals(0, OfferComparator.bestIndex(listOf(uber, ninety)))
        assertEquals(1, OfferComparator.bestIndex(listOf(ninety, uber)))
    }

    @Test fun `oferta incerta nunca é recomendada`() {
        val sure = analysis(1019, 7_000, 15 * 60)
        val unsure = analysis(9000, 10_000, 15 * 60, reliable = false)
        assertEquals(0, OfferComparator.bestIndex(listOf(sure, unsure)))
        assertNull(OfferComparator.bestIndex(listOf(unsure)))
    }

    @Test fun `painel mostra Uber e 99 juntos com a melhor em destaque`() {
        val list = listOf(
            OverlayOffer(Platform.NINETY_NINE, analysis(1019, 7_000, 15 * 60), DataStatus.AUTO_ACCEPTED, false, 0),
            OverlayOffer(Platform.UBER, analysis(2260, 11_000, 20 * 60), DataStatus.AUTO_ACCEPTED, false, 0),
        )
        val text = OverlayFormatter.offers(list)
        assertEquals("★ MELHOR: UBER · 99 x Uber", text.title)
        assertTrue(text.headline, text.headline.startsWith("R$68/h"))
        assertTrue(text.detail!!, text.detail!!.startsWith("99: R$10"))
    }
}
