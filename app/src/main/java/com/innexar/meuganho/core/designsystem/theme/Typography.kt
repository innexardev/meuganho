package com.innexar.meuganho.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.innexar.meuganho.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia: fonte do sistema (zero KB no APK, carregamento instantâneo) com
 * números TABULARES em todos os estilos numéricos, para que valores não
 * "dancem" quando atualizam.
 */
@Immutable
data class MgType(
    val hero: TextStyle,
    val metric: TextStyle,
    val metricSmall: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private const val TABULAR = "tnum"

/**
 * Space Grotesk (SIL OFL 1.1, embutida em res/font): traço técnico de "mostrador" para
 * números e títulos. Fonte variável: um arquivo só, pesos por eixo wght.
 */
@OptIn(ExperimentalTextApi::class)
val Grotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val DefaultMgType = MgType(
    hero = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TABULAR),
    metric = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR),
    metricSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, fontFeatureSettings = TABULAR),
    title = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR),
)

val LocalMgType = staticCompositionLocalOf { DefaultMgType }

internal fun MgType.toMaterial() = Typography(
    headlineLarge = hero,
    titleLarge = title,
    titleMedium = title.copy(fontSize = 17.sp),
    bodyLarge = body,
    bodyMedium = body.copy(fontSize = 14.sp),
    labelLarge = label.copy(fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = label,
    bodySmall = caption,
)
