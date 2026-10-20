package com.innexar.meuganho.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.platform.permissions.PermissionChecker
import com.innexar.meuganho.platform.permissions.PermissionState

/**
 * Onboarding e tela de permissões. Cada permissão explica POR QUE existe; todas
 * são opcionais: sem nenhuma, o app ainda funciona com registro manual.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit, onboarding: Boolean) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PermissionState?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = PermissionChecker.state(context) }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state = PermissionChecker.state(context)
    }
    val s = state

    MgScreen(if (onboarding) "Bem-vindo ao MeuGanho" else "Permissões", onBack = if (onboarding) null else onDone) {
        if (onboarding) {
            Text("Seu computador de bordo financeiro: analisa cada oferta, acompanha o turno e mostra quanto você realmente ganhou.",
                style = Mg.type.body, color = Mg.colors.textSecondary)
            Text("Seus valores, corridas e turnos ficam no seu celular. Para calibrar a leitura e descobrir os melhores " +
                "horários e regiões, o app envia dados das ofertas sem nomes, endereços ou recados. Dá para desligar em Configurações.",
                style = Mg.type.caption, color = Mg.colors.textSecondary)
        }
        PermissionCard(
            "Acessibilidade", s?.accessibility,
            "Lê o texto das telas do Uber e da 99 para analisar ofertas e acompanhar corridas. Só esses dois apps. " +
                "O MeuGanho nunca toca em botões: aceitar ou recusar é sempre decisão sua.",
        ) { context.startActivity(PermissionChecker.accessibilitySettings()) }
        PermissionCard(
            "Localização", s?.location,
            "Mede os km do turno (vazio, buscando passageiro e em corrida). Só durante um turno ativo; nenhum trajeto é guardado.",
        ) {
            runtime.launch(buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray())
        }
        PermissionCard(
            "Notificações dos apps", s?.notificationListener,
            "Opcional. Fonte reserva de ofertas quando a Acessibilidade estiver desligada e ajuda no diagnóstico.",
        ) { context.startActivity(PermissionChecker.notificationListenerSettings()) }
        PermissionCard(
            "Bateria sem restrição", s?.batteryUnrestricted,
            "Evita que o Android encerre o registro do turno. O MeuGanho é orientado a eventos e gasta pouco.",
        ) { context.startActivity(PermissionChecker.batterySettings()) }

        MgCard(Modifier.fillMaxWidth(), accent = if (s?.canSelfRestore == true) Mg.colors.positive else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Religar a leitura sozinho", style = Mg.type.title, color = Mg.colors.textPrimary, modifier = Modifier.weight(1f))
                Text(if (s?.canSelfRestore == true) "✓ ATIVO" else "OPCIONAL", style = Mg.type.label,
                    color = if (s?.canSelfRestore == true) Mg.colors.positive else Mg.colors.textSecondary)
            }
            Text("O Samsung desliga a Acessibilidade de apps fora da loja depois de atualizações. Para o MeuGanho religar " +
                "sozinho, rode UMA vez num computador com o celular no cabo (depuração USB ligada):",
                style = Mg.type.caption, color = Mg.colors.textSecondary, modifier = Modifier.padding(vertical = Mg.spacing.s))
            Text(com.innexar.meuganho.platform.permissions.AccessibilityRestorer.ADB_COMMAND,
                style = Mg.type.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Mg.colors.brand)
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(Mg.spacing.touch).padding(top = Mg.spacing.s)) {
            Text(if (onboarding) "CONTINUAR" else "PRONTO", style = Mg.type.label)
        }
    }
}

@Composable
private fun PermissionCard(title: String, granted: Boolean?, why: String, onGrant: () -> Unit) {
    MgCard(Modifier.fillMaxWidth(), accent = if (granted == true) Mg.colors.positive else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Mg.type.title, color = Mg.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(if (granted == true) "✓ ATIVO" else "✕ INATIVO", style = Mg.type.label,
                color = if (granted == true) Mg.colors.positive else Mg.colors.textSecondary)
        }
        Text(why, style = Mg.type.caption, color = Mg.colors.textSecondary, modifier = Modifier.padding(vertical = Mg.spacing.s))
        if (granted != true) OutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Ativar") }
    }
}
