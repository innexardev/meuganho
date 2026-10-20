package com.innexar.meuganho.features.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.EmptyState
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.PlatformBadge
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.Platform

@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val vm = containerViewModel { ReviewViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    MgScreen("Revisar", onBack) {
        Text("Dados que o app não teve certeza de ter lido corretamente. Nada aqui entra nas estatísticas até você confirmar.",
            style = Mg.type.caption, color = Mg.colors.textSecondary)
        if (state.offers.isEmpty() && state.trips.isEmpty()) {
            EmptyState("Tudo revisado", "Nenhum dado pendente de confirmação.")
        }
        if (state.trips.isNotEmpty()) SectionTitle("Corridas (${state.trips.size})")
        state.trips.forEach { t ->
            ReviewCard(
                key = "trip-${t.id}",
                platform = t.platform,
                at = t.acceptedAt,
                value = t.effectiveCents,
                details = listOfNotNull(
                    t.estimatedCents?.let { "Oferta: ${Money.format(it)}" },
                    t.finalCents?.let { "Tela final: ${Money.format(it)}" },
                    "Origem: ${t.valueSource.label}",
                    t.paidMeters.takeIf { it > 0 }?.let { Units.km(it) },
                ).joinToString(" · "),
                reasons = t.reasons,
                snapshot = null,
                requireValue = t.effectiveCents == null,
                onConfirm = { vm.confirmTrip(t, it) },
                onDiscard = { vm.discardTrip(t) },
            )
        }
        if (state.offers.isNotEmpty()) SectionTitle("Ofertas (${state.offers.size})")
        state.offers.forEach { o ->
            ReviewCard(
                key = "offer-${o.id}",
                platform = o.platform,
                at = o.detectedAt,
                value = o.effectiveCents,
                details = listOfNotNull(
                    o.pickupMeters?.let { "Busca ${Units.km(it)}" } ?: "Busca ?",
                    o.tripMeters?.let { "Viagem ${Units.km(it)}" } ?: "Viagem ?",
                    o.totalSeconds?.let { Units.duration(it) },
                    "leitura ${o.confidence.label}",
                ).joinToString(" · "),
                reasons = o.reasons,
                snapshot = o.rawSnapshot,
                requireValue = o.effectiveCents == null,
                onConfirm = { vm.confirmOffer(o, it) },
                onDiscard = { vm.discardOffer(o) },
            )
        }
    }
}

@Composable
private fun ReviewCard(
    key: String,
    platform: Platform,
    at: Long,
    value: Long?,
    details: String,
    reasons: String,
    snapshot: String?,
    requireValue: Boolean,
    onConfirm: (Long?) -> Unit,
    onDiscard: () -> Unit,
) {
    var editing by rememberSaveable(key) { mutableStateOf(requireValue) }
    var input by rememberSaveable(key + "v") { mutableStateOf(value?.let { Money.toInput(it) }.orEmpty()) }
    var showSnapshot by rememberSaveable(key + "s") { mutableStateOf(false) }

    MgCard(Modifier.fillMaxWidth(), accent = Mg.colors.warning) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Mg.spacing.s)) {
            PlatformBadge(platform)
            Text(DateFormats.relative(at), style = Mg.type.caption, color = Mg.colors.textSecondary)
        }
        Text(value?.let { "? ${Money.format(it)}" } ?: "? sem valor", style = Mg.type.metric, color = Mg.colors.warning,
            modifier = Modifier.padding(top = Mg.spacing.s))
        Text(details, style = Mg.type.caption, color = Mg.colors.textSecondary)
        if (reasons.isNotBlank()) Text(reasons, style = Mg.type.caption, color = Mg.colors.warning)
        if (snapshot != null) {
            TextButton(onClick = { showSnapshot = !showSnapshot }) { Text(if (showSnapshot) "Ocultar tela capturada" else "Ver tela capturada") }
            if (showSnapshot) Text(snapshot, style = Mg.type.caption.copy(fontFamily = FontFamily.Monospace), color = Mg.colors.textSecondary)
        }
        if (editing) MgField("Valor correto", input, { input = it }, suffix = "R$")
        Button(
            onClick = {
                val corrected = if (editing) Money.parseInput(input) else null
                if (!editing || corrected != null) onConfirm(corrected?.takeIf { it != value })
            },
            modifier = Modifier.fillMaxWidth().padding(top = Mg.spacing.s).height(Mg.spacing.touch),
        ) { Text(if (editing) "Salvar valor" else "✓ Está certo") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Mg.spacing.s)) {
            if (!editing) OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("Corrigir valor", maxLines = 1) }
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Descartar", maxLines = 1) }
        }
    }
}
