package com.innexar.meuganho.domain.validation

import com.innexar.meuganho.domain.financial.Rates
import com.innexar.meuganho.domain.model.RideOffer

/**
 * Validação em camadas de uma oferta capturada:
 * 1. limites absolutos (impossível ou improvável);
 * 2. coerência interna (velocidade implícita km ÷ tempo);
 * 3. plausibilidade pessoal (percentis do histórico).
 *
 * A confiança do PARSER é tratada à parte, em [StatusResolver]: um valor lido com
 * 100% de certeza ainda pode ser implausível (ex.: banner de promoção).
 */
object OfferValidator {

    // Faixas deliberadamente largas: o objetivo é pegar erro de leitura, não julgar a oferta.
    private const val MIN_OFFER_CENTS = 300L
    private const val MAX_OFFER_CENTS = 50_000L
    private const val MAX_TRIP_METERS = 150_000
    private const val MAX_PICKUP_METERS = 25_000
    private const val MIN_CENTS_PER_KM = 50L
    private const val MAX_CENTS_PER_KM = 1_000L
    private const val MIN_SPEED_KMH = 4.0
    private const val MAX_SPEED_KMH = 100.0
    private const val MIN_METERS_FOR_SPEED_CHECK = 1_000

    fun validate(offer: RideOffer, baseline: PersonalBaseline?): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val value = offer.offeredCents.value

        if (value == null) {
            issues += hard("NO_VALUE", "Valor da oferta não encontrado")
        } else if (value <= 0) {
            issues += hard("NON_POSITIVE_VALUE", "Valor zero ou negativo")
        } else if (value < MIN_OFFER_CENTS || value > MAX_OFFER_CENTS) {
            issues += soft("VALUE_OUT_OF_RANGE", "Valor fora da faixa esperada")
        }

        val trip = offer.tripMeters.value
        val tripTime = offer.tripSeconds.value
        val pickup = offer.pickupMeters.value
        if ((trip ?: 0) < 0 || (pickup ?: 0) < 0 || (tripTime ?: 0) < 0 || (offer.pickupSeconds.value ?: 0) < 0) {
            issues += hard("NEGATIVE_FIELD", "Distância ou tempo negativo")
        }
        if (trip == 0 || tripTime == 0) issues += soft("ZERO_TRIP", "Distância ou tempo da viagem zerado")
        if (trip != null && trip > MAX_TRIP_METERS) issues += soft("TRIP_TOO_LONG", "Viagem acima de 150 km")
        if (pickup != null && pickup > MAX_PICKUP_METERS) issues += soft("PICKUP_TOO_LONG", "Busca acima de 25 km")

        speedIssue(trip, tripTime, "viagem")?.let { issues += it }
        speedIssue(pickup, offer.pickupSeconds.value, "busca")?.let { issues += it }

        val total = offer.totalMeters
        if (value != null && value > 0 && total != null && total > 0) {
            val perKm = Rates.centsPerKm(value, total.toLong())!!
            if (perKm < MIN_CENTS_PER_KM || perKm > MAX_CENTS_PER_KM) {
                issues += soft("ABSURD_RATE", "R$/km fora do possível")
            } else if (!offer.confirmedByPlatform && baseline != null && !baseline.contains(perKm)) {
                // A faixa pessoal pega leitura errada; se o próprio app confirmou os números, não há o que pegar.
                issues += soft("OUTSIDE_PERSONAL_RANGE", "R$/km fora do seu histórico")
            }
        }

        offer.warnings.forEach { issues += soft("PARSER_WARNING", it) }
        return issues
    }

    private fun speedIssue(meters: Int?, seconds: Int?, label: String): ValidationIssue? {
        if (meters == null || seconds == null || seconds <= 0 || meters < MIN_METERS_FOR_SPEED_CHECK) return null
        val kmh = meters / 1000.0 / (seconds / 3600.0)
        return if (kmh < MIN_SPEED_KMH || kmh > MAX_SPEED_KMH) {
            soft("IMPLAUSIBLE_SPEED", "Velocidade implícita da $label improvável (${kmh.toInt()} km/h)")
        } else {
            null
        }
    }

    private fun hard(code: String, message: String) = ValidationIssue(Severity.HARD, code, message)
    private fun soft(code: String, message: String) = ValidationIssue(Severity.SOFT, code, message)
}
