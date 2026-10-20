package com.innexar.meuganho.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.Confidence
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.Platform
import kotlinx.serialization.Serializable

/**
 * Oferta exibida pela plataforma (aceita ou não).
 *
 * Auditoria: o valor capturado nunca é sobrescrito. A correção do usuário vai em
 * [correctedCents] e o valor efetivo é corrigido ?: capturado.
 */
@Serializable
@Entity(tableName = "offers", indices = [Index("detectedAt"), Index("status")])
data class OfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long?,
    val platform: Platform,
    val detectedAt: Long,
    val fingerprint: String,
    val offeredCents: Long?,
    val surgeCents: Long?,
    val category: String?,
    val pickupMeters: Int?,
    val pickupSeconds: Int?,
    val tripMeters: Int?,
    val tripSeconds: Int?,
    /** Confiança por campo, ex.: "valor=HIGH;km da viagem=MEDIUM". */
    val fieldConfidence: String,
    val confidence: Confidence,
    val status: DataStatus,
    /** Motivos da revisão/invalidação separados por " | ". */
    val reasons: String,
    val outcome: OfferOutcome = OfferOutcome.PENDING,
    val parserVersion: String,
    val sourceAppVersion: Long,
    val channel: CaptureChannel,
    /** Snapshot SANITIZADO, só para itens em revisão, apagado após a retenção. */
    val rawSnapshot: String? = null,
    val correctedCents: Long? = null,
    val reviewedAt: Long? = null,
    /** Região aproximada (geohash-6, ~1 km) onde o motorista estava quando a oferta tocou. */
    val cell: String? = null,
) {
    val effectiveCents: Long? get() = correctedCents ?: offeredCents
    val totalMeters: Int? get() = tripMeters?.let { it + (pickupMeters ?: 0) }
    val totalSeconds: Int? get() = tripSeconds?.let { it + (pickupSeconds ?: 0) }
}
