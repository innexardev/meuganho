package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.domain.model.Platform

/** Texto do fechamento diário, pronto para compartilhar (WhatsApp, notas). */
object DailyReportFormatter {

    fun format(dateLabel: String, s: PeriodSummary): String = buildString {
        appendLine(dateLabel)
        appendLine()
        appendLine("Trabalhado: ${Units.duration(s.workedSeconds)}")
        appendLine("Online: ${Units.duration(s.onlineSeconds)}")
        appendLine("Corridas: ${s.tripCount}")
        Platform.entries.forEach { p -> s.byPlatform[p]?.let { appendLine("${p.label}: ${it.tripCount}") } }
        appendLine()
        appendLine("Faturamento: ${Money.format(s.grossCents)}")
        if (s.pendingCount > 0) appendLine("Pendente de revisão: ${Money.format(s.pendingCents)} (${s.pendingCount})")
        appendLine("Km total: ${Units.km(s.totalMeters)}")
        appendLine("Km remunerado: ${Units.km(s.paidMeters)}")
        appendLine("Km vazio: ${Units.km(s.emptyMeters)}")
        appendLine("R$/hora: ${s.centsPerHourOnline?.let { Money.format(it) } ?: "—"}")
        appendLine("R$/km: ${s.centsPerKm?.let { Money.format(it) } ?: "—"}")
        appendLine()
        appendLine("Combustível (estimado): ${Money.format(s.fuelEstimateCents)}")
        appendLine("Custos operacionais: ${Money.format(s.operationalCostCents - s.fuelEstimateCents)}")
        if (s.expensesCents > 0) appendLine("Despesas: ${Money.format(s.expensesCents)}")
        appendLine("Resultado estimado: ${Money.format(s.operationalResultCents)}")
        append("(estimativa: não é valor contábil)")
    }
}
