package com.innexar.meuganho.features.shift

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.theme.Mg

@Composable
fun EndShiftDialog(onDismiss: () -> Unit, onConfirm: (odometer: String, notes: String) -> Unit) {
    var odometer by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encerrar turno") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Mg.spacing.s)) {
                MgField("Odômetro final", odometer, { odometer = it.filter(Char::isDigit) }, suffix = "km")
                MgField("Observação", notes, { notes = it }, keyboard = KeyboardType.Text)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(odometer, notes) }) { Text("Encerrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
