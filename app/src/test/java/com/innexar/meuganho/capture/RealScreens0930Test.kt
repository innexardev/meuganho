package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.ninetynine.NinetyNineParserV1
import com.innexar.meuganho.capture.uber.UberParserV1
import com.innexar.meuganho.domain.model.CaptureChannel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Telas reais de 30/09/2026 que o app lia errado (ver dados do dia). */
class RealScreens0930Test {
    private val uber = UberParserV1()
    private val nn = NinetyNineParserV1()

    @Test fun `Uber online com Filtro de destino e Procurando viagens NÃO é viagem`() {
        assertEquals(ScreenType.IDLE, uber.classify(Fixtures.uber("real_online_dest_filter")))
        assertEquals(ScreenType.IDLE, uber.classify(Fixtures.uber("real_online_nav_searching")))
    }

    @Test fun `Encontro com continua sendo indo buscar`() {
        assertEquals(ScreenType.TO_PICKUP, uber.classify(Fixtures.uber("real_trip_to_pickup")))
        assertEquals(ScreenType.IN_TRIP, uber.classify(Fixtures.uber("real_trip_in_progress")))
    }

    @Test fun `total do dia do Uber sem o rótulo HOJE é lido`() {
        assertEquals(3_280L, uber.dayTracker(Fixtures.uber("real_home_tracker_nolabel")))
    }

    @Test fun `navegação leve da 99 é navegação`() {
        assertEquals(ScreenType.NAVIGATING, nn.classify(Fixtures.ninetyNine("real_light_nav")))
    }

    @Test fun `Corrida em dinheiro perto do destino é em corrida`() {
        assertEquals(ScreenType.IN_TRIP, nn.classify(Fixtures.ninetyNine("real_cash_trip_end").copy(channel = CaptureChannel.OCR)))
    }

    @Test fun `Cheguei no embarque da 99 é indo buscar`() {
        assertEquals(ScreenType.TO_PICKUP, nn.classify(Fixtures.ninetyNine("real_to_pickup").copy(channel = CaptureChannel.OCR)))
    }
}
