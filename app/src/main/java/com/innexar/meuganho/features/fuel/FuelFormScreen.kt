package com.innexar.meuganho.features.fuel

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.ChoiceRow
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.FuelType

@Composable
fun FuelFormScreen(onBack: () -> Unit) {
    val vm = containerViewModel { FuelViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var odometer by rememberSaveable { mutableStateOf("") }
    var fuel by rememberSaveable { mutableStateOf(FuelType.GASOLINE) }
    var price by rememberSaveable { mutableStateOf("") }
    var liters by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    var fullTank by rememberSaveable { mutableStateOf(true) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.lastOdometer) {
        if (odometer.isEmpty()) state.lastOdometer?.let { odometer = it.toString() }
    }

    MgScreen("Novo abastecimento", onBack) {
        MgField("Odômetro", odometer, { odometer = it.filter(Char::isDigit) }, suffix = "km", keyboard = KeyboardType.Number)
        ChoiceRow(listOf(FuelType.GASOLINE, FuelType.ETHANOL, FuelType.DIESEL, FuelType.CNG), fuel, { it.label }, { fuel = it })
        MgField("Preço por litro", price, { price = it }, suffix = "R$/L")
        MgField("Litros", liters, { liters = it }, suffix = "L")
        MgField("Total pago", total, { total = it }, suffix = "R$", supporting = "Preencha dois dos três campos", isError = error != null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tanque cheio", style = Mg.type.body, color = Mg.colors.textPrimary, modifier = Modifier.weight(1f))
            Switch(checked = fullTank, onCheckedChange = { fullTank = it })
        }
        MgField("Observação", note, { note = it }, keyboard = KeyboardType.Text)
        error?.let { Text(it, color = Mg.colors.negative, style = Mg.type.caption) }
        Button(
            onClick = { vm.save(odometer, fuel, price, liters, total, fullTank, note, onSaved = onBack, onError = { error = it }) },
            modifier = Modifier.fillMaxWidth().height(Mg.spacing.touch),
        ) { Text("SALVAR", style = Mg.type.label) }
    }
}
