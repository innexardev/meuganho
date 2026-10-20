package com.innexar.meuganho.features.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.domain.stats.Bucket

/**
 * Barras horizontais de R$/h por faixa. Barra + número escrito: o valor é
 * legível sem depender da cor ou do comprimento. Destaque para as 3 melhores.
 */
@Composable
fun BucketBars(buckets: List<Bucket>, label: (Int) -> String) {
    val valid = buckets.filter { it.onlineSeconds >= 1800 && it.centsPerHour != null }
    if (valid.isEmpty()) {
        Text("Dados insuficientes (mínimo 30 min online por faixa).", style = Mg.type.caption, color = Mg.colors.textSecondary)
        return
    }
    val max = valid.maxOf { it.centsPerHour!! }.coerceAtLeast(1)
    val top = valid.sortedByDescending { it.centsPerHour }.take(3).map { it.key }.toSet()
    valid.forEach { b ->
        val best = b.key in top
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label(b.key), style = Mg.type.caption, color = Mg.colors.textSecondary, modifier = Modifier.width(56.dp))
            Box(Modifier.weight(1f).height(14.dp)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(b.centsPerHour!!.toFloat() / max)
                        .clip(RoundedCornerShape(Mg.radius.s))
                        .background(if (best) Mg.colors.brand else Mg.colors.surfaceHigh)
                )
            }
            Text((if (best) "★ " else "") + Units.perHour(b.centsPerHour!!), style = Mg.type.caption,
                color = if (best) Mg.colors.brand else Mg.colors.textPrimary, modifier = Modifier.padding(start = Mg.spacing.s))
        }
    }
}
