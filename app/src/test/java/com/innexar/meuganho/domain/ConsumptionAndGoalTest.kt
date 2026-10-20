package com.innexar.meuganho.domain

import com.innexar.meuganho.domain.financial.ConsumptionCalculator
import com.innexar.meuganho.domain.financial.FuelFill
import com.innexar.meuganho.domain.financial.GoalCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumptionAndGoalTest {
    @Test fun `consumo tanque cheio a tanque cheio soma parciais intermediários`() {
        val fills = listOf(
            FuelFill(10_000, 40.0, fullTank = true),
            FuelFill(10_200, 15.0, fullTank = false),
            FuelFill(10_500, 25.0, fullTank = true), // 500 km com 40 L = 12,5
            FuelFill(10_860, 30.0, fullTank = true), // 360 km com 30 L = 12,0
        )
        val intervals = ConsumptionCalculator.intervals(fills)
        assertEquals(2, intervals.size)
        assertEquals(12.5, intervals[0].kmPerLiter, 0.001)
        assertEquals(12.0, intervals[1].kmPerLiter, 0.001)
        // Ponderado por km: 860 / 70.
        assertEquals(860.0 / 70.0, ConsumptionCalculator.observedKmPerLiter(fills)!!, 0.001)
    }

    @Test fun `parcial isolado não mede consumo`() {
        val fills = listOf(FuelFill(1_000, 20.0, false), FuelFill(1_300, 20.0, false))
        assertNull(ConsumptionCalculator.observedKmPerLiter(fills))
    }

    @Test fun `meta mostra faltante e tempo estimado`() {
        // Exemplo da especificação: R$276 / R$350, média R$39,80/h -> 1h52.
        val p = GoalCalculator.progress(27_600, 35_000, 3_980)
        assertEquals(0.788, p.ratio, 0.001)
        assertEquals(7_400L, p.remainingCents)
        assertEquals(6_693L, p.estimatedSecondsToGoal)
    }

    @Test fun `meta batida não fica negativa`() {
        val p = GoalCalculator.progress(40_000, 35_000, 3_000)
        assertEquals(0L, p.remainingCents)
        assertEquals(true, p.reached)
    }
}
