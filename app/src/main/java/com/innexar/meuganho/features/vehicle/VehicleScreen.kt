package com.innexar.meuganho.features.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.ChoiceRow
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.FuelType

@Composable
fun VehicleScreen(onBack: () -> Unit) {
    val vm = containerViewModel { VehicleViewModel(it) }
    val f by vm.form.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val text = KeyboardType.Text

    val electric = f.fuelType == FuelType.ELECTRIC
    val unit = if (electric) "km/kWh" else "km/l"
    MgScreen("Veículo", onBack) {
        CatalogPicker(vm::applyCatalog)
        SectionTitle("Dados do carro")
        MgField("Fabricante", f.manufacturer, { v -> vm.update { it.copy(manufacturer = v) } }, keyboard = text)
        MgField("Modelo", f.model, { v -> vm.update { it.copy(model = v) } }, keyboard = text)
        Row(horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
            MgField("Versão", f.version, { v -> vm.update { it.copy(version = v) } }, Modifier.weight(1f), keyboard = text)
            MgField("Ano", f.year, { v -> vm.update { it.copy(year = v.filter(Char::isDigit).take(4)) } }, Modifier.weight(1f), keyboard = KeyboardType.Number)
        }
        ChoiceRow(com.innexar.meuganho.domain.vehicle.VehicleCatalog.commonVersions, f.version, { it },
            { v -> vm.update { it.copy(version = v) } })
        ChoiceRow(FuelType.entries, f.fuelType, { it.label }, { v -> vm.update { it.copy(fuelType = v) } })
        MgField("Odômetro atual", f.odometer, { v -> vm.update { it.copy(odometer = v.filter(Char::isDigit)) } }, suffix = "km", keyboard = KeyboardType.Number)

        SectionTitle(if (f.fromCatalog) "Consumo (estimado Inmetro · ajuste se quiser)" else "Consumo")
        Row(horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
            MgField("Cidade", f.city, { v -> vm.update { it.copy(city = v) } }, Modifier.weight(1f), suffix = unit)
            MgField("Estrada", f.highway, { v -> vm.update { it.copy(highway = v) } }, Modifier.weight(1f), suffix = unit)
        }
        MgField("Médio", f.average, { v -> vm.update { it.copy(average = v) } }, suffix = unit,
            supporting = "Vazio = 60% cidade + 40% estrada. Os abastecimentos corrigem isso com o tempo.")
        if (f.fuelType == FuelType.FLEX) {
            MgField("Médio com etanol", f.ethanol, { v -> vm.update { it.copy(ethanol = v) } }, suffix = "km/l",
                supporting = "Vazio = 70% do consumo com gasolina")
        }

        SectionTitle("Custos por km")
        MgField("Manutenção, pneus e óleo", f.maintenance, { v -> vm.update { it.copy(maintenance = v) } }, suffix = "R$/km",
            supporting = "Ex.: R$ 3.000/ano em 30.000 km = 0,10")
        MgField("Depreciação", f.depreciation, { v -> vm.update { it.copy(depreciation = v) } }, suffix = "R$/km",
            supporting = "Ex.: perde R$ 6.000/ano rodando 40.000 km = 0,15")
        error?.let { Text(it, color = Mg.colors.negative, style = Mg.type.caption) }
        Button(onClick = { vm.save(onSaved = onBack, onError = { error = it }) }, modifier = Modifier.fillMaxWidth().height(Mg.spacing.touch)) {
            Text("SALVAR", style = Mg.type.label)
        }
    }
}
