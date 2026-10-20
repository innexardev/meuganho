package com.innexar.meuganho.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Turno de trabalho. Os km por segmento vêm do GPS já agregados: não guardamos
 * pontos de localização (milhares de linhas sem valor para o motorista).
 */
@Serializable
@Entity(tableName = "shifts", indices = [Index("startedAt")])
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long?,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startOdometerKm: Int? = null,
    val endOdometerKm: Int? = null,
    /** Plataformas usadas no turno, separadas por vírgula (ex.: "UBER,NINETY_NINE"). */
    val platforms: String,
    val pausedMillis: Long = 0,
    val pausedAt: Long? = null,
    val emptyMeters: Long = 0,
    val pickupMeters: Long = 0,
    val paidMeters: Long = 0,
    val fuelPriceCentsPerLiter: Long,
    val notes: String? = null,
) {
    val gpsMeters: Long get() = emptyMeters + pickupMeters + paidMeters
    val isOpen: Boolean get() = endedAt == null
    val isPaused: Boolean get() = pausedAt != null
}
