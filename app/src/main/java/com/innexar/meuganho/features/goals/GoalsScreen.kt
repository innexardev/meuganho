package com.innexar.meuganho.features.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.ChoiceRow
import com.innexar.meuganho.core.designsystem.components.GoalProgressBar
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgField
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.model.GoalKind
import com.innexar.meuganho.domain.model.GoalPeriod

/** As três metas numa lista só, com um único Salvar e o progresso de cada uma. */
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { GoalsViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val kinds = remember { mutableStateMapOf<GoalPeriod, GoalKind>() }
    val targets = remember { mutableStateMapOf<GoalPeriod, String>() }
    var saved by remember { mutableStateOf(false) }

    MgScreen("Metas", onBack) {
        Text("Bruta = faturamento. Líquida = resultado depois dos custos do carro e despesas. Deixe vazio para não ter meta.",
            style = Mg.type.caption, color = Mg.colors.textSecondary)
        MgCard(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = Mg.spacing.m), color = Mg.colors.outline)
                val kind = kinds[row.period] ?: row.goal?.kind ?: GoalKind.GROSS
                val target = targets[row.period] ?: row.goal?.targetCents?.let { Money.toInput(it) }.orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
                    Text(row.period.label, style = Mg.type.title, color = Mg.colors.textPrimary, modifier = Modifier.weight(1f))
                    ChoiceRow(GoalKind.entries, kind, { it.label }, { kinds[row.period] = it; saved = false }, Modifier.weight(1.4f))
                }
                MgField("Valor", target, { targets[row.period] = it; saved = false }, suffix = "R$")
                row.progress?.let { GoalProgressBar(it, "") }
            }
        }
        Button(
            onClick = {
                rows.forEach { row ->
                    vm.save(row.period, kinds[row.period] ?: row.goal?.kind ?: GoalKind.GROSS,
                        targets[row.period] ?: row.goal?.targetCents?.let { Money.toInput(it) }.orEmpty(), row.goal)
                }
                saved = true
            },
            modifier = Modifier.fillMaxWidth().height(Mg.spacing.touch),
        ) { Text(if (saved) "✓ Metas salvas" else "SALVAR METAS", style = Mg.type.label) }
    }
}
