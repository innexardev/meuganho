package com.innexar.meuganho.capture.uber

import com.innexar.meuganho.capture.core.ScreenLine
import com.innexar.meuganho.capture.core.TextPatterns
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.PlatformTripRecord
import java.time.LocalDate
import java.time.LocalTime

/**
 * Lê a tela "Histórico de ganhos" do Uber: a fonte OFICIAL de cada corrida.
 *
 * Formato real (28/09/2026):
 *   seg., 28 de set.                          <- cabeçalho do dia
 *   R$ 5,51                                   <- valor da corrida
 *   Uber X · 4 min 23 segundos · 1.36 km      <- categoria · duração · distância
 *   7:19                                      <- horário
 *   R$ 2,50 Preço dinâmico / R$ 31,96 Dinheiro recebido   <- detalhes (ignorados)
 * Endereços são ignorados.
 */
object UberEarningsHistoryParser {
    private val dayHeader = Regex("""(\d{1,2}) de (jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)""")
    private val clock = Regex("""^(\d{1,2}):(\d{2})$""")
    private val seconds = Regex("""(\d+)\s*segundos?""")
    private val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    fun parse(lines: List<ScreenLine>, today: LocalDate, carriedDate: LocalDate? = null): List<PlatformTripRecord> {
        val out = mutableListOf<PlatformTripRecord>()
        var date = carriedDate
        lines.forEachIndexed { i, line ->
            val norm = line.normalized
            dayHeader.find(norm)?.takeIf { !norm.contains("r$") }?.let { m ->
                date = dateOf(m.groupValues[1].toInt(), months.indexOf(m.groupValues[2]) + 1, today)
                return@forEachIndexed
            }
            // Linha de detalhes: "Uber X · 4 min 23 segundos · 1.36 km", com o valor isolado logo acima.
            if (!norm.contains("·") || TextPatterns.distanceMeters(norm) == null || !norm.contains("min")) return@forEachIndexed
            // Às vezes há um selo entre o valor e os detalhes ("Preço dinâmico"): olha até 2 linhas acima.
            val price = (1..2).firstNotNullOfOrNull { k -> lines.getOrNull(i - k)?.takeIf { TextPatterns.isStandalonePrice(it.text) } }
                ?: return@forEachIndexed
            val time = lines.getOrNull(i + 1)?.let { clock.find(it.text.trim()) } ?: return@forEachIndexed
            val d = date ?: return@forEachIndexed
            val minutes = TextPatterns.durationSeconds(norm) ?: 0
            out += PlatformTripRecord(
                platform = Platform.UBER,
                date = d,
                time = LocalTime.of(time.groupValues[1].toInt(), time.groupValues[2].toInt()),
                valueCents = TextPatterns.moneyIn(price.text).first().cents,
                category = line.text.replace(Regex("[\u200E\u200F\u2066-\u2069]"), "").substringBefore("·").trim(),
                durationSeconds = minutes + (seconds.find(norm)?.groupValues?.get(1)?.toInt() ?: 0),
                meters = TextPatterns.distanceMeters(norm)!!,
            )
        }
        return out
    }

    /** Último dia visto na tela (para a próxima leitura, quando o cabeçalho sair de vista ao rolar). */
    fun lastDate(lines: List<ScreenLine>, today: LocalDate): LocalDate? = lines.mapNotNull { l ->
        dayHeader.find(l.normalized)?.takeIf { !l.normalized.contains("r$") }
            ?.let { dateOf(it.groupValues[1].toInt(), months.indexOf(it.groupValues[2]) + 1, today) }
    }.lastOrNull()

    // Sem ano na tela: é deste ano, a não ser que caia no futuro (virada de ano).
    private fun dateOf(day: Int, month: Int, today: LocalDate): LocalDate =
        LocalDate.of(today.year, month, day).let { if (it.isAfter(today)) it.minusYears(1) else it }
}
