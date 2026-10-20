package com.innexar.meuganho.domain.validation

/**
 * Faixa "normal" de R$/km do próprio motorista (percentis 1 e 99).
 *
 * Limites fixos não servem para todas as cidades e categorias. Comparar com o
 * próprio histórico detecta um parser que leu "R$ 285,00" onde era "R$ 28,50".
 */
data class PersonalBaseline(val p01CentsPerKm: Long, val p99CentsPerKm: Long, val sampleSize: Int) {

    /**
     * Dentro da faixa com margem (−30% / +50%): corridas boas de verdade não viram "suspeitas".
     * Um erro de leitura típico (vírgula perdida, km trocado) erra por 10x, não por 50%.
     */
    fun contains(centsPerKm: Long): Boolean =
        centsPerKm >= p01CentsPerKm * 7 / 10 && centsPerKm <= p99CentsPerKm * 3 / 2

    companion object {
        // Com poucas ofertas, os percentis são só o mínimo e o máximo vistos: exigimos 100.
        const val MIN_SAMPLES = 100

        fun fromSamples(centsPerKm: List<Long>): PersonalBaseline? {
            if (centsPerKm.size < MIN_SAMPLES) return null
            val sorted = centsPerKm.sorted()
            return PersonalBaseline(
                p01CentsPerKm = percentile(sorted, 0.01),
                p99CentsPerKm = percentile(sorted, 0.99),
                sampleSize = sorted.size,
            )
        }

        private fun percentile(sorted: List<Long>, p: Double): Long =
            sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)]
    }
}
