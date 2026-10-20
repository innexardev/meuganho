package com.innexar.meuganho.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.innexar.meuganho.core.designsystem.theme.Mg

/** Métrica isolada: rótulo pequeno em cima, número grande embaixo. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = Mg.colors.textPrimary,
) {
    Column(modifier) {
        Text(label.uppercase(), style = Mg.type.label, color = Mg.colors.textSecondary)
        Text(value, style = Mg.type.metric, color = valueColor, maxLines = 1)
        if (sub != null) Text(sub, style = Mg.type.caption, color = Mg.colors.textSecondary, maxLines = 1)
    }
}

/** Grade simples de métricas em linhas de N colunas. */
@Composable
fun MetricGrid(items: List<Triple<String, String, String?>>, modifier: Modifier = Modifier, columns: Int = 3) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Mg.spacing.l)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m)) {
                row.forEach { (label, value, sub) -> MetricTile(label, value, Modifier.weight(1f), sub) }
                repeat(columns - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
