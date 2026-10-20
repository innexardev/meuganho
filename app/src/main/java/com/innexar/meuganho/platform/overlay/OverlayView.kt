package com.innexar.meuganho.platform.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.innexar.meuganho.core.designsystem.theme.Palette

/**
 * Painel flutuante em Views nativas: sem Compose aqui porque o overlay vive
 * dentro do serviço o dia todo e precisa de custo mínimo de memória.
 * Largura máxima limitada: painel largo não sobra espaço para arrastar para os lados.
 */
@SuppressLint("ViewConstructor")
class OverlayView(context: Context) : LinearLayout(context) {

    private val title = text(11f, Palette.TextSecondary, bold = true)
    /** "✕" no canto: recolhe o painel para a bolinha. */
    val close: TextView = text(16f, Palette.TextSecondary, bold = true).apply {
        text = "✕"
        gravity = Gravity.CENTER
        val pad = dp(6)
        setPadding(pad * 2, pad, pad, pad)
        contentDescription = "Recolher painel"
    }
    private val headline = text(22f, Palette.TextPrimary, bold = true)
    private val line = text(14f, Palette.TextSecondary, bold = true)
    private val detail = text(12f, Palette.TextSecondary, bold = false)

    init {
        orientation = VERTICAL
        val pad = dp(12)
        setPadding(pad, dp(4), dp(4), dp(10))
        background = rounded(dp(16).toFloat())
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(close)
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        listOf(headline, line, detail).forEach {
            addView(it)
            it.setPadding(0, 0, dp(8), 0)
        }
        contentDescription = "Painel MeuGanho"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = dp(MAX_WIDTH_DP)
        val spec = MeasureSpec.makeMeasureSpec(minOf(max, MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: max), MeasureSpec.AT_MOST)
        super.onMeasure(spec, heightMeasureSpec)
    }

    fun render(t: OverlayText) {
        title.text = t.title
        headline.text = t.headline
        headline.setTextColor(t.headlineColor.toInt())
        line.text = t.line
        line.setTextColor(t.lineColor.toInt())
        detail.text = t.detail.orEmpty()
        detail.visibility = if (t.detail.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun text(sizeSp: Float, color: Long, bold: Boolean) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        // Números tabulares: dígitos não "dançam" quando os valores mudam.
        fontFeatureSettings = "tnum"
        includeFontPadding = false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MAX_WIDTH_DP = 300
    }
}

/** Fundo padrão do painel e da bolinha. */
internal fun View.rounded(radius: Float, fill: Long = Palette.Surface, alpha: Long = 0xF0000000) = GradientDrawable().apply {
    cornerRadius = radius
    setColor((fill and 0x00FFFFFF or alpha).toInt())
    setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), Palette.Outline.toInt())
}

/** Bolinha recolhida: mostra o R$/h do dia; toque abre o painel. */
@SuppressLint("ViewConstructor", "AppCompatCustomView") // TextView puro: o painel não usa AppCompat (leveza)
class OverlayBubble(context: Context) : TextView(context) {
    init {
        val size = (56 * resources.displayMetrics.density).toInt()
        minWidth = size
        minHeight = size
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Palette.Mint.toInt())
        fontFeatureSettings = "tnum"
        val pad = (6 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        background = rounded(size / 2f)
        contentDescription = "Abrir painel MeuGanho"
    }
}

/** Alvo "✕" no rodapé que aparece enquanto se arrasta: soltar em cima fecha o painel. */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class OverlayCloseTarget(context: Context) : TextView(context) {
    init {
        val size = (64 * resources.displayMetrics.density).toInt()
        minWidth = size
        minHeight = size
        gravity = Gravity.CENTER
        text = "✕"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.DEFAULT_BOLD
        highlight(false)
        contentDescription = "Soltar aqui para fechar"
    }

    fun highlight(on: Boolean) {
        setTextColor((if (on) Palette.Ink else Palette.TextPrimary).toInt())
        background = rounded(minWidth / 2f, fill = if (on) Palette.Coral else Palette.SurfaceHigh)
    }
}
