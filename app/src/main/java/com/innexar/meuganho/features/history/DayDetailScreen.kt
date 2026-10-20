package com.innexar.meuganho.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.EmptyState
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.MoneyText
import com.innexar.meuganho.core.designsystem.components.PlatformBadge
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.components.StatusPill
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.core.ui.shareText
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.mapper.toComparison
import com.innexar.meuganho.domain.stats.DailyReportFormatter

@Composable
fun DayDetailScreen(epochDay: Long, onBack: () -> Unit) {
    val vm = containerViewModel(key = "day-$epochDay") { DayDetailViewModel(it, epochDay) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val day = state.day
    val title = day?.date?.let { DateFormats.day(it) } ?: "Dia"

    MgScreen(title, onBack, actions = {
        if (day != null) {
            IconButton(onClick = { context.shareText(DailyReportFormatter.format(title, day.summary), "Compartilhar fechamento") }) {
                Icon(Icons.Filled.Share, contentDescription = "Compartilhar fechamento")
            }
        }
    }) {
        if (day == null) return@MgScreen
        SummaryCard(day.summary)
        val odometer = day.summary.odometerMeters
        if (odometer != null && day.summary.gpsMeters > 0) {
            MgCard(Modifier.fillMaxWidth()) {
                Text("Odômetro ${Units.km(odometer)} · GPS ${Units.km(day.summary.gpsMeters)}", style = Mg.type.body, color = Mg.colors.textPrimary)
                Text("Diferença grande indica GPS desligado em parte do turno ou odômetro digitado errado.",
                    style = Mg.type.caption, color = Mg.colors.textSecondary)
            }
        }
        day.reconciliation.filter { it.platformCents != null }.forEach { r ->
            val ok = r.reconciled == true
            MgCard(Modifier.fillMaxWidth(), accent = if (ok) null else Mg.colors.warning) {
                Text("${r.platform.label}: ${if (ok) "✓ bate com o app" else "⚠ não bate com o app"}",
                    style = Mg.type.body, color = if (ok) Mg.colors.positive else Mg.colors.warning)
                Text("Capturado ${Money.format(r.capturedCents)} · app ${Money.format(r.platformCents!!)}",
                    style = Mg.type.caption, color = Mg.colors.textSecondary)
            }
        }
        SectionTitle("Corridas (${day.trips.size})")
        if (day.trips.isEmpty()) EmptyState("Sem corridas", "Nenhuma corrida registrada neste dia.")
        day.trips.forEach { TripRow(it) }
    }
}

@Composable
private fun TripRow(t: TripEntity) {
    val comparison = t.toComparison()
    MgCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Mg.spacing.s)) {
            PlatformBadge(t.platform)
            Text("${DateFormats.time(t.acceptedAt)} · ${t.state.label}", style = Mg.type.caption,
                color = Mg.colors.textSecondary, modifier = Modifier.weight(1f))
            if (t.status != com.innexar.meuganho.domain.model.DataStatus.AUTO_ACCEPTED) StatusPill(t.status)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.valueSource.label, style = Mg.type.caption, color = Mg.colors.textSecondary, modifier = Modifier.weight(1f))
            t.effectiveCents?.let { MoneyText(it, style = Mg.type.metricSmall) } ?: Text("—", color = Mg.colors.textSecondary)
        }
        if (comparison != null) {
            val est = comparison.estimatedPerHour?.let { Units.perHour(it) } ?: "—"
            val real = comparison.realPerHour?.let { Units.perHour(it) } ?: "—"
            Text("Oferta prometia $est · real $real" + if (comparison.disappointed) "  ▼ pior que parecia" else "",
                style = Mg.type.caption, color = if (comparison.disappointed) Mg.colors.warning else Mg.colors.textSecondary)
        }
    }
}
