package com.innexar.meuganho.engine

import com.innexar.meuganho.data.local.dao.OfferDao
import com.innexar.meuganho.data.local.dao.ShiftDao
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.RideOffer
import com.innexar.meuganho.domain.validation.OfferAssessment
import com.innexar.meuganho.domain.validation.PersonalBaseline
import com.innexar.meuganho.domain.validation.StatusDecision

data class RecordedOffer(val entity: OfferEntity, val decision: StatusDecision)

/**
 * Valida, decide o status e persiste uma oferta. O snapshot sanitizado só é
 * guardado quando a oferta precisa de revisão (evidência para o usuário e para
 * reprocessar com uma nova versão do parser).
 */
class OfferRecorder(
    private val offerDao: OfferDao,
    private val shiftDao: ShiftDao,
    private val stats: com.innexar.meuganho.data.remote.StatsSync,
) {

    private var baseline: PersonalBaseline? = null
    private var offersSinceBaseline = BASELINE_REFRESH_EVERY

    /** [replaceId]: oferta gravada antes a partir da notificação, agora lida completa na tela. */
    suspend fun record(offer: RideOffer, replaceId: Long? = null, sanitizedSnapshot: () -> String): RecordedOffer {
        refreshBaselineIfNeeded()
        val decision = OfferAssessment.assess(offer, baseline)
        val keepSnapshot = decision.status == DataStatus.NEEDS_REVIEW || decision.status == DataStatus.INVALID

        val entity = OfferEntity(
            shiftId = shiftDao.open()?.id,
            platform = offer.platform,
            detectedAt = offer.detectedAt,
            fingerprint = offer.fingerprint(),
            offeredCents = offer.offeredCents.value,
            surgeCents = offer.surgeCents,
            category = offer.category,
            pickupMeters = offer.pickupMeters.value,
            pickupSeconds = offer.pickupSeconds.value,
            tripMeters = offer.tripMeters.value,
            tripSeconds = offer.tripSeconds.value,
            fieldConfidence = offer.criticalFields.entries.joinToString(";") { "${it.key}=${it.value.confidence}" },
            confidence = offer.criticalConfidence,
            status = decision.status,
            reasons = decision.reasons.joinToString(" | "),
            parserVersion = offer.parserVersion,
            sourceAppVersion = offer.sourceAppVersion,
            channel = offer.channel,
            rawSnapshot = if (keepSnapshot) sanitizedSnapshot() else null,
            cell = LocationState.currentCell(offer.detectedAt),
        )
        if (replaceId != null) {
            val previous = offerDao.byId(replaceId)
            if (previous != null) {
                val updated = entity.copy(id = replaceId, outcome = previous.outcome, shiftId = previous.shiftId)
                offerDao.update(updated)
                stats.enqueue(com.innexar.meuganho.data.remote.StatsSync.Kind.OFFER, replaceId)
                return RecordedOffer(updated, decision)
            }
        }
        val id = offerDao.insert(entity)
        offersSinceBaseline++
        stats.enqueue(com.innexar.meuganho.data.remote.StatsSync.Kind.OFFER, id)
        return RecordedOffer(entity.copy(id = id), decision)
    }

    private suspend fun refreshBaselineIfNeeded() {
        if (offersSinceBaseline < BASELINE_REFRESH_EVERY) return
        baseline = PersonalBaseline.fromSamples(offerDao.recentCentsPerKm())
        offersSinceBaseline = 0
    }

    private companion object {
        // Percentis mudam devagar: recalcular a cada 20 ofertas basta.
        const val BASELINE_REFRESH_EVERY = 20
    }
}
