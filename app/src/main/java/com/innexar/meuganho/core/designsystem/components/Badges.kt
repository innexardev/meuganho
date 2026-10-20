package com.innexar.meuganho.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.OfferRating
import com.innexar.meuganho.domain.model.Platform

@Composable
private fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Mg.radius.pill)
    Text(
        text = text,
        style = Mg.type.label,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), shape)
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .padding(horizontal = Mg.spacing.s, vertical = Mg.spacing.xs),
    )
}

@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier) =
    Pill(platform.label.uppercase(), platformColor(platform), modifier)

/** Sempre símbolo + texto: a classificação nunca depende só da cor. */
@Composable
fun RatingBadge(rating: OfferRating, modifier: Modifier = Modifier) =
    Pill("${rating.symbol} ${rating.label}", ratingColor(rating), modifier)

@Composable
fun StatusPill(status: DataStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        DataStatus.CONFIRMED, DataStatus.AUTO_ACCEPTED -> Mg.colors.positive
        DataStatus.NEEDS_REVIEW -> Mg.colors.warning
        DataStatus.INVALID -> Mg.colors.negative
        DataStatus.DUPLICATE -> Mg.colors.textSecondary
    }
    val symbol = when (status) {
        DataStatus.CONFIRMED -> "✓✓"
        DataStatus.AUTO_ACCEPTED -> "✓"
        DataStatus.NEEDS_REVIEW -> "?"
        DataStatus.INVALID -> "✕"
        DataStatus.DUPLICATE -> "="
    }
    Pill("$symbol ${status.label.uppercase()}", color, modifier)
}

@Composable
fun platformColor(platform: Platform): Color = when (platform) {
    Platform.UBER -> Mg.colors.uber
    Platform.NINETY_NINE -> Mg.colors.ninetyNine
}

@Composable
fun ratingColor(rating: OfferRating): Color = when (rating) {
    OfferRating.EXCELLENT -> Mg.colors.positive
    OfferRating.ACCEPTABLE -> Mg.colors.info
    OfferRating.POOR -> Mg.colors.negative
    OfferRating.UNCERTAIN -> Mg.colors.warning
}
