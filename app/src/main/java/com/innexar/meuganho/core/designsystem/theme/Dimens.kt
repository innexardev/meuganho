package com.innexar.meuganho.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Escala de espaçamento em múltiplos de 4. */
@Immutable
data class MgSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Área de toque mínima: maior que os 48dp do Material, pensando em uso no carro. */
    val touch: Dp = 56.dp,
)

@Immutable
data class MgRadius(val s: Dp = 8.dp, val m: Dp = 14.dp, val l: Dp = 20.dp, val pill: Dp = 100.dp)

val LocalMgSpacing = staticCompositionLocalOf { MgSpacing() }
val LocalMgRadius = staticCompositionLocalOf { MgRadius() }
