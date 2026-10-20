package com.innexar.meuganho.domain.financial

import com.innexar.meuganho.domain.model.OfferRating

/**
 * Limites pessoais de classificação, editáveis nas configurações.
 * Uma oferta precisa atingir OS DOIS critérios (R$/km E R$/h) para subir de nível:
 * R$/km alto com trânsito parado ainda é uma corrida ruim.
 */
data class RatingThresholds(
    val excellentCentsPerKm: Long = 200,
    val excellentCentsPerHour: Long = 4500,
    val acceptableCentsPerKm: Long = 160,
    val acceptableCentsPerHour: Long = 3500,
) {
    fun rate(centsPerKm: Long?, centsPerHour: Long?): OfferRating {
        if (centsPerKm == null || centsPerHour == null) return OfferRating.UNCERTAIN
        return when {
            centsPerKm >= excellentCentsPerKm && centsPerHour >= excellentCentsPerHour -> OfferRating.EXCELLENT
            centsPerKm >= acceptableCentsPerKm && centsPerHour >= acceptableCentsPerHour -> OfferRating.ACCEPTABLE
            else -> OfferRating.POOR
        }
    }
}
