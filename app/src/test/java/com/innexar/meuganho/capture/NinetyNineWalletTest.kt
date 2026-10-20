package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.ninetynine.NinetyNineParserV1
import com.innexar.meuganho.capture.ninetynine.NinetyNineWalletParser
import com.innexar.meuganho.capture.ninetynine.NinetyNineWalletParser.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Extrato da Conta99 real (29/09/2026): positivo = recebido; negativo em dinheiro = taxa da 99. */
class NinetyNineWalletTest {
    private val snap = Fixtures.ninetyNine("real_wallet")

    @Test fun `extrato reconhecido como histórico oficial da 99`() {
        assertEquals(ScreenType.EARNINGS_HISTORY, NinetyNineParserV1().classify(snap))
    }

    @Test fun `lançamentos - corridas, taxas em dinheiro e recompensas`() {
        val e = NinetyNineWalletParser.parse(snap.lines, carriedDate = null)
        assertEquals(listOf(Kind.OTHER, Kind.CASH_FEE, Kind.TRIP_CREDIT, Kind.CASH_FEE, Kind.TRIP_CREDIT, Kind.TRIP_CREDIT, Kind.OTHER), e.map { it.kind })
        assertEquals(listOf(492L, -1220L, 1940L, -776L, 819L, 779L, 880L), e.map { it.cents })
        assertEquals(LocalDate.of(2026, 9, 29), e.first().date)
        assertEquals(LocalTime.of(17, 47), e[1].time)
        assertEquals("PIX", e[2].payment)
    }

    @Test fun `registros - 3 corridas com valor e 2 em dinheiro com a taxa`() {
        val r = NinetyNineParserV1().parseEarningsHistory(snap, null)
        assertEquals(listOf(0L, 1940L, 0L, 819L, 779L), r.map { it.valueCents })
        assertEquals(listOf(1220L, null, 776L, null, null), r.map { it.cashFeeCents })
        assertEquals(true, r.all { it.timeIsEnd })
    }
}
