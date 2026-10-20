package com.innexar.meuganho.features.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.innexar.meuganho.core.designsystem.components.InfoRow
import com.innexar.meuganho.core.designsystem.components.MetricGrid
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MoneyText
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.domain.stats.PeriodSummary

/** Resumo financeiro completo de um período (usado no Histórico e no Detalhe do dia). */
@Composable
fun SummaryCard(s: PeriodSummary) {
    MgCard(Modifier.fillMaxWidth()) {
        Text("FATURAMENTO", style = Mg.type.label, color = Mg.colors.textSecondary)
        MoneyText(s.grossCents, style = Mg.type.hero)
        if (s.pendingCount > 0) {
            Text("? +${Money.format(s.pendingCents)} a confirmar (${s.pendingCount})", style = Mg.type.caption, color = Mg.colors.warning)
        }
        MetricGrid(
            listOf(
                Triple("R$/hora", s.centsPerHourOnline?.let { Units.perHourValue(it) } ?: "—", "online"),
                Triple("R$/km", s.centsPerKm?.let { Units.perKmValue(it) } ?: "—", "rodado"),
                Triple("Corridas", s.tripCount.toString(), null),
                Triple("Km", Units.km(s.totalMeters).removeSuffix(" km"), "${Units.km(s.emptyMeters)} vazio"),
                Triple("Online", Units.duration(s.onlineSeconds), "${Units.duration(s.idleSeconds)} ocioso"),
                Triple("R$/h corrida", s.centsPerHourInTrip?.let { Units.perHourValue(it) } ?: "—", Units.duration(s.inTripSeconds)),
            ),
            modifier = Modifier.padding(top = Mg.spacing.l),
        )
        HorizontalDivider(Modifier.padding(vertical = Mg.spacing.m), color = Mg.colors.outline)
        s.byPlatform.forEach { (p, slice) -> InfoRow("${p.label} · ${Units.count(slice.tripCount, "corrida")}", Money.format(slice.grossCents)) }
        InfoRow("Combustível estimado", "-${Money.format(s.fuelEstimateCents)}")
        InfoRow("Manutenção/depreciação", "-${Money.format(s.operationalCostCents - s.fuelEstimateCents)}")
        if (s.expensesCents > 0) InfoRow("Despesas", "-${Money.format(s.expensesCents)}")
        InfoRow("Resultado estimado", Money.format(s.operationalResultCents),
            valueColor = if (s.operationalResultCents >= 0) Mg.colors.positive else Mg.colors.negative)
        s.netCentsPerHourOnline?.let { InfoRow("Lucro por hora online", Units.perHourValue(it)) }
        s.netCentsPerKm?.let { InfoRow("Lucro por km rodado", Units.perKmValue(it)) }
        s.costCentsPerKm?.let { InfoRow("Custo do carro por km", Units.perKmValue(it)) }
        s.averageTicketCents?.let { InfoRow("Média por corrida", Money.format(it)) }
        HorizontalDivider(Modifier.padding(vertical = Mg.spacing.m), color = Mg.colors.outline)
        InfoRow("Combustível comprado", Money.format(s.fuelPurchasedCents))
        InfoRow("Caixa (bruto − compras − despesas)", Money.format(s.cashCents))
        Text("Resultado é estimativa pelo custo/km configurado, não valor contábil.",
            style = Mg.type.caption, color = Mg.colors.textSecondary, modifier = Modifier.padding(top = Mg.spacing.s))
    }
}
