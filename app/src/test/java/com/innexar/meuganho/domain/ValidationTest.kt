package com.innexar.meuganho.domain

import com.innexar.meuganho.domain.model.Captured
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.FieldSource
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.RideOffer
import com.innexar.meuganho.domain.validation.OfferAssessment
import com.innexar.meuganho.domain.validation.PersonalBaseline
import com.innexar.meuganho.domain.validation.TripValuePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    private fun high(v: Int) = Captured(v, FieldSource.TEXT_ANCHOR, Confidence.HIGH)

    private fun offer(
        cents: Long = 2870, pickupM: Int = 1800, pickupS: Int = 240, tripM: Int = 12_400, tripS: Int = 1440,
        priceConfidence: Confidence = Confidence.HIGH,
    ) = RideOffer(
        platform = Platform.UBER, detectedAt = 0,
        offeredCents = Captured(cents, FieldSource.TEXT_ANCHOR, priceConfidence),
        pickupMeters = high(pickupM), pickupSeconds = high(pickupS), tripMeters = high(tripM), tripSeconds = high(tripS),
        parserVersion = "test", sourceAppVersion = 0,
    )

    @Test fun `oferta limpa é aceita`() = assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(offer(), null).status)

    @Test fun `valor negativo é inválido`() = assertEquals(DataStatus.INVALID, OfferAssessment.assess(offer(cents = -100), null).status)

    @Test fun `tempo zero vai para revisão`() = assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(offer(tripS = 0), null).status)

    @Test fun `distância absurda vai para revisão`() =
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(offer(tripM = 400_000, tripS = 14_400), null).status)

    @Test fun `velocidade impossível vai para revisão`() =
        // 12,4 km em 2 min = 372 km/h: km e min trocados ou lidos errado.
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(offer(tripS = 120), null).status)

    @Test fun `confiança baixa no preço vai para revisão mesmo com valor plausível`() =
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(offer(priceConfidence = Confidence.LOW), null).status)

    @Test fun `fora da faixa pessoal vai para revisão`() {
        val baseline = PersonalBaseline(p01CentsPerKm = 120, p99CentsPerKm = 300, sampleSize = 100)
        // R$ 9,00 em 14,2 km = R$ 0,63/km: abaixo de 70% do seu mínimo -> leitura suspeita.
        assertEquals(DataStatus.NEEDS_REVIEW, OfferAssessment.assess(offer(cents = 900), baseline).status)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(offer(), baseline).status)
        // Oferta muito boa (R$ 4,00/km) confirmada pelo app não vira suspeita (caso real de 28/09).
        val great = offer(cents = 5680).copy(confirmedByPlatform = true)
        assertEquals(DataStatus.AUTO_ACCEPTED, OfferAssessment.assess(great, baseline).status)
    }

    @Test fun `baseline só existe com amostras suficientes`() {
        assertNull(PersonalBaseline.fromSamples(List(50) { 200L }))
        val b = PersonalBaseline.fromSamples((100L..199L).toList())
        assertNotNull(b)
        assertEquals(100L, b!!.p01CentsPerKm)
        assertEquals(198L, b.p99CentsPerKm)
    }

    @Test fun `política de valor final da corrida`() {
        assertEquals(DataStatus.AUTO_ACCEPTED, TripValuePolicy.resolve(DataStatus.AUTO_ACCEPTED, 2000, 2300).status)
        // Final muito diferente da oferta: um dos dois foi lido errado.
        assertEquals(DataStatus.NEEDS_REVIEW, TripValuePolicy.resolve(DataStatus.AUTO_ACCEPTED, 2000, 20_000).status)
        // Sem tela final, oferta confiável: usa a estimativa.
        assertEquals(DataStatus.AUTO_ACCEPTED, TripValuePolicy.resolve(DataStatus.AUTO_ACCEPTED, 2000, null).status)
        // Sem tela final e oferta duvidosa: revisão.
        assertEquals(DataStatus.NEEDS_REVIEW, TripValuePolicy.resolve(DataStatus.NEEDS_REVIEW, 2000, null).status)
        assertEquals(DataStatus.NEEDS_REVIEW, TripValuePolicy.resolve(null, null, null).status)
    }
}
