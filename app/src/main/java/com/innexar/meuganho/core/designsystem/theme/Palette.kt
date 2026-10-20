package com.innexar.meuganho.core.designsystem.theme

/**
 * Tokens de cor em ARGB puro. Fonte única para Compose ([MeuGanhoColors]) e para
 * o overlay (Views nativas). Nenhuma tela usa hexadecimal diretamente.
 *
 * Direção: "painel de instrumentos à noite": fundo quase preto azulado, texto
 * de alto contraste, uma cor de marca (menta) para dinheiro positivo.
 */
object Palette {
    // Escuro (prioritário: uso dentro do carro, à noite, com brilho baixo)
    const val Ink = 0xFF0A0E13
    const val Surface = 0xFF111720
    const val SurfaceHigh = 0xFF19212D
    const val Outline = 0xFF263142
    const val TextPrimary = 0xFFEAF0F6
    const val TextSecondary = 0xFF8C99AA

    // Claro (preparado)
    const val Paper = 0xFFF4F6F8
    const val PaperSurface = 0xFFFFFFFF
    const val PaperSurfaceHigh = 0xFFEAEEF2
    const val PaperOutline = 0xFFD3DAE2
    const val InkText = 0xFF0E141B
    const val InkTextSecondary = 0xFF55616F

    // Semânticas (mesmas nos dois temas, contraste verificado sobre Surface)
    const val Mint = 0xFF2EE6A6
    const val MintDeep = 0xFF0F9E6E
    const val Amber = 0xFFFFB547
    const val Coral = 0xFFFF5C6C
    const val Sky = 0xFF5AB8FF

    // Brilhos do fundo (usados com alfa baixo)
    const val GlowMint = 0xFF1FD6A0
    const val GlowSky = 0xFF2F7DFF

    // Plataformas
    const val UberTone = 0xFFEAF0F6
    const val NinetyNineTone = 0xFFFFD23F
}
