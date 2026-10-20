package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Captured
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.RideOffer
import kotlin.math.abs
import kotlin.math.max

/**
 * Prova cruzada com o próprio app: o Uber exibe "R$1,58/km aprox." calculado
 * sobre o km TOTAL (busca + viagem). Se valor ÷ nossos km dá o mesmo número,
 * o preço e as duas distâncias foram lidos certo, e a confiança sobe.
 *
 * A soma não prova qual trecho é a busca e qual é a viagem; por isso esses
 * campos sobem só até MEDIUM (a ordem na tela decide), e o preço vai a HIGH.
 * Se não bate, algo foi lido errado: vira alerta e a oferta vai para revisão.
 */
object PlatformRateCheck {
    private const val RELATIVE_TOLERANCE = 0.03
    private const val ABSOLUTE_TOLERANCE_CENTS = 2.0

    fun apply(offer: RideOffer, shownCentsPerKm: Long?): RideOffer {
        if (shownCentsPerKm == null || shownCentsPerKm <= 0) return offer
        val value = offer.offeredCents.value ?: return offer
        val meters = offer.totalMeters?.takeIf { it > 0 } ?: return offer
        val computed = value * 1000.0 / meters
        val matches = abs(computed - shownCentsPerKm) <= max(ABSOLUTE_TOLERANCE_CENTS, shownCentsPerKm * RELATIVE_TOLERANCE)
        if (!matches) {
            return offer.copy(warnings = offer.warnings +
                "R$/km calculado (${computed.toLong()}) difere do exibido pelo app ($shownCentsPerKm)")
        }
        val reason = "confere com o R$/km exibido pelo app"
        return offer.copy(
            offeredCents = offer.offeredCents.atLeast(Confidence.HIGH, reason),
            pickupMeters = offer.pickupMeters.atLeast(Confidence.MEDIUM, reason),
            tripMeters = offer.tripMeters.atLeast(Confidence.MEDIUM, reason),
            // Os tempos não entram na conta do app, mas estão na mesma linha que as distâncias confirmadas.
            pickupSeconds = offer.pickupSeconds.atLeast(Confidence.MEDIUM, reason),
            tripSeconds = offer.tripSeconds.atLeast(Confidence.MEDIUM, reason),
            // O alerta "identificadas pela ordem" deixa de valer: os números foram confirmados.
            warnings = offer.warnings.filterNot { it.contains("pela ordem") },
            confirmedByPlatform = true,
        )
    }

    private fun <T> Captured<T>.atLeast(level: Confidence, reason: String): Captured<T> =
        if (value == null || confidence >= level) this
        else copy(confidence = level, reasons = reasons.filterNot { it.contains("não encontrado") } + reason)
}
