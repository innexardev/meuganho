package com.innexar.meuganho.domain

import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.financial.OfferAnalyzer
import com.innexar.meuganho.domain.financial.RatingThresholds
import com.innexar.meuganho.domain.model.OfferRating
import org.junit.Assert.assertEquals
import org.junit.Test

class OfferAnalyzerTest {
    private val cost = CostModel(
        fuelPriceCentsPerLiter = 600, kmPerLiter = 10.0,
        maintenanceCentsPerKm = 10.0, depreciationCentsPerKm = 15.0, otherCentsPerKm = 0.0,
    )
    private val thresholds = RatingThresholds()

    // Exemplo da especificação: R$ 28,70; 1,8 km/4 min + 12,4 km/24 min.
    @Test fun `calcula exemplo da especificação`() {
        val a = OfferAnalyzer.analyze(2870, 14_200, 28 * 60L, reliable = true, costModel = cost, thresholds = thresholds)
        assertEquals(202L, a.centsPerKm) // R$ 2,02/km
        assertEquals(6150L, a.centsPerHour) // R$ 61,50/h
        assertEquals(852L, a.fuelCostCents) // 14,2 km / 10 km/l * R$ 6,00
        assertEquals(2870L - 852L, a.afterFuelCents)
        assertEquals(852L + 355L, a.operationalCostCents) // + R$ 0,25/km
        assertEquals(OfferRating.EXCELLENT, a.rating)
    }

    @Test fun `precisa dos dois critérios para subir de nível`() {
        // R$/km excelente, mas R$/h fraco (trânsito): não pode ser EXCELENTE.
        assertEquals(OfferRating.ACCEPTABLE, thresholds.rate(centsPerKm = 250, centsPerHour = 4000))
        assertEquals(OfferRating.POOR, thresholds.rate(centsPerKm = 250, centsPerHour = 2000))
        assertEquals(OfferRating.POOR, thresholds.rate(centsPerKm = 120, centsPerHour = 9000))
    }

    @Test fun `dado não confiável nunca recebe veredito`() {
        val a = OfferAnalyzer.analyze(2870, 14_200, 1680, reliable = false, costModel = cost, thresholds = thresholds)
        assertEquals(OfferRating.UNCERTAIN, a.rating)
        assertEquals(202L, a.centsPerKm) // Números continuam visíveis, com "?".
    }

    @Test fun `sem distância não há taxa por km`() {
        val a = OfferAnalyzer.analyze(2000, null, 600, reliable = true, costModel = cost, thresholds = thresholds)
        assertEquals(null, a.centsPerKm)
        assertEquals(OfferRating.UNCERTAIN, a.rating)
    }
}
