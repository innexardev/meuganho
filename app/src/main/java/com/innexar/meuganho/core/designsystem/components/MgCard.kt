package com.innexar.meuganho.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.designsystem.theme.Palette

/**
 * Superfície base, levemente translúcida sobre o fundo com brilho.
 * [glow] = borda em degradê menta→azul, reservada aos blocos principais de cada tela.
 */
@Composable
fun MgCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Mg.radius.l)
    val border = when {
        accent != null -> Brush.linearGradient(listOf(accent, accent))
        glow -> Brush.linearGradient(listOf(Color(Palette.Mint).copy(alpha = 0.7f), Color(Palette.Sky).copy(alpha = 0.35f), Mg.colors.outline))
        else -> Brush.linearGradient(listOf(Mg.colors.outline, Mg.colors.outline))
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(Mg.colors.surface.copy(alpha = 0.82f))
            .border(1.dp, border, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(Mg.spacing.l),
        content = content,
    )
}
