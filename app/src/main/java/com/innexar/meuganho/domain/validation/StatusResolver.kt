package com.innexar.meuganho.domain.validation

import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus

data class StatusDecision(val status: DataStatus, val reasons: List<String>)

/**
 * Combina os dois eixos independentes:
 * - confiança do parser (leu o campo certo?);
 * - validação (o valor faz sentido?).
 *
 * Qualquer problema HARD -> INVALID. Confiança baixa em campo crítico ou
 * problema SOFT -> NEEDS_REVIEW. Só o caso limpo vira AUTO_ACCEPTED.
 */
object StatusResolver {

    fun resolve(
        criticalConfidence: Confidence,
        fieldReasons: List<String>,
        issues: List<ValidationIssue>,
    ): StatusDecision {
        val reasons = fieldReasons + issues.map { it.message }
        val status = when {
            issues.any { it.severity == Severity.HARD } -> DataStatus.INVALID
            criticalConfidence == Confidence.LOW -> DataStatus.NEEDS_REVIEW
            issues.isNotEmpty() -> DataStatus.NEEDS_REVIEW
            else -> DataStatus.AUTO_ACCEPTED
        }
        return StatusDecision(status, reasons.distinct())
    }
}
