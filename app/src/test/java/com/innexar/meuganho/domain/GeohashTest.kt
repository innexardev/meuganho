package com.innexar.meuganho.domain

import com.innexar.meuganho.core.geo.Geohash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GeohashTest {
    @Test fun `célula conhecida`() {
        // Valor de referência clássico do geohash.
        assertEquals("u4pruy", Geohash.encode(57.64911, 10.40744))
    }

    @Test fun `pontos a poucos metros caem na mesma célula e bairros diferentes não`() {
        val a = Geohash.encode(-24.0089, -46.4128) // Praia Grande
        assertEquals(a, Geohash.encode(-24.0091, -46.4130))
        assertNotEquals(a, Geohash.encode(-23.9608, -46.3336)) // Santos
    }
}
