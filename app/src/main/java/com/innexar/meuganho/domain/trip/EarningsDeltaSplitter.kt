package com.innexar.meuganho.domain.trip

import kotlin.math.abs

/**
 * O total do dia da plataforma subiu [delta] desde a leitura anterior. A quais corridas pertence?
 * Função pura. [items] = estimativas (valor da oferta) das corridas SEM valor oficial terminadas
 * desde a leitura anterior, mais a em andamento (que acaba de terminar).
 *
 * A 99 só é lida quando o motorista abre a Central de ganhos: uma subida pode somar várias corridas.
 * Real/simulação (30/09/2026): a soma de 2 corridas ia inteira para uma, ou virava corrida inexistente.
 */
object EarningsDeltaSplitter {
    sealed interface Decision {
        /** Uma corrida só: o valor oficial é o dela. */
        data class Single(val index: Int) : Decision
        /** Várias corridas e a soma bate: todas conferidas; [unseenCents] = corrida não vista (para revisão). */
        data class ConfirmSum(val unseenCents: Long?) : Decision
        /** A soma não bate (menos do que o estimado): todas para revisão. */
        data object Mismatch : Decision
        /** Nenhuma corrida vista: uma passou sem ser lida (para revisão). */
        data object Unseen : Decision
        /** Subida menor que uma corrida e nenhuma corrida pendente: recompensa/ajuste (99Multiplica), não corrida. */
        data object Ignore : Decision
    }

    /** Menor corrida possível (tarifa mínima): diferença menor que isso é gorjeta/dinâmica, não corrida. */
    const val MIN_TRIP_CENTS = 550L

    fun decide(delta: Long, items: List<Long?>): Decision {
        if (items.isEmpty()) return if (delta >= MIN_TRIP_CENTS) Decision.Unseen else Decision.Ignore
        if (items.size == 1) {
            val est = items[0] ?: return Decision.Single(0)
            val extra = delta - est
            // Bem acima da oferta e com sobra de uma corrida inteira: esta + uma não vista.
            return if (extra > est / 2 + 300 && extra >= MIN_TRIP_CENTS) Decision.ConfirmSum(extra) else Decision.Single(0)
        }
        if (items.any { it == null }) return Decision.Mismatch
        val sum = items.sumOf { it!! }
        val diff = delta - sum
        val tolerance = maxOf(300L, sum * 3 / 100)
        return when {
            abs(diff) <= tolerance -> Decision.ConfirmSum(null)
            diff >= MIN_TRIP_CENTS -> Decision.ConfirmSum(diff)
            diff > 0 -> Decision.ConfirmSum(null) // gorjetas/dinâmica somadas
            else -> Decision.Mismatch
        }
    }
}
