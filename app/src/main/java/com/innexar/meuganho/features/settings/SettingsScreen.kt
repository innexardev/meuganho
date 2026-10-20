package com.innexar.meuganho.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.app.navigation.Routes
import com.innexar.meuganho.core.designsystem.components.ChoiceRow
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.FuelType

@Composable
fun SettingsScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val fuel by vm.vehicleFuel.collectAsStateWithLifecycle()
    var confirmImport by remember { mutableStateOf(false) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportJson) }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importJson) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }

    MgScreen("Configurações", onBack) {
        val settings = s ?: return@MgScreen
        val t = settings.thresholds

        SectionTitle("Classificação de ofertas")
        Text("EXCELENTE e ACEITÁVEL exigem os dois critérios (R$/km e R$/h).", style = Mg.type.caption, color = Mg.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
            MoneySetting("Excelente R$/km", t.excellentCentsPerKm, Modifier.weight(1f)) { v -> vm.update { it.copy(thresholds = it.thresholds.copy(excellentCentsPerKm = v)) } }
            MoneySetting("Excelente R$/h", t.excellentCentsPerHour, Modifier.weight(1f)) { v -> vm.update { it.copy(thresholds = it.thresholds.copy(excellentCentsPerHour = v)) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
            MoneySetting("Aceitável R$/km", t.acceptableCentsPerKm, Modifier.weight(1f)) { v -> vm.update { it.copy(thresholds = it.thresholds.copy(acceptableCentsPerKm = v)) } }
            MoneySetting("Aceitável R$/h", t.acceptableCentsPerHour, Modifier.weight(1f)) { v -> vm.update { it.copy(thresholds = it.thresholds.copy(acceptableCentsPerHour = v)) } }
        }

        SectionTitle("Combustível e custos")
        // Só o que vale para o carro cadastrado (sem carro: mostra combustível líquido).
        if (fuel == FuelType.ELECTRIC) {
            MoneySetting("Preço do kWh (recarga)", settings.electricityPriceCentsPerKwh) { v -> vm.update { it.copy(electricityPriceCentsPerKwh = v) } }
        } else {
            MoneySetting("Preço do combustível (R$/L)", settings.fuelPriceCentsPerLiter) { v -> vm.update { it.copy(fuelPriceCentsPerLiter = v) } }
            if (fuel == FuelType.FLEX) {
                Text("No tanque agora", style = Mg.type.caption, color = Mg.colors.textSecondary)
                ChoiceRow(listOf(FuelType.GASOLINE, FuelType.ETHANOL), settings.currentFuel, { it.label }, { f -> vm.update { it.copy(currentFuel = f) } })
            }
            Toggle("Usar consumo medido nos abastecimentos", settings.useObservedConsumption) { v -> vm.update { it.copy(useObservedConsumption = v) } }
        }
        MoneySetting("Outros custos por km (seguro, documentos)", (settings.otherCentsPerKm).toLong()) { v ->
            vm.update { it.copy(otherCentsPerKm = v.toDouble()) }
        }

        SectionTitle("Leitura e painel")
        Toggle("GPS durante o turno", settings.gpsEnabled) { v -> vm.update { it.copy(gpsEnabled = v) } }
        Text("Mostrar a análise da oferta como", style = Mg.type.caption, color = Mg.colors.textSecondary)
        ChoiceRow(com.innexar.meuganho.data.preferences.OfferDisplay.entries, settings.offerDisplay, { it.label },
            { d -> vm.update { it.copy(offerDisplay = d, overlayEnabled = true) } })
        Text("Guardar telas de ofertas duvidosas (sem dados pessoais) por:", style = Mg.type.caption, color = Mg.colors.textSecondary)
        ChoiceRow(listOf(3, 7, 14, 30), settings.snapshotRetentionDays, { "$it dias" }, { d -> vm.update { it.copy(snapshotRetentionDays = d) } })

        SectionTitle("Calibração e estatísticas")
        Toggle("Enviar estatísticas de ofertas e corridas", settings.shareStats) { v -> vm.update { it.copy(shareStats = v) } }
        Text("Valores, km, tempos, horário e REGIÃO APROXIMADA (~1 km) das ofertas e corridas, para descobrir os melhores " +
            "horários e regiões. Nunca endereço, trajeto ou dados pessoais.", style = Mg.type.caption, color = Mg.colors.textSecondary)
        Toggle("Enviar telas para calibração", settings.sendSamples) { v -> vm.update { it.copy(sendSamples = v) } }
        Text("Envia amostras das telas do Uber/99 SEM nomes, endereços e textos livres, para ajustar a leitura automática. " +
            "Nenhum dado financeiro seu é enviado.", style = Mg.type.caption, color = Mg.colors.textSecondary)

        SectionTitle("Atualizações")
        com.innexar.meuganho.features.update.UpdateCard(alwaysVisible = true)

        SectionTitle("Backup")
        MgCard(Modifier.fillMaxWidth()) {
            val stamp = DateFormats.day(System.currentTimeMillis()).replace('/', '-')
            OutlinedButton(onClick = { exportJson.launch("meuganho-backup-$stamp.json") }, Modifier.fillMaxWidth()) { Text("Exportar backup (JSON)") }
            OutlinedButton(onClick = { confirmImport = true }, Modifier.fillMaxWidth()) { Text("Restaurar backup") }
            OutlinedButton(onClick = { exportCsv.launch("meuganho-corridas-$stamp.csv") }, Modifier.fillMaxWidth()) { Text("Exportar corridas (CSV)") }
            message?.let { Text(it, style = Mg.type.caption, color = Mg.colors.brand) }
        }

        SectionTitle("Sistema")
        OutlinedButton(onClick = { navigate(Routes.PERMISSIONS) }, Modifier.fillMaxWidth()) { Text("Permissões") }
        OutlinedButton(onClick = { navigate(Routes.DIAGNOSTICS) }, Modifier.fillMaxWidth()) { Text("Diagnóstico de captura") }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Restaurar backup?") },
            text = { Text("Todos os dados atuais serão substituídos pelos do arquivo. Faça um backup antes se tiver dúvida.") },
            confirmButton = { TextButton(onClick = { confirmImport = false; importJson.launch(arrayOf("application/json", "*/*")) }) { Text("Escolher arquivo") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MoneySetting(label: String, cents: Long, modifier: Modifier = Modifier, onValid: (Long) -> Unit) {
    // Sem chave: reformatar a cada tecla atrapalharia a digitação.
    var text by remember { mutableStateOf(Money.toInput(cents)) }
    MgField(label, text, { v ->
        text = v
        Money.parseInput(v)?.takeIf { it >= 0 }?.let(onValid)
    }, modifier)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Mg.type.body, color = Mg.colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
