package com.innexar.meuganho.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Tema do app. Escuro por padrão: uso principal é no carro, muitas vezes à noite.
 * O tema claro está pronto e é ativado passando dark = false.
 */
@Composable
fun MeuGanhoTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (dark) DarkMgColors else LightMgColors
    CompositionLocalProvider(
        LocalMgColors provides colors,
        LocalMgType provides DefaultMgType,
        LocalMgSpacing provides MgSpacing(),
        LocalMgRadius provides MgRadius(),
    ) {
        MaterialTheme(colorScheme = colors.toMaterial(dark), typography = DefaultMgType.toMaterial(), content = content)
    }
}

/** Acesso curto aos tokens: `Mg.colors.positive`, `Mg.spacing.l`. */
object Mg {
    val colors: MgColors @Composable @ReadOnlyComposable get() = LocalMgColors.current
    val type: MgType @Composable @ReadOnlyComposable get() = LocalMgType.current
    val spacing: MgSpacing @Composable @ReadOnlyComposable get() = LocalMgSpacing.current
    val radius: MgRadius @Composable @ReadOnlyComposable get() = LocalMgRadius.current
}
