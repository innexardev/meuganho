package com.innexar.meuganho.domain

import com.innexar.meuganho.domain.trip.EarningsDeltaSplitter
import com.innexar.meuganho.domain.trip.EarningsDeltaSplitter.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class EarningsDeltaSplitterTest {
    @Test fun `uma corrida recebe o valor oficial, mesmo com gorjeta`() {
        assertEquals(Decision.Single(0), EarningsDeltaSplitter.decide(2_050, listOf(1_800)))
    }

    @Test fun `duas corridas e a soma bate - conferidas, sem corrida inventada`() {
        assertEquals(Decision.ConfirmSum(null), EarningsDeltaSplitter.decide(3_707 + 1_987, listOf(3_707, 1_987)))
    }

    @Test fun `uma vista e outra não - sobra vira corrida para revisão`() {
        assertEquals(Decision.ConfirmSum(2_500), EarningsDeltaSplitter.decide(1_210 + 2_500, listOf(1_210)))
    }

    @Test fun `subida menor que o estimado vai para revisão`() {
        assertEquals(Decision.Mismatch, EarningsDeltaSplitter.decide(2_000, listOf(1_500, 1_500)))
    }

    @Test fun `nenhuma corrida vista`() {
        assertEquals(Decision.Unseen, EarningsDeltaSplitter.decide(1_500, emptyList()))
    }
}
