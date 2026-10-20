package com.innexar.meuganho.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.app.navigation.Routes
import com.innexar.meuganho.core.designsystem.components.GoalRing
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MoneyText
import com.innexar.meuganho.core.designsystem.components.RateGauge
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.components.platformColor
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.data.repository.DayOverview
import com.innexar.meuganho.domain.stats.PeriodSummary
import java.time.LocalDate

/**
 * Tela Hoje em forma de cockpit: mostrador de R$/hora (o número que decide se vale estar
 * na rua), faturamento, e um grid "bento" com meta, R$/km e resultado. Detalhes ficam no Dia.
 */
@Composable
fun DashboardScreen(navigate: (String) -> Unit) {
    val vm = containerViewModel { DashboardViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val day = state.day
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Mg.spacing.l),
        verticalArrangement = Arrangement.spacedBy(Mg.spacing.m),
    ) {
        Header(state, onTogglePause = vm::togglePause)
        com.innexar.meuganho.features.update.UpdateCard()
        if (day == null) return@Column
        val s = day.summary
        val openDay = { navigate(Routes.day(day.date.toEpochDay())) }

        MgCard(Modifier.fillMaxWidth(), onClick = openDay, glow = true) {
            RateGauge(
                centsPerHour = s.centsPerHourOnline,
                acceptableCentsPerHour = state.thresholds.acceptableCentsPerHour,
                excellentCentsPerHour = state.thresholds.excellentCentsPerHour,
                valueText = s.centsPerHourOnline?.let { Units.perHourValue(it) } ?: "R$ —",
                caption = "R$ por hora online",
            )
            Row(Modifier.fillMaxWidth().padding(top = Mg.spacing.m), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("FATURAMENTO HOJE", style = Mg.type.label, color = Mg.colors.textSecondary)
                    MoneyText(s.grossCents, style = Mg.type.metric.copy(fontSize = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp)))
                }
                if (s.pendingCount > 0) {
                    Button(
                        onClick = { navigate(Routes.REVIEW) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mg.colors.warning),
                    ) { Text("? +${Money.formatShort(s.pendingCents)}") }
                }
            }
        }

        // Bento: meta (anel) à esquerda; R$/km e resultado empilhados à direita.
        Row(Modifier.fillMaxWidth().height(IntrinsicSizeHeight), horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
            MgCard(Modifier.weight(1f).fillMaxHeight(), onClick = { navigate(Routes.GOALS) }) {
                Text("META", style = Mg.type.label, color = Mg.colors.textSecondary)
                Box(Modifier.fillMaxWidth().padding(vertical = Mg.spacing.s), contentAlignment = Alignment.Center) {
                    GoalRing(day.goalProgress?.ratio)
                }
                Text(
                    day.goalProgress?.let { if (it.reached) "Meta batida" else "faltam ${Money.formatShort(it.remainingCents)}" }
                        ?: "Toque para definir",
                    style = Mg.type.caption, color = if (day.goalProgress == null) Mg.colors.brand else Mg.colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
                Tile("R$/km", s.centsPerKm?.let { Units.perKmValue(it) } ?: "—", "rodado", onClick = openDay)
                Tile("Resultado", Money.formatShort(s.operationalResultCents), "após custos do carro",
                    valueColor = if (s.operationalResultCents >= 0) Mg.colors.positive else Mg.colors.negative, onClick = openDay)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
            Tile("Corridas", s.tripCount.toString(), s.averageTicketCents?.let { "média ${Money.formatShort(it)}" }, Modifier.weight(1f), openDay)
            Tile("Km", Units.km(s.totalMeters).removeSuffix(" km"), "${Units.km(s.paidMeters)} pagos", Modifier.weight(1f), openDay)
            Tile("Horas", Units.duration(s.onlineSeconds), "online", Modifier.weight(1f), openDay)
        }

        Alerts(state, navigate)
        PlatformSplit(s)
        ReconciliationCard(day)
        Spacer(Modifier.height(Mg.spacing.l))
    }
}

private val IntrinsicSizeHeight = 196.dp

@Composable
private fun Tile(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
    valueColor: androidx.compose.ui.graphics.Color = Mg.colors.textPrimary,
) {
    MgCard(modifier, onClick = onClick) {
        Text(label.uppercase(), style = Mg.type.label, color = Mg.colors.textSecondary, maxLines = 1)
        Text(value, style = Mg.type.metric, color = valueColor, maxLines = 1)
        if (sub != null) Text(sub, style = Mg.type.caption, color = Mg.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Header(state: DashboardState, onTogglePause: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Mg.spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(DateFormats.shortDay(LocalDate.now()).uppercase(), style = Mg.type.label, color = Mg.colors.textSecondary)
            val shift = state.openShift
            Text(
                when {
                    shift == null -> "Pronto para rodar"
                    shift.isPaused -> "Turno pausado"
                    else -> "Rodando desde ${DateFormats.time(shift.startedAt)}"
                },
                style = Mg.type.title,
                color = when {
                    shift == null -> Mg.colors.textPrimary
                    shift.isPaused -> Mg.colors.warning
                    else -> Mg.colors.brand
                },
            )
        }
        // Pausa a um toque: almoço, abastecer, banheiro. Tempo pausado não conta como online.
        state.openShift?.let { shift ->
            androidx.compose.material3.OutlinedButton(onClick = onTogglePause) {
                Text(if (shift.isPaused) "▶ Retomar" else "❚❚ Pausar",
                    color = if (shift.isPaused) Mg.colors.brand else Mg.colors.warning)
            }
        }
    }
}

@Composable
private fun Alerts(state: DashboardState, navigate: (String) -> Unit) {
    if (state.reviewCount > 0 && (state.day?.summary?.pendingCount ?: 0) == 0) {
        MgCard(Modifier.fillMaxWidth(), onClick = { navigate(Routes.REVIEW) }, accent = Mg.colors.warning) {
            Text("${Units.count(state.reviewCount, "item", "itens")} para revisar", style = Mg.type.body, color = Mg.colors.warning)
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationsOff = !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (notificationsOff) {
        MgCard(Modifier.fillMaxWidth(), accent = Mg.colors.negative, onClick = {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) {
            Text("Notificações bloqueadas", style = Mg.type.body, color = Mg.colors.negative)
            Text("A análise das ofertas aparece como notificação. Toque para permitir.",
                style = Mg.type.caption, color = Mg.colors.textSecondary)
        }
    }
    if (!state.hasVehicle) {
        MgCard(Modifier.fillMaxWidth(), onClick = { navigate(Routes.VEHICLE) }, accent = Mg.colors.info) {
            Text("Cadastre seu carro", style = Mg.type.body, color = Mg.colors.info)
            Text("Escolha o modelo e o app calcula combustível, manutenção e depreciação.",
                style = Mg.type.caption, color = Mg.colors.textSecondary)
        }
    }
}

/** Uber x 99 como barra de proporção do faturamento, com valores embaixo. */
@Composable
private fun PlatformSplit(s: PeriodSummary) {
    if (s.byPlatform.isEmpty()) return
    val total = s.byPlatform.values.sumOf { it.grossCents }.coerceAtLeast(1)
    SectionTitle("Uber x 99")
    MgCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            s.byPlatform.forEach { (p, slice) ->
                Box(Modifier.weight(slice.grossCents.coerceAtLeast(1).toFloat() / total).fillMaxHeight().background(platformColor(p)))
            }
        }
        s.byPlatform.forEach { (p, slice) ->
            Row(Modifier.fillMaxWidth().padding(top = Mg.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = Mg.spacing.s).height(10.dp).fillMaxWidth(0.03f).clip(RoundedCornerShape(3.dp)).background(platformColor(p)))
                Text("${p.label} · ${Units.count(slice.tripCount, "corrida")}", style = Mg.type.body, color = Mg.colors.textSecondary,
                    modifier = Modifier.weight(1f))
                MoneyText(slice.grossCents, style = Mg.type.metricSmall)
            }
        }
    }
}

@Composable
private fun ReconciliationCard(day: DayOverview) {
    val items = day.reconciliation.filter { it.platformCents != null }
    if (items.isEmpty()) return
    SectionTitle("Conferência com as plataformas")
    MgCard(Modifier.fillMaxWidth()) {
        items.forEach { r ->
            val ok = r.reconciled == true
            Text("${r.platform.label}: capturado ${Money.format(r.capturedCents)} · app ${Money.format(r.platformCents!!)}",
                style = Mg.type.body, color = Mg.colors.textPrimary)
            Text(
                if (ok) "✓ Bate com o app" else "⚠ Diferença de ${Money.format(r.differenceCents ?: 0)}: há corrida faltando ou valor errado",
                style = Mg.type.caption, color = if (ok) Mg.colors.positive else Mg.colors.warning,
                modifier = Modifier.padding(bottom = Mg.spacing.s),
            )
        }
    }
}
