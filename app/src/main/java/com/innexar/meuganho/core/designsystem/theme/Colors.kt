package com.innexar.meuganho.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cores semânticas do MeuGanho. Telas pedem "positive", nunca um hexadecimal. */
@Immutable
data class MgColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val brand: Color,
    val onBrand: Color,
    val positive: Color,
    val info: Color,
    val warning: Color,
    val negative: Color,
    val uber: Color,
    val ninetyNine: Color,
)

val DarkMgColors = MgColors(
    background = Color(Palette.Ink),
    surface = Color(Palette.Surface),
    surfaceHigh = Color(Palette.SurfaceHigh),
    outline = Color(Palette.Outline),
    textPrimary = Color(Palette.TextPrimary),
    textSecondary = Color(Palette.TextSecondary),
    brand = Color(Palette.Mint),
    onBrand = Color(Palette.Ink),
    positive = Color(Palette.Mint),
    info = Color(Palette.Sky),
    warning = Color(Palette.Amber),
    negative = Color(Palette.Coral),
    uber = Color(Palette.UberTone),
    ninetyNine = Color(Palette.NinetyNineTone),
)

val LightMgColors = MgColors(
    background = Color(Palette.Paper),
    surface = Color(Palette.PaperSurface),
    surfaceHigh = Color(Palette.PaperSurfaceHigh),
    outline = Color(Palette.PaperOutline),
    textPrimary = Color(Palette.InkText),
    textSecondary = Color(Palette.InkTextSecondary),
    brand = Color(Palette.MintDeep),
    onBrand = Color(Palette.PaperSurface),
    positive = Color(Palette.MintDeep),
    info = Color(0xFF1F7FCC),
    warning = Color(0xFFB36B00),
    negative = Color(0xFFD12F40),
    uber = Color(Palette.InkText),
    ninetyNine = Color(0xFFB38F00),
)

val LocalMgColors = staticCompositionLocalOf { DarkMgColors }

/** Material 3 recebe as mesmas cores para que componentes padrão combinem. */
internal fun MgColors.toMaterial(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = brand, onPrimary = onBrand, secondary = info, onSecondary = onBrand,
        background = background, onBackground = textPrimary, surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceHigh, onSurfaceVariant = textSecondary, surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh, surfaceContainerLow = surface,
        outline = outline, outlineVariant = outline, error = negative, tertiary = warning,
    )
} else {
    lightColorScheme(
        primary = brand, onPrimary = onBrand, secondary = info, onSecondary = onBrand,
        background = background, onBackground = textPrimary, surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceHigh, onSurfaceVariant = textSecondary, surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh, surfaceContainerLow = surface,
        outline = outline, outlineVariant = outline, error = negative, tertiary = warning,
    )
}
