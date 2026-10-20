package com.innexar.meuganho.features.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.platform.update.UpdateState

/**
 * Popup de atualização exibido sobre qualquer tela do app. "Depois" adia só até a
 * próxima abertura; o cartão no Dashboard e em Configurações continua disponível.
 */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val manager = context.appContainer.updateManager
    val state by manager.state.collectAsStateWithLifecycle()
    val postponed by manager.postponedVersion.collectAsStateWithLifecycle()

    val manifest = when (val s = state) {
        is UpdateState.Available -> s.manifest
        is UpdateState.Downloading -> s.manifest
        is UpdateState.Ready -> s.manifest
        else -> return
    }
    if (postponed == manifest.versionCode) return
    val later = { manager.postponedVersion.value = manifest.versionCode }

    AlertDialog(
        onDismissRequest = later,
        title = { Text("Nova versão ${manifest.versionName}") },
        text = {
            Column {
                if (manifest.notes.isNotBlank()) Text(manifest.notes, style = Mg.type.body)
                when (val s = state) {
                    is UpdateState.Downloading -> LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    is UpdateState.Ready -> Text("Pronta para instalar. Seus dados são mantidos.", style = Mg.type.caption, color = Mg.colors.textSecondary)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Available -> TextButton(onClick = manager::download) { Text("Atualizar agora") }
                is UpdateState.Ready -> TextButton(onClick = { manager.installNow(context) }) { Text("Instalar") }
                else -> TextButton(onClick = {}, enabled = false) { Text("Baixando…") }
            }
        },
        dismissButton = { TextButton(onClick = later) { Text("Depois") } },
    )
}
