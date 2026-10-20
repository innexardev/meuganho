package com.innexar.meuganho.platform.ocr

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Miniatura 16×24 em tons de cinza da metade de baixo da tela (onde aparece o cartão
 * de oferta). Comparar duas assinaturas custa microssegundos e evita rodar o OCR
 * (centenas de ms) quando nada mudou.
 */
object ScreenSignature {
    private const val W = 16
    private const val H = 24
    private const val PIXEL_DELTA = 24
    private const val CHANGED_FRACTION = 0.06

    fun of(bitmap: Bitmap): IntArray {
        val top = bitmap.height * 2 / 5
        val out = IntArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            val px = bitmap.getPixel(x * bitmap.width / W + bitmap.width / (2 * W), top + y * (bitmap.height - top) / H)
            out[y * W + x] = ((px shr 16 and 0xFF) * 3 + (px shr 8 and 0xFF) * 6 + (px and 0xFF)) / 10
        }
        return out
    }

    /** Menos de 6% dos pontos mudaram de brilho: a tela é a mesma (mapa se mexendo pouco não conta). */
    fun similar(a: IntArray, b: IntArray): Boolean =
        a.indices.count { abs(a[it] - b[it]) > PIXEL_DELTA } < a.size * CHANGED_FRACTION
}
