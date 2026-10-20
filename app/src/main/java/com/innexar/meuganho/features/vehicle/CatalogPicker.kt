package com.innexar.meuganho.features.vehicle

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.domain.vehicle.CatalogCar
import com.innexar.meuganho.domain.vehicle.VehicleCatalog

/** Montadora -> modelo/versão. Escolher preenche consumo e custos sugeridos. */
@Composable
fun CatalogPicker(onPick: (CatalogCar) -> Unit) {
    var manufacturer by remember { mutableStateOf<String?>(null) }
    MgCard(Modifier.fillMaxWidth()) {
        Text("Escolha o seu carro", style = Mg.type.title, color = Mg.colors.textPrimary)
        Text("Preenchemos consumo e custos com estimativas do Inmetro. Você pode ajustar tudo depois.",
            style = Mg.type.caption, color = Mg.colors.textSecondary)
        Dropdown("Montadora", manufacturer, VehicleCatalog.manufacturers, { it }) { manufacturer = it }
        manufacturer?.let { m ->
            Dropdown("Modelo e versão", null, VehicleCatalog.models(m), { "${it.label} · ${it.fuel.label}" }, onPick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(label: String, selected: T?, options: List<T>, text: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.let(text) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Toque para escolher") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}
