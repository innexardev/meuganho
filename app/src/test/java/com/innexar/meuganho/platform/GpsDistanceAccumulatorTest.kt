package com.innexar.meuganho.platform

import com.innexar.meuganho.domain.model.DriveSegment
import com.innexar.meuganho.platform.location.GpsDistanceAccumulator
import com.innexar.meuganho.platform.location.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDistanceAccumulatorTest {
    // ~0,0009° de latitude ≈ 100 m.
    private fun fix(step: Int, t: Long, accuracy: Float = 5f) = GpsFix(-23.5 + step * 0.0009, -46.6, accuracy, t)

    @Test fun `soma distância e entrega por segmento`() {
        val flushed = mutableListOf<Pair<DriveSegment, Long>>()
        val acc = GpsDistanceAccumulator(DriveSegment.EMPTY, flushEveryMeters = 1_000.0) { k, m -> flushed += k to m }
        (0..5).forEach { acc.onFix(fix(it, it * 10_000L), DriveSegment.EMPTY) }
        acc.onFix(fix(6, 60_000), DriveSegment.PAID) // troca de segmento entrega o acumulado
        acc.flush()
        assertEquals(DriveSegment.EMPTY, flushed[0].first)
        assertTrue(flushed[0].second in 490..510)
        assertEquals(DriveSegment.PAID, flushed[1].first)
        assertTrue(flushed[1].second in 95..105)
    }

    @Test fun `ignora precisão ruim, saltos e tremor parado`() {
        var total = 0L
        val acc = GpsDistanceAccumulator(DriveSegment.EMPTY) { _, m -> total += m }
        acc.onFix(fix(0, 0), DriveSegment.EMPTY)
        acc.onFix(fix(1, 10_000, accuracy = 80f), DriveSegment.EMPTY) // impreciso
        acc.onFix(fix(50, 11_000), DriveSegment.EMPTY) // 5 km em 11 s: salto
        acc.onFix(GpsFix(-23.5 + 0.00002, -46.6, 5f, 20_000), DriveSegment.EMPTY) // 2 m: tremor
        acc.flush()
        assertEquals(0L, total)
    }
}
