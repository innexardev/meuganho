package com.innexar.meuganho.platform.overlay

import com.innexar.meuganho.core.designsystem.theme.Palette
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.data.repository.DayOverview
import com.innexar.meuganho.domain.financial.OfferComparator
import com.innexar.meuganho.domain.model.OfferRating
import com.innexar.meuganho.engine.OverlayOffer

data class OverlayText(
    val title: String,
    val headline: String,
    val headlineColor: Long,
    val line: String,
    val lineColor: Long,
    val detail: String?,
)

/** Textos do overlay. Separado da View para ser testável e fácil de ajustar. */
object OverlayFormatter {

    fun summary(day: DayOverview?, expanded: Boolean): OverlayText {
        val s = day?.summary
        val goal = day?.goalProgress?.let { " · META ${Units.percent(it.ratio)}" }.orEmpty()
        val perHour = s?.centsPerHourOnline?.let { Units.perHour(it) } ?: "—/h"
        val detail = if (expanded && s != null) {
            buildString {
                append(s.centsPerKm?.let { Units.perKm(it) } ?: "—/km")
                append(" · ${s.tripCount} corridas")
                if (s.pendingCount > 0) append("\n+${Money.formatShort(s.pendingCents)} pendente (${s.pendingCount})")
            }
        } else {
            null
        }
        return OverlayText(
            title = "HOJE",
            headline = Money.formatShort(s?.grossCents ?: 0),
            headlineColor = Palette.TextPrimary,
            line = perHour + goal,
            lineColor = Palette.TextSecondary,
            detail = detail,
        )
    }

    /**
     * Várias ofertas ao mesmo tempo (Uber x 99, Radar): a MELHOR em destaque (ver
     * OfferComparator) e as outras em uma linha cada, com a diferença de R$/h para a melhor.
     */
    fun offers(list: List<OverlayOffer>): OverlayText {
        if (list.size == 1) return offer(list.first())
        val bestIdx = OfferComparator.bestIndex(list.map { it.analysis })
        val main = list[bestIdx ?: 0]
        val head = offer(main)
        val platforms = list.map { it.platform }.distinct()
        val bestPerHour = main.analysis.centsPerHour
        val others = list.filter { it !== main }.joinToString("\n") { o ->
            val a = o.analysis
            val verdict = if (o.parserTripped || a.rating == OfferRating.UNCERTAIN) "?" else a.rating.symbol
            val diff = if (bestIdx != null && bestPerHour != null && a.centsPerHour != null) {
                " (−${Money.formatShort(bestPerHour - a.centsPerHour)}/h)"
            } else {
                ""
            }
            "${o.platform.label}: ${Money.formatShort(a.valueCents)} · ${a.centsPerHour?.let { "${Money.formatShort(it)}/h" } ?: "—/h"} · " +
                "${a.centsPerKm?.let { Units.perKm(it) } ?: "—/km"} $verdict$diff"
        }
        return head.copy(
            // A melhor fica no título; a linha grande mantém só R$/h e R$/km (não quebra no painel estreito).
            title = when {
                bestIdx == null -> "${list.size} OFERTAS"
                platforms.size > 1 -> "★ MELHOR: ${main.platform.label.uppercase()} · ${platforms.joinToString(" x ") { it.label }}"
                else -> "★ MELHOR DE ${list.size} · ${main.platform.label.uppercase()}"
            },
            headline = head.headline,
            line = if (bestIdx != null) head.line else "? Nenhuma confiável para comparar",
            detail = listOf(others, head.detail).filter { !it.isNullOrBlank() }.joinToString("\n"),
        )
    }

    /** R$/km e R$/h juntos em destaque; classificação, valor, km, tempo e combustível embaixo. */
    fun offer(o: OverlayOffer): OverlayText {
        val a = o.analysis
        val perKm = a.centsPerKm?.let { Units.perKm(it) } ?: "—/km"
        val perHour = a.centsPerHour?.let { "${Money.formatShort(it)}/h" } ?: "—/h"
        val uncertain = o.parserTripped || a.rating == OfferRating.UNCERTAIN
        val detail = buildString {
            append(Money.format(a.valueCents))
            a.totalMeters?.let { append(" · ${Units.km(it)}") }
            a.totalSeconds?.let { append(" · ${Units.duration(it)}") }
            a.afterFuelCents?.let { append("\nApós combustível ${Money.format(it)}") }
        }
        val (line, color) = when {
            o.parserTripped -> "Análise indisponível: app mudou?" to Palette.Amber
            uncertain -> "? INCERTO · confira na tela" to Palette.Amber
            else -> "${a.rating.symbol} ${a.rating.label}" to colorFor(a.rating)
        }
        return OverlayText(
            title = "${o.platform.label.uppercase()} · OFERTA",
            headline = (if (uncertain) "? " else "") + "$perHour   $perKm",
            headlineColor = if (uncertain) Palette.Amber else colorFor(a.rating),
            line = line,
            lineColor = color,
            detail = detail,
        )
    }

    fun colorFor(rating: OfferRating): Long = when (rating) {
        OfferRating.EXCELLENT -> Palette.Mint
        OfferRating.ACCEPTABLE -> Palette.Sky
        OfferRating.POOR -> Palette.Coral
        OfferRating.UNCERTAIN -> Palette.Amber
    }
}
