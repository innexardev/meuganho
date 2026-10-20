package com.innexar.meuganho.capture

import com.innexar.meuganho.capture.core.OfferDeduplicator
import com.innexar.meuganho.capture.core.ParserHealthMonitor
import com.innexar.meuganho.capture.core.ParserRegistry
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.capture.core.SnapshotSanitizer
import com.innexar.meuganho.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizerAndDedupTest {
    @Test fun `sanitizador remove nomes e endereços e mantém a estrutura`() {
        val snap = ScreenSnapshot.fromText(
            "com.ubercab.driver",
            """
            Buscando Maria
            Rua das Flores, 123 - Centro
            R$ 28,70
            4 min (1,8 km) de distância
            Aceitar
            """.trimIndent(),
        )
        val out = SnapshotSanitizer.sanitize(snap, ParserRegistry.sanitizerKeywords(Platform.UBER)).lines()
        assertEquals("Buscando ‹texto›", out[0])
        assertFalse(out[1].contains("Flores"))
        assertEquals("R$ 28,70", out[2])
        assertEquals("4 min (1,8 km) de distância", out[3])
        assertEquals("Aceitar", out[4])
    }

    @Test fun `nome em maiúsculas não vaza, categoria curta continua`() {
        val snap = ScreenSnapshot.fromText("com.ubercab.driver", "FULANO\nUberX VIP")
        val out = SnapshotSanitizer.sanitize(snap, ParserRegistry.sanitizerKeywords(Platform.UBER)).lines()
        assertFalse(out[0].contains("FULANO"))
        assertTrue(out[1].contains("VIP"))
    }

    @Test fun `deduplica a mesma oferta dentro da janela`() {
        val dedup = OfferDeduplicator(windowMs = 90_000)
        assertFalse(dedup.isDuplicate("UBER|2870|18|124|24", 0))
        assertTrue(dedup.isDuplicate("UBER|2870|18|124|24", 5_000))
        assertFalse(dedup.isDuplicate("UBER|2870|18|124|24", 200_000))
        assertFalse(dedup.isDuplicate("UBER|3100|18|124|24", 200_500))
    }

    @Test fun `circuit breaker abre com muitas capturas duvidosas e fecha ao normalizar`() {
        val health = ParserHealthMonitor(windowSize = 10, minSamples = 5, badThreshold = 0.3)
        repeat(3) { health.record(Platform.UBER, ok = true) }
        assertFalse(health.isTripped(Platform.UBER))
        repeat(3) { health.record(Platform.UBER, ok = false) }
        assertTrue(health.isTripped(Platform.UBER))
        assertFalse(health.isTripped(Platform.NINETY_NINE))
        repeat(10) { health.record(Platform.UBER, ok = true) }
        assertFalse(health.isTripped(Platform.UBER))
    }
}
