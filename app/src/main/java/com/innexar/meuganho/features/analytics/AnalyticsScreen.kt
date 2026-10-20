package com.innexar.meuganho.features.analytics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.EmptyState
import com.innexar.meuganho.core.designsystem.components.InfoRow
import com.innexar.meuganho.core.designsystem.components.MetricGrid
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.ui.containerViewModel

private val weekdays = listOf("", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

@Composable
fun AnalyticsScreen(onBack: (() -> Unit)?) {
    val vm = containerViewModel { AnalyticsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    MgScreen("Análises · 90 dias", onBack) {
        val s = state.summary
        if (state.loaded && (s == null || s.tripCount == 0)) {
            EmptyState("Ainda sem histórico", "As análises aparecem depois dos primeiros turnos com corridas.")
            return@MgScreen
        }
        if (s == null) return@MgScreen
        MgCard(Modifier.fillMaxWidth()) {
            MetricGrid(
                listOf(
                    Triple("Ticket médio", s.averageTicketCents?.let { Money.format(it) } ?: "—", null),
                    Triple("Tempo médio", if (s.tripCount > 0) Units.duration(s.inTripSeconds / s.tripCount) else "—", "em corrida"),
                    Triple("Km médio", if (s.tripCount > 0) Units.km(s.paidMeters / s.tripCount) else "—", "pago"),
                    Triple("Km vazio", Units.percent(s.emptyRatio), Units.km(s.emptyMeters)),
                    Triple("Ocioso", Units.percent(s.idleRatio), "do tempo online"),
                    Triple("Km pago", Units.km(s.paidMeters), null),
                ),
            )
        }
        SectionTitle("R$/hora online por horário")
        MgCard(Modifier.fillMaxWidth()) { BucketBars(state.byHour) { "${it}h" } }
        SectionTitle("R$/hora online por dia da semana")
        MgCard(Modifier.fillMaxWidth()) { BucketBars(state.byWeekday) { weekdays[it] } }

        if (state.platforms.isNotEmpty()) {
            SectionTitle("Uber x 99")
            state.platforms.forEach { p ->
                MgCard(Modifier.fillMaxWidth()) {
                    Text(p.platform.label, style = Mg.type.title, color = Mg.colors.textPrimary)
                    InfoRow("Corridas", p.trips.toString())
                    InfoRow("Faturamento", Money.format(p.grossCents))
                    InfoRow("Ticket médio", p.averageTicketCents?.let { Money.format(it) } ?: "—")
                    InfoRow("R$/km pago", p.centsPerKm?.let { Units.perKm(it) } ?: "—")
                    InfoRow("R$/h em corrida", p.centsPerHourInTrip?.let { Units.perHour(it) } ?: "—")
                }
            }
        }

        state.estimateVsReal?.takeIf { it.comparisons > 0 }?.let { e ->
            SectionTitle("Oferta x realidade (${e.comparisons} corridas)")
            MgCard(Modifier.fillMaxWidth()) {
                InfoRow("R$/h prometido", e.avgEstimatedPerHour?.let { Units.perHour(it) } ?: "—")
                InfoRow("R$/h real", e.avgRealPerHour?.let { Units.perHour(it) } ?: "—")
                InfoRow("R$/km prometido", e.avgEstimatedPerKm?.let { Units.perKm(it) } ?: "—")
                InfoRow("R$/km real", e.avgRealPerKm?.let { Units.perKm(it) } ?: "—")
                InfoRow("Minutos a mais por corrida", e.avgExtraMinutes?.toString() ?: "—")
                InfoRow("Pareciam boas e não foram", "${e.disappointedCount}", valueColor = Mg.colors.warning)
            }
        }
        Text("Resultado por região exige dados de localização por corrida, que ainda não são guardados (privacidade e economia).",
            style = Mg.type.caption, color = Mg.colors.textSecondary)
    }
}
