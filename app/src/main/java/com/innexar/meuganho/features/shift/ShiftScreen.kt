package com.innexar.meuganho.features.shift

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.app.navigation.Routes
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.MultiChoiceRow
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.platform.foreground.ShiftForegroundService

@Composable
fun ShiftScreen(navigate: (String) -> Unit) {
    val vm = containerViewModel { ShiftViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Pede localização (e notificação no Android 13+) na hora de começar o turno,
    // quando o motivo é óbvio para o usuário.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ShiftForegroundService.startIfAllowed(context)
    }
    val startGps: (Boolean) -> Unit = { gpsEnabled ->
        if (gpsEnabled && !ShiftForegroundService.startIfAllowed(context)) {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    MgScreen(title = if (state.openShift == null) "Iniciar turno" else "Turno ativo", onBack = null) {
        if (state.openShift == null) {
            StartShiftForm(vm, state, onStart = { odo, price -> vm.start(odo, price, startGps) })
        } else {
            ActiveShift(vm, state, navigate, onResumeGps = { startGps(state.gpsEnabled) }, onEnded = {
                ShiftForegroundService.stop(context)
                navigate(Routes.day(java.time.LocalDate.now().toEpochDay()))
            })
        }
    }
}

@Composable
private fun StartShiftForm(vm: ShiftViewModel, state: ShiftUiState, onStart: (String, String) -> Unit) {
    val platforms by vm.platforms.collectAsStateWithLifecycle()
    var odometer by rememberSaveable(state.suggestedOdometer) { mutableStateOf(state.suggestedOdometer) }
    var fuelPrice by rememberSaveable(state.suggestedFuelPrice) { mutableStateOf(state.suggestedFuelPrice) }

    Text("Plataformas", style = Mg.type.label, color = Mg.colors.textSecondary)
    MultiChoiceRow(Platform.entries, platforms, { it.label }, vm::togglePlatform)
    MgField("Odômetro inicial", odometer, { odometer = it.filter(Char::isDigit) }, suffix = "km",
        supporting = "Opcional. Com início e fim, o km do dia usa o odômetro (mais exato que o GPS).")
    MgField("Preço do combustível", fuelPrice, { fuelPrice = it }, suffix = "R$/L")
    if (!state.accessibilityOn) {
        MgCard(Modifier.fillMaxWidth(), accent = Mg.colors.warning) {
            Text("Captura automática desligada", style = Mg.type.body, color = Mg.colors.warning)
            Text("Ative a Acessibilidade do MeuGanho em Mais > Permissões para ler ofertas do Uber e 99. " +
                "Sem ela você ainda pode registrar corridas manualmente.", style = Mg.type.caption, color = Mg.colors.textSecondary)
        }
    }
    Button(onClick = { onStart(odometer, fuelPrice) }, modifier = Modifier.fillMaxWidth().height(Mg.spacing.touch)) {
        Text("INICIAR TURNO", style = Mg.type.label)
    }
}

@Composable
private fun ActiveShift(
    vm: ShiftViewModel,
    state: ShiftUiState,
    navigate: (String) -> Unit,
    onResumeGps: () -> Unit,
    onEnded: () -> Unit,
) {
    val shift = state.openShift ?: return
    var ending by remember { mutableStateOf(false) }
    val s = state.today?.summary

    MgCard(Modifier.fillMaxWidth(), glow = true) {
        Text(if (shift.isPaused) "PAUSADO" else "ONLINE", style = Mg.type.label,
            color = if (shift.isPaused) Mg.colors.warning else Mg.colors.positive)
        Text(Units.duration(s?.onlineSeconds ?: 0), style = Mg.type.hero, color = Mg.colors.textPrimary)
        Text(state.live.state.label + (state.live.platform?.let { " · ${it.label}" } ?: ""),
            style = Mg.type.body, color = Mg.colors.textSecondary)
        Text(if (state.gpsRunning) "GPS registrando · ${Units.km(shift.gpsMeters)} no turno" else "GPS desligado · km do turno não está sendo medido",
            style = Mg.type.caption, color = if (state.gpsRunning) Mg.colors.textSecondary else Mg.colors.warning)
    }
    if (!state.gpsRunning && state.gpsEnabled && !shift.isPaused) {
        OutlinedButton(onClick = onResumeGps, modifier = Modifier.fillMaxWidth()) { Text("Ativar GPS do turno") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
        OutlinedButton(onClick = { vm.togglePause() }, modifier = Modifier.weight(1f).height(Mg.spacing.touch)) {
            Text(if (shift.isPaused) "Retomar" else "Pausar")
        }
        OutlinedButton(onClick = { navigate(Routes.MANUAL_TRIP) }, modifier = Modifier.weight(1f).height(Mg.spacing.touch)) {
            Text("+ Corrida")
        }
        OutlinedButton(onClick = { navigate(Routes.EXPENSES) }, modifier = Modifier.weight(1f).height(Mg.spacing.touch)) {
            Text("+ Despesa")
        }
    }
    if (s != null) {
        MgCard(Modifier.fillMaxWidth()) {
            com.innexar.meuganho.core.designsystem.components.MetricGrid(
                listOf(
                    Triple("Hoje", com.innexar.meuganho.core.money.Money.formatShort(s.grossCents),
                        if (s.pendingCount > 0) "+${com.innexar.meuganho.core.money.Money.formatShort(s.pendingCents)} a confirmar" else null),
                    Triple("Corridas", s.tripCount.toString(), null),
                    Triple("R$/hora", s.centsPerHourOnline?.let { Units.perHourValue(it) } ?: "—", "online"),
                ),
            )
        }
    }
    Button(
        onClick = { ending = true },
        modifier = Modifier.fillMaxWidth().height(Mg.spacing.touch),
        colors = ButtonDefaults.buttonColors(containerColor = Mg.colors.negative),
    ) { Text("ENCERRAR TURNO", style = Mg.type.label) }

    if (ending) EndShiftDialog(onDismiss = { ending = false }, onConfirm = { odo, notes -> vm.end(odo, notes, onEnded) })
}
