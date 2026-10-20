package com.innexar.meuganho.domain

import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun `formata no padrão brasileiro`() {
        assertEquals("R$ 28,70", Money.format(2870))
        assertEquals("R$ 1.234,56", Money.format(123456))
        assertEquals("-R$ 0,05", Money.format(-5))
        assertEquals("R$184", Money.formatShort(18430))
    }

    @Test fun `interpreta entrada do usuário`() {
        assertEquals(2850L, Money.parseInput("28,50"))
        assertEquals(2850L, Money.parseInput("28.50"))
        assertEquals(123456L, Money.parseInput("1.234,56"))
        assertEquals(2800L, Money.parseInput("R$ 28"))
        assertNull(Money.parseInput("abc"))
        assertNull(Money.parseInput(""))
    }

    @Test fun `formata distância e tempo`() {
        assertEquals("14,2 km", Units.km(14_200))
        assertEquals("28 min", Units.duration(28 * 60))
        assertEquals("1h52", Units.duration(6693))
        assertEquals("9h42", Units.duration(9 * 3600 + 42 * 60))
    }
}
