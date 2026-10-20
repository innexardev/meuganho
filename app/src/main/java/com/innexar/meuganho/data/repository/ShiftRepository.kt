package com.innexar.meuganho.data.repository

import com.innexar.meuganho.data.local.dao.ShiftDao
import com.innexar.meuganho.data.local.dao.TripDao
import com.innexar.meuganho.data.local.dao.VehicleDao
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.domain.model.DriveSegment
import com.innexar.meuganho.domain.model.Platform

/** Regras de ciclo de vida do turno (abrir, pausar, encerrar) e gravação de km do GPS. */
class ShiftRepository(
    private val shiftDao: ShiftDao,
    private val tripDao: TripDao,
    private val vehicleDao: VehicleDao,
) {
    val openShift = shiftDao.observeOpen()

    suspend fun start(platforms: Set<Platform>, startOdometerKm: Int?, fuelPriceCents: Long, now: Long): Long {
        shiftDao.open()?.let { return it.id } // Nunca dois turnos abertos.
        val vehicle = vehicleDao.active()
        return shiftDao.insert(
            ShiftEntity(
                vehicleId = vehicle?.id,
                startedAt = now,
                startOdometerKm = startOdometerKm,
                platforms = platforms.joinToString(",") { it.name },
                fuelPriceCentsPerLiter = fuelPriceCents,
            )
        )
    }

    suspend fun togglePause(now: Long) {
        val shift = shiftDao.open() ?: return
        val updated = if (shift.pausedAt != null) {
            shift.copy(pausedMillis = shift.pausedMillis + (now - shift.pausedAt), pausedAt = null)
        } else {
            shift.copy(pausedAt = now)
        }
        shiftDao.update(updated)
    }

    suspend fun end(endOdometerKm: Int?, notes: String?, now: Long) {
        val shift = shiftDao.open() ?: return
        val pausedExtra = shift.pausedAt?.let { now - it } ?: 0
        shiftDao.update(
            shift.copy(
                endedAt = now,
                endOdometerKm = endOdometerKm,
                pausedMillis = shift.pausedMillis + pausedExtra,
                pausedAt = null,
                notes = notes,
            )
        )
        if (endOdometerKm != null && shift.vehicleId != null) vehicleDao.raiseOdometer(shift.vehicleId, endOdometerKm)
    }

    /** Grava km agregados pelo GPS no turno e, se houver, na corrida ativa. */
    suspend fun addDistance(shiftId: Long, tripId: Long?, segment: DriveSegment, meters: Long) {
        when (segment) {
            DriveSegment.EMPTY -> shiftDao.addEmptyMeters(shiftId, meters)
            DriveSegment.PICKUP -> {
                shiftDao.addPickupMeters(shiftId, meters)
                tripId?.let { tripDao.addPickupMeters(it, meters) }
            }
            DriveSegment.PAID -> {
                shiftDao.addPaidMeters(shiftId, meters)
                tripId?.let { tripDao.addPaidMeters(it, meters) }
            }
        }
    }
}
