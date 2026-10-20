package com.innexar.meuganho.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.domain.financial.GoalProgress

/** "R$276 / R$350 · 79%" com barra e tempo estimado para bater a meta. */
@Composable
fun GoalProgressBar(progress: GoalProgress, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label.uppercase(), style = Mg.type.label, color = Mg.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(Units.percent(progress.ratio), style = Mg.type.metricSmall, color = Mg.colors.brand)
        }
        Box(
            Modifier.padding(vertical = Mg.spacing.s).fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(Mg.radius.pill)).background(Mg.colors.surfaceHigh)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(progress.ratio.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(Mg.radius.pill)).background(Mg.colors.brand)
            )
        }
        val eta = progress.estimatedSecondsToGoal
        val right = when {
            progress.reached -> "Meta batida"
            eta != null -> "Faltam ${Money.formatShort(progress.remainingCents)} · ~${Units.duration(eta)}"
            else -> "Faltam ${Money.formatShort(progress.remainingCents)}"
        }
        Text(
            "${Money.formatShort(progress.currentCents)} / ${Money.formatShort(progress.targetCents)}   ·   $right",
            style = Mg.type.caption, color = Mg.colors.textSecondary,
        )
    }
}
