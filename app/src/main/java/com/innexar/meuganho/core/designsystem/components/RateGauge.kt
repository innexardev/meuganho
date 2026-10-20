package com.innexar.meuganho.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innexar.meuganho.core.designsystem.theme.Mg
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mostrador de R$/hora (o "velocímetro" do dia). As faixas usam os limites PESSOAIS
 * do motorista: vermelho abaixo do aceitável, âmbar até o excelente, verde acima.
 * O ponteiro anima até o valor. Arco de 240°, aberto embaixo, como um painel de carro.
 */
@Composable
fun RateGauge(
    centsPerHour: Long?,
    acceptableCentsPerHour: Long,
    excellentCentsPerHour: Long,
    valueText: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val max = (excellentCentsPerHour * 1.6).coerceAtLeast(1.0)
    val target = ((centsPerHour ?: 0) / max).toFloat().coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, animationSpec = tween(900), label = "ponteiro")
    val track = Mg.colors.surfaceHigh
    val low = Mg.colors.negative
    val mid = Mg.colors.warning
    val high = Mg.colors.positive
    val needle = Mg.colors.textPrimary
    val acc = (acceptableCentsPerHour / max).toFloat()
    val exc = (excellentCentsPerHour / max).toFloat()

    Column(modifier.fillMaxWidth().semantics { contentDescription = "$caption: $valueText" },
        horizontalAlignment = Alignment.CenterHorizontally) {
        // O arco de 240° desce até 3/4 do diâmetro: altura = 0,75·d + traço. Valor fica ABAIXO do eixo.
        Canvas(Modifier.fillMaxWidth(0.82f).aspectRatio(1.28f)) {
            val stroke = 16.dp.toPx()
            val diameter = minOf(size.width, (size.height - stroke) / 0.75f) - stroke
            val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
            val arc = Size(diameter, diameter)
            fun sweep(from: Float, to: Float, color: androidx.compose.ui.graphics.Color, alpha: Float) =
                drawArc(color.copy(alpha = alpha), START + from * SWEEP, (to - from) * SWEEP, false, topLeft, arc,
                    style = Stroke(stroke, cap = StrokeCap.Butt))
            drawArc(track, START, SWEEP, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            // Faixas pessoais (esmaecidas) e o trecho já percorrido (forte).
            sweep(0f, acc, low, 0.28f); sweep(acc, exc, mid, 0.28f); sweep(exc, 1f, high, 0.28f)
            val lit = progress
            if (lit > 0f) sweep(0f, minOf(lit, acc), low, 1f)
            if (lit > acc) sweep(acc, minOf(lit, exc), mid, 1f)
            if (lit > exc) sweep(exc, lit, high, 1f)
            val center = Offset(topLeft.x + diameter / 2, topLeft.y + diameter / 2)
            val angle = Math.toRadians((START + lit * SWEEP).toDouble())
            val length = diameter / 2 - stroke * 1.6f
            val tip = Offset(center.x + length * cos(angle).toFloat(), center.y + length * sin(angle).toFloat())
            drawLine(needle, center, tip, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(needle, radius = 9.dp.toPx(), center = center)
            drawCircle(track, radius = 4.dp.toPx(), center = center)
        }
        Text(valueText, style = Mg.type.hero.copy(fontSize = 48.sp), color = Mg.colors.textPrimary,
            modifier = Modifier.padding(top = 2.dp))
        Text(caption.uppercase(), style = Mg.type.label, color = Mg.colors.textSecondary)
    }
}

private const val START = 150f
private const val SWEEP = 240f
