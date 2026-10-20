package com.innexar.meuganho.features.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.InfoRow
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.core.ui.shareText

/** Gravação automática de telas: o que foi capturado e o estado do envio. */
@Composable
fun CalibrationSection() {
    val vm = containerViewModel { CalibrationViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SectionTitle("Calibração automática")
    MgCard(Modifier.fillMaxWidth()) {
        InfoRow("Envio automático", if (state.enabled) "ligado" else "desligado",
            valueColor = if (state.enabled) Mg.colors.positive else Mg.colors.warning)
        InfoRow("Na fila para envio", "${state.pending}")
        InfoRow("Último envio", state.status.lastSuccessAt?.let { "${DateFormats.day(it)} ${DateFormats.time(it)}" } ?: "nunca")
        state.status.lastError?.let { Text("Último erro: $it", style = Mg.type.caption, color = Mg.colors.warning) }
        state.message?.let { Text(it, style = Mg.type.caption, color = Mg.colors.brand) }
        Row(horizontalArrangement = Arrangement.spacedBy(Mg.spacing.s)) {
            OutlinedButton(onClick = { vm.uploadNow() }, modifier = Modifier.weight(1f)) { Text("Enviar agora") }
            OutlinedButton(onClick = { context.shareText(vm.shareText(), "Compartilhar telas") }, modifier = Modifier.weight(1f)) {
                Text("Compartilhar")
            }
        }
    }
    if (state.latest.isEmpty()) {
        Text("Nenhuma tela gravada ainda. Abra o Uber ou a 99 com a Acessibilidade ativa.",
            style = Mg.type.caption, color = Mg.colors.textSecondary)
    }
    state.latest.forEach { s ->
        var open by rememberSaveable(s.id) { mutableStateOf(false) }
        MgCard(Modifier.fillMaxWidth(), onClick = { open = !open }) {
            Text("${s.platform} · ${s.screenType} · ${s.reason}", style = Mg.type.body, color = Mg.colors.textPrimary)
            Text("${DateFormats.time(s.capturedAt)} · ${if (s.uploaded) "enviada" else "na fila"}",
                style = Mg.type.caption, color = Mg.colors.textSecondary)
            if (open) {
                Text(s.parseSummary, style = Mg.type.caption, color = Mg.colors.brand)
                Text(s.sanitizedText, style = Mg.type.caption.copy(fontFamily = FontFamily.Monospace), color = Mg.colors.textSecondary)
            } else {
                TextButton(onClick = { open = true }) { Text("Ver texto") }
            }
        }
    }
}
