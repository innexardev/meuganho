package com.innexar.meuganho.capture

import com.innexar.meuganho.capture.core.SampleReason
import com.innexar.meuganho.capture.core.SamplingPolicy
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.core.SnapshotSanitizer
import com.innexar.meuganho.domain.model.Platform.NINETY_NINE
import com.innexar.meuganho.domain.model.Platform.UBER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingTest {
    @Test fun `telas comuns no máximo uma a cada 10 minutos por tipo`() {
        val p = SamplingPolicy()
        assertTrue(p.shouldSample(UBER, ScreenType.IDLE, SampleReason.SCREEN, 1, 0))
        assertFalse(p.shouldSample(UBER, ScreenType.IDLE, SampleReason.SCREEN, 2, 60_000))
        assertTrue(p.shouldSample(UBER, ScreenType.IN_TRIP, SampleReason.SCREEN, 3, 60_000))
        assertTrue(p.shouldSample(NINETY_NINE, ScreenType.IDLE, SampleReason.SCREEN, 4, 60_000))
        assertTrue(p.shouldSample(UBER, ScreenType.IDLE, SampleReason.SCREEN, 5, 600_001))
    }

    @Test fun `ofertas são amostradas com intervalo curto`() {
        val p = SamplingPolicy()
        assertTrue(p.shouldSample(UBER, ScreenType.OFFER, SampleReason.OFFER, 1, 0))
        assertFalse(p.shouldSample(UBER, ScreenType.OFFER, SampleReason.OFFER, 2, 2_000))
        assertTrue(p.shouldSample(UBER, ScreenType.OFFER, SampleReason.OFFER, 3, 3_500)) // rajadas: cada oferta vale
    }

    @Test fun `telas desconhecidas repetidas não são reenviadas`() {
        val p = SamplingPolicy()
        assertTrue(p.shouldSample(UBER, ScreenType.UNKNOWN, SampleReason.UNKNOWN, 42, 0))
        assertFalse(p.shouldSample(UBER, ScreenType.UNKNOWN, SampleReason.UNKNOWN, 42, 10 * 60_000))
        assertTrue(p.shouldSample(UBER, ScreenType.UNKNOWN, SampleReason.UNKNOWN, 43, 10 * 60_000))
    }

    @Test fun `sanitizador mantém vocabulário de interface e mascara pessoas`() {
        val vocab = setOf("aceitar")
        assertEquals("Confirmar chegada", SnapshotSanitizer.sanitizeLine("Confirmar chegada", vocab))
        assertEquals("‹texto› · 4,9 ★", SnapshotSanitizer.sanitizeLine("Maria · 4,9 ★", vocab))
        assertEquals("Buscando ‹texto›", SnapshotSanitizer.sanitizeLine("Buscando Maria Silva", vocab))
        assertEquals("UBERX", SnapshotSanitizer.sanitizeLine("UBERX", vocab))
        // Recado do passageiro (texto livre) some por inteiro.
        assertEquals(SnapshotSanitizer.FREE_TEXT_MASK,
            SnapshotSanitizer.sanitizeLine("estou na portaria do prédio azul perto da padaria", vocab))
    }
}
