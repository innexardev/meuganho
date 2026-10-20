package com.innexar.meuganho.capture.ninetynine

import com.innexar.meuganho.capture.core.ScreenLine
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.PlatformTripRecord
import java.time.LocalDate
import java.time.LocalTime

/**
 * Extrato da Conta99: a fonte OFICIAL das corridas da 99. Formato real (29/09/2026), lido pela Acessibilidade:
 *
 *   29-09-2026            <- dia
 *   Pop                   <- categoria (ou 99Multiplica, Compensação aprovada…)
 *   R$19,40               <- positivo: o que o motorista recebeu (cartão/PIX)
 *   17:23                 <- horário (fim/pagamento da corrida)
 *   -                     <- (opcional) separador
 *   PIX                   <- (opcional) forma de pagamento
 *
 *   Pop / -R$12,20 / 17:47 / - / Dinheiro   <- NEGATIVO em dinheiro: é a TAXA da 99 (o motorista
 *                                              recebeu o valor em mãos); confirma a corrida, sem o valor.
 * Recompensas e compensações não são corridas.
 */
object NinetyNineWalletParser {
    enum class Kind { TRIP_CREDIT, CASH_FEE, OTHER }

    data class Entry(val date: LocalDate, val time: LocalTime, val cents: Long, val title: String, val payment: String?, val kind: Kind)

    private val dateLine = Regex("""^(\d{2})-(\d{2})-(\d{4})$""")
    private val amount = Regex("""^(-)?R\$\s?(\d{1,3}(?:\.\d{3})*),(\d{2})$""")
    private val clock = Regex("""^(\d{1,2}):(\d{2})$""")
    private val payments = setOf("dinheiro", "pix", "cartao", "cartao de credito", "cartao de debito", "99pay", "carteira")
    private val notTrips = listOf("multiplica", "compensacao", "recompensa", "bonus", "saque", "transferencia", "resgate", "ajuste", "promocao", "indicacao")

    fun parse(lines: List<ScreenLine>, carriedDate: LocalDate?): List<Entry> {
        val out = mutableListOf<Entry>()
        var date = carriedDate
        val text = lines.map { it.text.trim() }
        for (i in text.indices) {
            dateLine.matchEntire(text[i])?.let { m ->
                date = runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() ?: date
            }
            val a = amount.matchEntire(text[i]) ?: continue
            val c = text.getOrNull(i + 1)?.let(clock::matchEntire) ?: continue
            val d = date ?: continue
            val title = text.getOrNull(i - 1)?.takeIf { it.isNotBlank() && it != "-" && amount.matchEntire(it) == null } ?: continue
            val payment = if (text.getOrNull(i + 2) == "-") text.getOrNull(i + 3) else null
            val cents = (a.groupValues[2].replace(".", "").toLong() * 100 + a.groupValues[3].toLong()) * (if (a.groupValues[1] == "-") -1 else 1)
            val norm = com.innexar.meuganho.capture.core.TextPatterns.normalize(title)
            val payNorm = payment?.let(com.innexar.meuganho.capture.core.TextPatterns::normalize)
            val kind = when {
                notTrips.any { norm.contains(it) } -> Kind.OTHER
                cents < 0 && payNorm == "dinheiro" -> Kind.CASH_FEE
                cents > 0 -> Kind.TRIP_CREDIT
                else -> Kind.OTHER
            }
            val time = runCatching { LocalTime.of(c.groupValues[1].toInt(), c.groupValues[2].toInt()) }.getOrNull() ?: continue
            out += Entry(d, time, cents, title, payment?.takeIf { payNorm in payments }, kind)
        }
        return out
    }

    /** É o extrato? Uma data "dd-mm-aaaa" e pelo menos 2 lançamentos (valor + horário). */
    fun looksLikeWallet(lines: List<ScreenLine>): Boolean =
        lines.any { dateLine.matches(it.text.trim()) } &&
            lines.zipWithNext().count { (a, b) -> amount.matches(a.text.trim()) && clock.matches(b.text.trim()) } >= 2

    fun lastDate(lines: List<ScreenLine>): LocalDate? = lines.mapNotNull { l ->
        dateLine.matchEntire(l.text.trim())?.let { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
    }.lastOrNull()

    /** Lançamentos de corrida como registros oficiais (horário = fim da corrida). */
    fun records(entries: List<Entry>): List<PlatformTripRecord> = entries.mapNotNull { e ->
        when (e.kind) {
            Kind.TRIP_CREDIT -> PlatformTripRecord(Platform.NINETY_NINE, e.date, e.time, e.cents, e.title, 0, 0, timeIsEnd = true)
            Kind.CASH_FEE -> PlatformTripRecord(Platform.NINETY_NINE, e.date, e.time, 0, e.title, 0, 0, timeIsEnd = true, cashFeeCents = -e.cents)
            Kind.OTHER -> null
        }
    }
}
