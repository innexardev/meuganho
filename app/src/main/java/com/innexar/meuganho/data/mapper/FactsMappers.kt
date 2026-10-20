package com.innexar.meuganho.data.mapper

import com.innexar.meuganho.data.local.entity.FuelLogEntity
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.domain.financial.FuelFill
import com.innexar.meuganho.domain.stats.ShiftFacts
import com.innexar.meuganho.domain.stats.TripFacts

fun TripEntity.toFacts() = TripFacts(
    platform = platform,
    state = state,
    status = status,
    valueCents = effectiveCents,
    acceptedAt = acceptedAt,
    pickupArrivedAt = pickupArrivedAt,
    startedAt = startedAt,
    endedAt = endedAt,
    pickupMeters = pickupMeters,
    paidMeters = paidMeters,
)

fun ShiftEntity.toFacts() = ShiftFacts(
    startedAt = startedAt,
    endedAt = endedAt,
    pausedMillis = pausedMillis,
    pausedAt = pausedAt,
    startOdometerKm = startOdometerKm,
    endOdometerKm = endOdometerKm,
    gpsMeters = gpsMeters,
)

fun FuelLogEntity.toFill() = FuelFill(odometerKm = odometerKm, liters = liters, fullTank = fullTank)
