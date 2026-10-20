package com.innexar.meuganho.platform

import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.financial.OfferAnalyzer
import com.innexar.meuganho.domain.financial.RatingThresholds
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.engine.OverlayOffer
import com.innexar.meuganho.platform.notifications.OfferNotificationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferNotificationTest {
    private val t = RatingThresholds()
    private val cost = CostModel(619, 12.5, 12.0, 15.0, 0.0)

    private fun offer(cents: Long, m: Long, s: Long, reliable: Boolean = true) =
        OverlayOffer(Platform.UBER, OfferAnalyzer.analyze(cents, m, s, reliable, cost, t), DataStatus.AUTO_ACCEPTED, false, 0)

    @Test fun `exemplo do Mobai - R$ 12,81 em 4,7 km e 14 min`() {
        val text = OfferNotificationText.of(offer(1281, 4_700, 14 * 60), t, isBest = false, extra = 0)
        assertEquals("Uber · R$ 12,81 · ▲ EXCELENTE", text.title)
        assertEquals("🟢 R$ 2,73/km   🟢 R$55/h   🟢 R$ 0,92/min", text.metrics)
        assertTrue(text.details, text.details.startsWith("4,7 km · 14 min"))
    }

    @Test fun `bolinhas por métrica e incerto sem cor`() {
        val poor = OfferNotificationText.of(offer(1000, 9_000, 30 * 60), t, isBest = false, extra = 0)
        assertTrue(poor.metrics, poor.metrics.startsWith("🔴"))
        val unsure = OfferNotificationText.of(offer(1281, 4_700, 14 * 60, reliable = false), t, isBest = true, extra = 1)
        assertTrue(unsure.title, unsure.title.startsWith("★ MELHOR · Uber") && unsure.title.endsWith("? INCERTO"))
        assertTrue(unsure.metrics.startsWith("⚪"))
        assertTrue(unsure.details.endsWith("+1 outra(s) na tela"))
    }
}
