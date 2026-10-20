package com.innexar.meuganho.features.history

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import com.innexar.meuganho.core.designsystem.components.EmptyState
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.MoneyText
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navigate: (String) -> Unit) {
    val vm = containerViewModel { HistoryViewModel(it) }
    val period by vm.period.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    MgScreen("Histórico", onBack = null) {
        ChoiceRow(HistoryPeriod.entries, period, { it.label }, { if (it == HistoryPeriod.CUSTOM) picking = true else vm.select(it) })
        state.summary?.let { SummaryCard(it) }
        SectionTitle("Dias")
        if (state.days.isEmpty()) EmptyState("Sem corridas no período", "Os dias com corridas aparecem aqui.")
        state.days.forEach { day ->
            MgCard(Modifier.fillMaxWidth(), onClick = { navigate(Routes.day(day.date.toEpochDay())) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DateFormats.shortDay(day.date), style = Mg.type.body, color = Mg.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${day.trips} corridas" + if (day.pending > 0) " · ${day.pending}?" else "",
                        style = Mg.type.caption, color = if (day.pending > 0) Mg.colors.warning else Mg.colors.textSecondary)
                    Text("   ")
                    MoneyText(day.grossCents, style = Mg.type.metricSmall)
                }
            }
        }
    }

    if (picking) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val from = pickerState.selectedStartDateMillis
                    val to = pickerState.selectedEndDateMillis ?: from
                    if (from != null && to != null) {
                        // O seletor devolve meia-noite UTC; convertemos para a data local escolhida.
                        vm.setCustom(
                            Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    picking = false
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(state = pickerState, title = { Text("Período", modifier = Modifier) })
        }
    }
}
