package com.innexar.meuganho.capture

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.uber.UberEarningsHistoryParser
import com.innexar.meuganho.capture.uber.UberParserV1
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Histórico de ganhos do Uber (prints de 28/09/2026): a fonte oficial das corridas feitas. */
class EarningsHistoryTest {
    private val snap = Fixtures.uber("real_earnings_history")

    @Test fun `tela reconhecida como histórico`() {
        assertEquals(ScreenType.EARNINGS_HISTORY, UberParserV1().classify(snap))
    }

    @Test fun `lê as corridas com valor, categoria, duração, km e horário`() {
        val trips = UberEarningsHistoryParser.parse(snap.lines, today = LocalDate.of(2026, 9, 28))
        assertEquals(listOf(551L, 551L, 801L, 1475L, 2699L, 1635L, 551L, 2805L), trips.map { it.valueCents })
        assertEquals(7, trips.count { it.date == LocalDate.of(2026, 9, 28) })
        assertEquals(LocalDate.of(2026, 9, 27), trips.last().date)
        val first = trips.first()
        assertEquals(LocalTime.of(7, 19), first.time)
        assertEquals("Uber X", first.category)
        assertEquals(4 * 60 + 23, first.durationSeconds)
        assertEquals(1360, first.meters)
        assertEquals("Bag", trips[5].category)
    }

    @Test fun `lista rolada sem cabeçalho usa o dia da leitura anterior`() {
        val noHeader = snap.lines.filterNot { it.text.startsWith("seg.") || it.text.startsWith("dom.") }
        val trips = UberEarningsHistoryParser.parse(noHeader, LocalDate.of(2026, 9, 28), carriedDate = LocalDate.of(2026, 9, 28))
        assertEquals(8, trips.size)
    }
}

/** Regressão da 0.3.3: o botão "VER HISTÓRICO DE GANHOS" da tela inicial não é a tela de histórico. */
class HistoryButtonRegressionTest {
    private val parser = UberParserV1()

    @Test fun `navegação indo buscar com o botão de histórico continua sendo indo buscar`() {
        assertEquals(ScreenType.TO_PICKUP, parser.classify(Fixtures.uber("real_nav_with_history_button")))
    }

    // ---- Telas reais de 29/09/2026 ----

    @Test fun `navegação sem frase de fase vira NAVEGANDO`() {
        assertEquals(ScreenType.NAVIGATING, parser.classify(Fixtures.uber("real_nav_no_label")))
        assertEquals(ScreenType.NAVIGATING, parser.classify(Fixtures.uber("real_nav_guest")))
    }

    @Test fun `tela inicial online não é navegação`() {
        assertEquals(ScreenType.IDLE, parser.classify(Fixtures.uber("real_home_online")))
    }

    @Test fun `coletar pagamento encerra a corrida sem usar o dinheiro como ganho`() {
        val snap = Fixtures.uber("real_collect_cash")
        assertEquals(ScreenType.TRIP_COMPLETED, parser.classify(snap))
        assertEquals(null, parser.parseCompletedValue(snap))
    }

    @Test fun `Radar vazio é tela ociosa`() {
        assertEquals(ScreenType.IDLE, parser.classify(Fixtures.uber("real_radar_empty")))
    }

    @Test fun `lista de atividade do histórico é lida, com selo entre valor e detalhes`() {
        val snap = Fixtures.uber("real_activity_feed")
        assertEquals(ScreenType.EARNINGS_HISTORY, parser.classify(snap))
        val trips = UberEarningsHistoryParser.parse(snap.lines, LocalDate.of(2026, 9, 29), carriedDate = LocalDate.of(2026, 9, 29))
        assertEquals(listOf(1289L, 1156L, 768L), trips.map { it.valueCents })
        assertEquals(listOf(LocalTime.of(8, 59), LocalTime.of(7, 32), LocalTime.of(7, 25)), trips.map { it.time })
        assertEquals(8000, trips[0].meters)
        assertEquals(18 * 60 + 17, trips[0].durationSeconds)
    }

    @Test fun `tela inicial com o botão de histórico é ociosa e mostra o total do dia`() {
        val snap = Fixtures.uber("real_home_with_tracker")
        assertEquals(ScreenType.IDLE, parser.classify(snap))
        assertEquals(8814L, parser.dayTracker(Fixtures.uber("real_nav_with_history_button")))
    }
}
