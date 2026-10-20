package com.innexar.meuganho.screens

import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.data.local.entity.GoalEntity
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.local.entity.VehicleEntity
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.FuelType
import com.innexar.meuganho.domain.model.GoalKind
import com.innexar.meuganho.domain.model.GoalPeriod
import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import kotlinx.coroutines.runBlocking

/** Dados parecidos com um turno real (valores das amostras de 28/09/2026). */
object SeedData {
    private const val MIN = 60_000L

    fun fill(c: AppContainer, now: Long = System.currentTimeMillis()) = runBlocking {
        val db = c.database
        db.vehicleDao().upsert(VehicleEntity(manufacturer = "Chevrolet", model = "Onix", version = "1.0", year = 2022,
            fuelType = FuelType.FLEX, odometerKm = 48_210, cityKmPerLiter = 11.5, highwayKmPerLiter = 14.0,
            averageKmPerLiter = 12.5, maintenanceCentsPerKm = 12.0, depreciationCentsPerKm = 15.0, createdAt = now))
        // Tudo dentro de "hoje", mesmo se o teste rodar logo após a meia-noite.
        val startOfDay = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val shiftStart = maxOf(now - 4 * 60 * MIN, startOfDay + MIN)
        val spacing = ((now - shiftStart) / 8).coerceAtLeast(MIN)
        val shiftId = db.shiftDao().insert(ShiftEntity(vehicleId = 1, startedAt = shiftStart, startOdometerKm = 48_120,
            platforms = "UBER,NINETY_NINE", fuelPriceCentsPerLiter = 619, emptyMeters = 21_000, pickupMeters = 14_000, paidMeters = 55_000))
        db.goalDao().upsert(GoalEntity(period = GoalPeriod.DAILY, kind = GoalKind.GROSS, targetCents = 35_000))

        val trips = listOf(
            Triple(Platform.UBER, 2679L, 15_600), Triple(Platform.NINETY_NINE, 1210L, 9_700), Triple(Platform.UBER, 4083L, 25_600),
            Triple(Platform.UBER, 746L, 4_100), Triple(Platform.NINETY_NINE, 2260L, 11_000), Triple(Platform.UBER, 3654L, 26_200),
        )
        trips.forEachIndexed { i, (platform, cents, meters) ->
            val accepted = shiftStart + i * spacing
            db.tripDao().insert(TripEntity(shiftId = shiftId, offerId = null, platform = platform, state = TripState.COMPLETED,
                status = DataStatus.AUTO_ACCEPTED, acceptedAt = accepted, pickupArrivedAt = accepted + spacing / 6,
                startedAt = accepted + spacing / 5, endedAt = accepted + spacing * 4 / 5, estimatedCents = cents, finalCents = cents,
                valueSource = ValueSource.COMPLETION_SCREEN, estTripMeters = meters, estPickupMeters = 2_500))
        }
        // Uma corrida pendente de revisão e uma oferta duvidosa.
        db.tripDao().insert(TripEntity(shiftId = shiftId, offerId = null, platform = Platform.UBER, state = TripState.COMPLETED,
            status = DataStatus.NEEDS_REVIEW, acceptedAt = maxOf(now - 20 * MIN, startOfDay), startedAt = maxOf(now - 15 * MIN, startOfDay), endedAt = now,
            estimatedCents = 2850, valueSource = ValueSource.OFFER_ESTIMATE, reasons = "Oferta duvidosa e sem tela final"))
        listOf(2679L to DataStatus.AUTO_ACCEPTED, 16_481L to DataStatus.NEEDS_REVIEW, 810L to DataStatus.AUTO_ACCEPTED).forEachIndexed { i, (v, st) ->
            db.offerDao().insert(OfferEntity(shiftId = shiftId, platform = if (i == 2) Platform.NINETY_NINE else Platform.UBER,
                detectedAt = now - (10 - i) * MIN, fingerprint = "f$i", offeredCents = v, surgeCents = null, category = "UberX",
                pickupMeters = 2_200, pickupSeconds = 360, tripMeters = 13_400, tripSeconds = 1_260, fieldConfidence = "",
                confidence = Confidence.HIGH, status = st, reasons = if (st == DataStatus.NEEDS_REVIEW) "km da viagem: identificado pela ordem" else "",
                outcome = OfferOutcome.NOT_ACCEPTED, parserVersion = "uber-v1", sourceAppVersion = 1, channel = CaptureChannel.ACCESSIBILITY))
        }
        c.settingsRepository.update { it.copy(onboardingDone = true) }
    }
}
