package com.innexar.meuganho.features.offers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.EmptyState
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.PlatformBadge
import com.innexar.meuganho.core.designsystem.components.RatingBadge
import com.innexar.meuganho.core.designsystem.components.StatusPill
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.DataStatus

@Composable
fun OffersScreen(onBack: () -> Unit) {
    val vm = containerViewModel { OffersViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    MgScreen("Ofertas recebidas", onBack) {
        if (rows.isEmpty()) {
            EmptyState("Nenhuma oferta ainda", "Com a Acessibilidade ativa, as ofertas do Uber e da 99 aparecem aqui, aceitas ou não.")
        }
        rows.forEach { OfferCard(it) }
    }
}

@Composable
private fun OfferCard(row: OfferRow) {
    val o = row.offer
    val a = row.analysis
    MgCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Mg.spacing.s)) {
            PlatformBadge(o.platform)
            Text("${DateFormats.relative(o.detectedAt)} · ${o.outcome.label}",
                style = Mg.type.caption, color = Mg.colors.textSecondary, modifier = Modifier.weight(1f))
            // "Automático" é o normal: só mostramos selo quando há algo a fazer ou a saber.
            if (o.status != DataStatus.AUTO_ACCEPTED) StatusPill(o.status)
        }
        Row(Modifier.fillMaxWidth().padding(top = Mg.spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(o.effectiveCents?.let { Money.format(it) } ?: "—", style = Mg.type.metric, color = Mg.colors.textPrimary,
                modifier = Modifier.weight(1f))
            a?.let { RatingBadge(it.rating) }
        }
        Text(
            listOfNotNull(
                a?.centsPerKm?.let { Units.perKm(it) },
                a?.centsPerHour?.let { Units.perHour(it) },
                o.totalMeters?.let { Units.km(it) },
                o.totalSeconds?.let { Units.duration(it) },
                o.category,
            ).joinToString(" · "),
            style = Mg.type.caption, color = Mg.colors.textSecondary,
        )
        if (o.status != DataStatus.AUTO_ACCEPTED && o.reasons.isNotBlank()) {
            Text(o.reasons, style = Mg.type.caption, color = Mg.colors.warning, modifier = Modifier.padding(top = Mg.spacing.xs))
        }
    }
}
