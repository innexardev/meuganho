package com.innexar.meuganho.domain.validation

/**
 * HARD: o dado é impossível e vira INVALID.
 * SOFT: o dado é possível, mas improvável, e vai para revisão.
 */
enum class Severity { HARD, SOFT }

data class ValidationIssue(val severity: Severity, val code: String, val message: String)
