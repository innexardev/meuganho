package com.innexar.meuganho.platform

import com.innexar.meuganho.platform.ocr.ScreenSignature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSignatureTest {
    private val base = IntArray(16 * 24) { 40 }

    @Test fun `mapa mexendo pouco não dispara OCR`() {
        val moved = base.copyOf().also { for (i in 0 until 10) it[i] = 200 } // ~3% dos pontos
        assertTrue(ScreenSignature.similar(base, moved))
    }

    @Test fun `cartão de oferta aparecendo dispara OCR`() {
        val card = base.copyOf().also { for (i in 120 until 384) it[i] = 230 } // metade de baixo clareou
        assertFalse(ScreenSignature.similar(base, card))
    }
}
