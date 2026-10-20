package com.innexar.meuganho.domain.financial

data class FuelFill(
    val odometerKm: Int,
    val liters: Double,
    val fullTank: Boolean,
)

data class ConsumptionInterval(val fromKm: Int, val toKm: Int, val liters: Double) {
    val kmPerLiter: Double get() = (toKm - fromKm) / liters
}

/**
 * Consumo real pelo método tanque-cheio -> tanque-cheio.
 *
 * Entre dois abastecimentos de tanque cheio, os litros consumidos são a soma de
 * tudo que foi colocado DEPOIS do primeiro, incluindo parciais, até o segundo.
 * Abastecimentos parciais sozinhos não permitem medir consumo.
 */
object ConsumptionCalculator {

    const val MIN_INTERVALS_FOR_TRUST = 2

    fun intervals(fills: List<FuelFill>): List<ConsumptionInterval> {
        val sorted = fills.sortedBy { it.odometerKm }
        val result = mutableListOf<ConsumptionInterval>()
        var anchor: FuelFill? = null
        var litersSinceAnchor = 0.0
        for (fill in sorted) {
            if (anchor != null) litersSinceAnchor += fill.liters
            if (fill.fullTank) {
                if (anchor != null && fill.odometerKm > anchor.odometerKm && litersSinceAnchor > 0) {
                    result += ConsumptionInterval(anchor.odometerKm, fill.odometerKm, litersSinceAnchor)
                }
                anchor = fill
                litersSinceAnchor = 0.0
            }
        }
        return result
    }

    /** Média ponderada pelos km (não média das médias). Null sem histórico suficiente. */
    fun observedKmPerLiter(fills: List<FuelFill>): Double? {
        val intervals = intervals(fills)
        if (intervals.size < MIN_INTERVALS_FOR_TRUST) return null
        val km = intervals.sumOf { it.toKm - it.fromKm }
        val liters = intervals.sumOf { it.liters }
        return if (liters > 0) km / liters else null
    }
}
