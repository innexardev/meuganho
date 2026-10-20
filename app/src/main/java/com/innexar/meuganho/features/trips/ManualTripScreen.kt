package com.innexar.meuganho.features.trips

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.innexar.meuganho.core.designsystem.components.ChoiceRow
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.Platform

@Composable
fun ManualTripScreen(onBack: () -> Unit) {
    val vm = containerViewModel { ManualTripViewModel(it) }
    var platform by rememberSaveable { mutableStateOf(Platform.UBER) }
    var value by rememberSaveable { mutableStateOf("") }
    var km by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    MgScreen("Registrar corrida", onBack) {
        ChoiceRow(Platform.entries, platform, { it.label }, { platform = it })
        MgField("Valor recebido", value, { value = it }, suffix = "R$", isError = error != null, supporting = error)
        MgField("Km da corrida", km, { km = it }, suffix = "km", supporting = "Opcional: melhora o R$/km")
        MgField("Duração", minutes, { minutes = it.filter(Char::isDigit) }, suffix = "min", keyboard = KeyboardType.Number,
            supporting = "Terminou agora; a duração define o início")
        Button(
            onClick = { vm.save(platform, value, km, minutes, onSaved = onBack, onError = { error = it }) },
            modifier = Modifier.fillMaxWidth().height(Mg.spacing.touch),
        ) { Text("SALVAR", style = Mg.type.label) }
    }
}
