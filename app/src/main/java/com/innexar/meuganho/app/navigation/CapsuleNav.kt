package com.innexar.meuganho.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innexar.meuganho.core.designsystem.theme.Mg

data class NavTab(val route: String, val label: String, val icon: ImageVector)

/**
 * Barra de navegação flutuante em cápsula, com o botão de TURNO no centro:
 * ▶ para iniciar; com turno aberto, mostra o tempo online correndo.
 * Um toque leva ao Turno de qualquer tela.
 */
@Composable
fun CapsuleNav(
    left: List<NavTab>,
    right: List<NavTab>,
    current: String?,
    shiftElapsed: String?,
    onSelect: (String) -> Unit,
    onShift: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp), clip = false)
                .clip(RoundedCornerShape(32.dp))
                .background(Mg.colors.surfaceHigh.copy(alpha = 0.96f))
                .border(1.dp, Mg.colors.outline, RoundedCornerShape(32.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            left.forEach { Item(it, current == it.route, Modifier.weight(1f), onSelect) }
            Box(Modifier.weight(1.2f))
            right.forEach { Item(it, current == it.route, Modifier.weight(1f), onSelect) }
        }
        ShiftButton(shiftElapsed, Modifier.offset(y = (-10).dp), onShift)
    }
}

@Composable
private fun Item(tab: NavTab, selected: Boolean, modifier: Modifier, onSelect: (String) -> Unit) {
    val color = if (selected) Mg.colors.brand else Mg.colors.textSecondary
    Column(
        modifier.clickable(role = Role.Tab) { onSelect(tab.route) }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Text(tab.label, color = color, style = Mg.type.caption.copy(fontSize = 11.sp))
    }
}

@Composable
private fun ShiftButton(elapsed: String?, modifier: Modifier, onClick: () -> Unit) {
    val active = elapsed != null
    val fill = if (active) Mg.colors.negative else Mg.colors.brand
    Box(
        modifier.size(68.dp)
            .shadow(12.dp, CircleShape, ambientColor = fill, spotColor = fill)
            .clip(CircleShape)
            .background(fill)
            .border(4.dp, Mg.colors.background, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("■", color = Color.White, style = Mg.type.caption)
                Text(elapsed!!, color = Color.White, style = Mg.type.metricSmall.copy(fontSize = 14.sp))
            }
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Iniciar turno", tint = Mg.colors.onBrand, modifier = Modifier.size(34.dp))
        }
    }
}
