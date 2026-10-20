package com.innexar.meuganho.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money

/** Valor em reais com sinal de cor opcional (positivo/negativo). */
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = Mg.type.metric,
    color: Color = Mg.colors.textPrimary,
    signColored: Boolean = false,
) {
    val tint = if (signColored) (if (cents < 0) Mg.colors.negative else Mg.colors.positive) else color
    Text(Money.format(cents), modifier = modifier, style = style, color = tint)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Mg.spacing.l, bottom = Mg.spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = Mg.type.label, color = Mg.colors.textSecondary)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = Mg.colors.brand) }
        }
    }
}

/** Par rótulo/valor em linha, usado em detalhes e resumos. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Mg.colors.textPrimary) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Mg.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Mg.spacing.m),
    ) {
        Text(label, style = Mg.type.body, color = Mg.colors.textSecondary, modifier = Modifier.weight(1f))
        // Valor sempre colado à direita; textos longos quebram em até ~60% da largura.
        Text(value, style = Mg.type.metricSmall, color = valueColor, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.widthIn(max = 240.dp))
    }
}
