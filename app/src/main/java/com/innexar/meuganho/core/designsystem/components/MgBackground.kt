package com.innexar.meuganho.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.designsystem.theme.Palette

/**
 * Fundo "cockpit à noite": base escura com dois brilhos suaves (menta no alto, azul embaixo).
 * Desenhado uma vez por composição, sem imagem e sem custo contínuo.
 */
@Composable
fun MgBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val base = Mg.colors.background
    Box(
        modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(Palette.GlowMint).copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.02f),
                        radius = size.width * 0.95f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(Palette.GlowSky).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.85f),
                        radius = size.width * 1.1f,
                    )
                )
            },
        content = content,
    )
}
