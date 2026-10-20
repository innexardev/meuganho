package com.innexar.meuganho.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.innexar.meuganho.core.designsystem.theme.Mg

/** Anel da meta: enche conforme o dia avança; percentual no centro. */
@Composable
fun GoalRing(ratio: Double?, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val target = (ratio ?: 0.0).toFloat().coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, animationSpec = tween(900), label = "meta")
    val track = Mg.colors.surfaceHigh
    val fill = if ((ratio ?: 0.0) >= 1.0) Mg.colors.positive else Mg.colors.brand
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 10.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
            drawArc(fill, -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ratio?.let { "${(it * 100).toInt()}%" } ?: "—", style = Mg.type.metric, color = Mg.colors.textPrimary)
        }
    }
}
