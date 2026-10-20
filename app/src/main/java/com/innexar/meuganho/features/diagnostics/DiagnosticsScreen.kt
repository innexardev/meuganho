package com.innexar.meuganho.features.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.InfoRow
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.core.ui.shareText
import com.innexar.meuganho.domain.model.Platform

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { DiagnosticsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions(context) }

    MgScreen("Diagnóstico", onBack) {
        SectionTitle("Serviços e permissões")
        MgCard(Modifier.fillMaxWidth()) {
            val p = state.permissions
            Check("Acessibilidade habilitada", p?.accessibility)
            Check("Acessibilidade conectada agora", state.accessibilityConnected)
            Check("Leitor de notificações", p?.notificationListener)
            Check("Localização", p?.location)
            Check("Notificações", p?.postNotifications)
            Check("Sem restrição de bateria", p?.batteryUnrestricted)
            Check("Serviço do turno (GPS) rodando", state.shiftServiceRunning)
            InfoRow("Overlay", "janela de acessibilidade (sem permissão extra)")
        }

        Platform.entries.forEach { platform ->
            val d = state.platforms[platform]
            val h = state.health[platform]
            SectionTitle(platform.label)
            MgCard(Modifier.fillMaxWidth(), accent = if (h?.tripped == true) Mg.colors.negative else null) {
                InfoRow("Último evento", d?.lastEventAt?.let { DateFormats.time(it) } ?: "nunca")
                InfoRow("Última tela", d?.lastScreenType?.name ?: "—")
                InfoRow("Eventos (5 min)", "${d?.eventsLast5Min ?: 0}")
                InfoRow("Versão do app", d?.appVersionCode?.toString() ?: "—")
                InfoRow("Versão do parser", d?.parserVersion ?: "—")
                InfoRow("Duplicatas ignoradas", "${d?.duplicatesIgnored ?: 0}")
                InfoRow("Ofertas encontradas", "${d?.offersFound ?: 0}")
                if ((d?.soundTriggers ?: 0) > 0) InfoRow("Leituras disparadas por som", "${d?.soundTriggers}")
                val heard = (d?.soundOffersRead ?: 0) + (d?.soundOffersMissed ?: 0)
                if (heard > 0) InfoRow("Ofertas lidas após o toque", "${d?.soundOffersRead} de $heard (${(d?.soundOffersRead ?: 0) * 100 / heard}%)")
                if ((d?.queueDropped ?: 0) > 0) InfoRow("Telas descartadas (fila cheia)", "${d?.queueDropped}")
                if ((d?.ocrOk ?: 0) + (d?.ocrFailed ?: 0) > 0) {
                    InfoRow("Leituras por imagem", "${d?.ocrOk} ok · ${d?.ocrFailed} falhas · ${d?.ocrAvgMs} ms")
                }
                InfoRow("Última oferta", d?.lastOfferSummary ?: "—")
                d?.lastTree?.let { Text("Árvore: $it", style = Mg.type.caption, color = Mg.colors.textSecondary) }
                d?.lastParserError?.let {
                    Text("Último problema (${d.lastParserErrorAt?.let(DateFormats::time)}): $it", style = Mg.type.caption, color = Mg.colors.warning)
                }
                if (h != null) {
                    InfoRow("Capturas duvidosas", "${h.badSamples}/${h.samples} (${Units.percent(h.badRatio)})",
                        valueColor = if (h.tripped) Mg.colors.negative else Mg.colors.textPrimary)
                    if (h.tripped) {
                        Text("Análise automática suspensa: o app provavelmente mudou de layout. Grave uma tela abaixo para ajustar o parser.",
                            style = Mg.type.caption, color = Mg.colors.negative)
                        TextButton(onClick = { vm.resetHealth(platform) }) { Text("Reativar análise") }
                    }
                }
            }
        }

        CalibrationSection()

        SectionTitle("Gravar tela manualmente")
        MgCard(Modifier.fillMaxWidth()) {
            Text("Abra o Uber ou a 99 na tela desejada (oferta, corrida, ganhos). O texto é gravado SEM endereços e nomes.",
                style = Mg.type.caption, color = Mg.colors.textSecondary)
            OutlinedButton(onClick = vm::armRecording, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.recordingArmed) "Aguardando próxima tela…" else "Gravar próxima tela")
            }
            state.recorded?.let { text ->
                Text(text, style = Mg.type.caption.copy(fontFamily = FontFamily.Monospace), color = Mg.colors.textPrimary)
                OutlinedButton(onClick = { context.shareText(text, "Compartilhar tela gravada") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Compartilhar")
                }
            }
        }

        SectionTitle("Log técnico")
        state.logs.forEach { e ->
            Text("${DateFormats.time(e.at)} [${e.category}] ${e.message}", style = Mg.type.caption,
                color = if (e.isError) Mg.colors.negative else Mg.colors.textSecondary)
        }
    }
}

@Composable
private fun Check(label: String, ok: Boolean?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Mg.type.body, color = Mg.colors.textSecondary)
        Text(
            when (ok) { true -> "✓ sim"; false -> "✕ não"; null -> "…" },
            style = Mg.type.metricSmall,
            color = when (ok) { true -> Mg.colors.positive; false -> Mg.colors.negative; null -> Mg.colors.textSecondary },
        )
    }
}
