package com.innexar.meuganho.capture

import com.innexar.meuganho.capture.core.TextPatterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPatternsTest {
    @Test fun `lê valores monetários brasileiros`() {
        assertEquals(listOf(2850L), TextPatterns.moneyIn("R$ 28,50").map { it.cents })
        assertEquals(listOf(123456L), TextPatterns.moneyIn("R$ 1.234,56").map { it.cents })
        assertEquals(listOf(2800L), TextPatterns.moneyIn("R$28").map { it.cents })
        assertTrue(TextPatterns.moneyIn("+R$ 5,00").single().isAddition)
    }

    @Test fun `distingue preço isolado de valor dentro de frase`() {
        assertTrue(TextPatterns.isStandalonePrice("R$ 28,50"))
        assertFalse(TextPatterns.isStandalonePrice("Tarifa R$ 22,40"))
        assertFalse(TextPatterns.isStandalonePrice("+R$ 6,50"))
    }

    @Test fun `lê distância em km, m e com ponto ou vírgula`() {
        assertEquals(1800, TextPatterns.distanceMeters("4 min (1,8 km) de distancia"))
        assertEquals(1800, TextPatterns.distanceMeters("8 min (1.8 km) away"))
        assertEquals(800, TextPatterns.distanceMeters("a 800 m"))
        // "min" não pode ser confundido com metros.
        assertEquals(null, TextPatterns.distanceMeters("15 min"))
    }

    @Test fun `lê durações em vários formatos`() {
        assertEquals(15 * 60, TextPatterns.durationSeconds("viagem de 15 min"))
        assertEquals(3900, TextPatterns.durationSeconds("1 h 5 min"))
        assertEquals(3900, TextPatterns.durationSeconds("1h05"))
        assertEquals(7200, TextPatterns.durationSeconds("2 horas"))
    }

    @Test fun `normaliza acentos e espaços`() {
        assertEquals("viagem concluida", TextPatterns.normalize("  Viagem   Concluída "))
    }

    @Test fun `detecta faixa de preço`() {
        assertTrue(TextPatterns.isPriceRange("R$ 18,00 - R$ 22,00"))
        assertFalse(TextPatterns.isPriceRange("R$ 18,00"))
    }

    @Test fun `OCR - RS com centavos vira R$, sem vírgula continua ilegível`() {
        assertEquals("R$1,93/km", TextPatterns.fixOcr("RS1,93/km"))
        assertEquals("R$134,58 >", TextPatterns.fixOcr("RS134,58 >"))
        assertEquals("RS2395", TextPatterns.fixOcr("RS2395"))
    }
}
