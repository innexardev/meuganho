package com.innexar.meuganho.features.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.BuildConfig
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.platform.update.UpdateState

/**
 * Cartão de atualização. [alwaysVisible] = false (Dashboard) só aparece quando há
 * algo a fazer; true (Configurações) mostra também a versão atual e "Verificar".
 */
@Composable
fun UpdateCard(alwaysVisible: Boolean = false) {
    val context = LocalContext.current
    val manager = context.appContainer.updateManager
    val state by manager.state.collectAsStateWithLifecycle()
    val actionable = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready
    if (!alwaysVisible && !actionable) return

    MgCard(Modifier.fillMaxWidth(), accent = if (actionable) Mg.colors.brand else null) {
        when (val s = state) {
            is UpdateState.Available -> {
                Text("Nova versão ${s.manifest.versionName}", style = Mg.type.title, color = Mg.colors.brand)
                if (s.manifest.notes.isNotBlank()) Text(s.manifest.notes, style = Mg.type.caption, color = Mg.colors.textSecondary)
                Button(onClick = manager::download, modifier = Modifier.fillMaxWidth()) { Text("Atualizar agora") }
            }
            is UpdateState.Downloading -> {
                Text("Baixando ${s.manifest.versionName}…", style = Mg.type.body, color = Mg.colors.textPrimary)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> {
                Text("Versão ${s.manifest.versionName} pronta", style = Mg.type.title, color = Mg.colors.brand)
                Text("Seus dados são mantidos. Se o Android pedir, permita que o MeuGanho instale apps e toque em Instalar de novo.",
                    style = Mg.type.caption, color = Mg.colors.textSecondary)
                Button(onClick = { manager.installNow(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Instalar")
                }
            }
            else -> {
                Text("Versão instalada: ${BuildConfig.VERSION_NAME}", style = Mg.type.body, color = Mg.colors.textPrimary)
                Text(
                    when (s) {
                        UpdateState.Checking -> "Verificando…"
                        UpdateState.UpToDate -> "Você está na versão mais recente."
                        is UpdateState.Failed -> s.message
                        else -> "Atualizações são verificadas automaticamente."
                    },
                    style = Mg.type.caption,
                    color = if (s is UpdateState.Failed) Mg.colors.warning else Mg.colors.textSecondary,
                )
                OutlinedButton(onClick = manager::check, modifier = Modifier.fillMaxWidth()) { Text("Verificar atualização") }
            }
        }
    }
}
