package com.innexar.meuganho.domain.financial

data class GoalProgress(
    val currentCents: Long,
    val targetCents: Long,
    val ratio: Double,
    val remainingCents: Long,
    /** Tempo estimado para atingir a meta no ritmo atual; null se não há ritmo. */
    val estimatedSecondsToGoal: Long?,
) {
    val reached: Boolean get() = remainingCents == 0L
}

object GoalCalculator {
    fun progress(currentCents: Long, targetCents: Long, centsPerHour: Long?): GoalProgress {
        val remaining = (targetCents - currentCents).coerceAtLeast(0)
        val eta = when {
            remaining == 0L -> 0L
            centsPerHour == null || centsPerHour <= 0 -> null
            else -> remaining * 3600 / centsPerHour
        }
        return GoalProgress(
            currentCents = currentCents,
            targetCents = targetCents,
            ratio = if (targetCents > 0) currentCents.toDouble() / targetCents else 0.0,
            remainingCents = remaining,
            estimatedSecondsToGoal = eta,
        )
    }
}
