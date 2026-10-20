package com.innexar.meuganho.features.expenses

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.ChoiceRow
import com.innexar.meuganho.core.designsystem.components.InfoRow
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.ExpenseCategory

@Composable
fun ExpensesScreen(onBack: () -> Unit) {
    val vm = containerViewModel { ExpensesViewModel(it) }
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(ExpenseCategory.FOOD) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    MgScreen("Despesas", onBack) {
        MgCard(Modifier.fillMaxWidth()) {
            ChoiceRow(ExpenseCategory.entries, category, { it.label }, { category = it })
            MgField("Valor", amount, { amount = it }, suffix = "R$", isError = error != null, supporting = error)
            MgField("Observação", note, { note = it }, keyboard = KeyboardType.Text)
            Button(onClick = {
                vm.add(category, amount, note, onError = { error = it }) { amount = ""; note = ""; error = null }
            }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar") }
        }
        SectionTitle("Este mês · ${Money.format(expenses.sumOf { it.amountCents })}")
        ExpenseCategory.entries.mapNotNull { c ->
            expenses.filter { it.category == c }.sumOf { it.amountCents }.takeIf { it > 0 }?.let { c to it }
        }.forEach { (c, total) -> InfoRow(c.label, Money.format(total)) }
        expenses.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${DateFormats.day(e.at)} · ${e.category.label}" + (e.note?.let { " · $it" } ?: ""),
                    style = Mg.type.caption, color = Mg.colors.textSecondary, modifier = Modifier.weight(1f))
                Text(Money.format(e.amountCents), style = Mg.type.metricSmall, color = Mg.colors.textPrimary)
                IconButton(onClick = { vm.delete(e.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Mg.colors.textSecondary) }
            }
        }
    }
}
