package com.innexar.meuganho.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import kotlinx.serialization.Serializable

/**
 * Corrida aceita. Guarda a estimativa da oferta E o resultado real para permitir
 * a comparação "parecia boa na oferta, foi ruim na prática".
 */
@Serializable
@Entity(tableName = "trips", indices = [Index("acceptedAt"), Index("shiftId"), Index("offerId")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long?,
    val offerId: Long?,
    val platform: Platform,
    val state: TripState,
    val status: DataStatus,
    val acceptedAt: Long,
    val pickupArrivedAt: Long? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val estimatedCents: Long? = null,
    val estPickupMeters: Int? = null,
    val estPickupSeconds: Int? = null,
    val estTripMeters: Int? = null,
    val estTripSeconds: Int? = null,
    val finalCents: Long? = null,
    val valueSource: ValueSource = ValueSource.NONE,
    val correctedCents: Long? = null,
    val reviewedAt: Long? = null,
    val gpsPickupMeters: Long = 0,
    val gpsPaidMeters: Long = 0,
    /** Informados no registro manual. */
    val manualMeters: Int? = null,
    val reasons: String = "",
    val isManual: Boolean = false,
    /** Região aproximada onde a corrida terminou (geohash-6). */
    val endCell: String? = null,
) {
    /**
     * Valor efetivo: correção do usuário > tela de conclusão > estimativa da oferta.
     * Corrida cancelada só tem valor se a plataforma pagou taxa (tela final ou usuário).
     */
    val effectiveCents: Long?
        get() = if (state == TripState.CANCELLED) correctedCents ?: finalCents
        else correctedCents ?: finalCents ?: estimatedCents

    val paidMeters: Long get() = if (gpsPaidMeters > 0) gpsPaidMeters else (manualMeters ?: estTripMeters ?: 0).toLong()
    val pickupMeters: Long get() = if (gpsPickupMeters > 0) gpsPickupMeters else (estPickupMeters ?: 0).toLong()
}
