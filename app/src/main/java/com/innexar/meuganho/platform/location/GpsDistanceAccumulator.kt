package com.innexar.meuganho.platform.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val timeMillis: Long)

/**
 * Soma a distância percorrida por segmento (vazio / busca / pago) SEM guardar pontos.
 *
 * Filtros:
 * - precisão ruim (> 35 m) é descartada;
 * - saltos impossíveis (> 55 m/s ≈ 200 km/h) são descartados;
 * - deslocamentos < 8 m não movem a âncora, o que evita somar o "tremor" do GPS parado.
 *
 * Acumula em memória e só entrega (grava no banco) a cada ~300 m ou quando o
 * segmento muda, reduzindo escritas a poucas dezenas por turno.
 */
class GpsDistanceAccumulator<K>(
    initialKey: K,
    private val flushEveryMeters: Double = 300.0,
    private val onFlush: (K, Long) -> Unit,
) {
    private var anchor: GpsFix? = null
    private var pendingMeters = 0.0
    private var pendingKey: K = initialKey

    /**
     * [key] identifica para onde vão os metros (segmento + corrida). Quando muda,
     * o acumulado anterior é entregue com a chave antiga: os últimos metros de uma
     * corrida recém-concluída continuam atribuídos a ela.
     */
    fun onFix(fix: GpsFix, key: K) {
        if (fix.accuracyMeters > MAX_ACCURACY_M) return
        if (key != pendingKey) {
            flush()
            pendingKey = key
        }
        val previous = anchor
        if (previous == null) {
            anchor = fix
            return
        }
        val meters = haversineMeters(previous, fix)
        val seconds = (fix.timeMillis - previous.timeMillis) / 1000.0
        if (seconds <= 0) return
        if (meters / seconds > MAX_SPEED_MPS) return
        if (meters < MIN_STEP_M) return
        anchor = fix
        pendingMeters += meters
        if (pendingMeters >= flushEveryMeters) flush()
    }

    fun flush() {
        val meters = pendingMeters.roundToLong()
        pendingMeters = 0.0
        if (meters > 0) onFlush(pendingKey, meters)
    }

    companion object {
        private const val MAX_ACCURACY_M = 35f
        private const val MAX_SPEED_MPS = 55.0
        private const val MIN_STEP_M = 8.0
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun haversineMeters(a: GpsFix, b: GpsFix): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(h))
        }
    }
}
