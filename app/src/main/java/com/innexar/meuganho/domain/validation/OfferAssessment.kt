package com.innexar.meuganho.domain.validation

import com.innexar.meuganho.domain.model.RideOffer

/**
 * Decisão completa sobre uma oferta capturada: confiança dos campos +
 * validação -> status. Função pura usada pelo gravador e pelos testes de
 * regressão (fixture -> parser -> status esperado).
 */
object OfferAssessment {
    fun assess(offer: RideOffer, baseline: PersonalBaseline?): StatusDecision {
        val issues = OfferValidator.validate(offer, baseline)
        val fieldReasons = offer.criticalFields.flatMap { (name, field) -> field.reasons.map { "$name: $it" } }
        return StatusResolver.resolve(offer.criticalConfidence, fieldReasons, issues)
    }
}
