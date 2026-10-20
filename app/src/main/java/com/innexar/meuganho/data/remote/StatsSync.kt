package com.innexar.meuganho.data.remote

import android.os.Build
import com.innexar.meuganho.BuildConfig
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.local.MeuGanhoDatabase
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.local.entity.SyncOutboxEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.data.preferences.SettingsRepository
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.financial.OfferAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

/**
 * Envio ao banco de estatísticas (innexar.com.br/meuganho/api/events): ofertas,
 * corridas e turnos, para descobrir melhores horários e regiões.
 *
 * Fila local (sync_outbox) com só (tipo, id); o conteúdo é lido na hora do envio.
 * O servidor grava por (instalação, id local), então reenviar nunca duplica.
 * Desligável em Configurações > Calibração e estatísticas.
 */
class StatsSync(
    private val db: MeuGanhoDatabase,
    private val settingsRepository: SettingsRepository,
    private val settings: StateFlow<AppSettings>,
    private val costModel: StateFlow<CostModel>,
    private val scope: CoroutineScope,
) {
    enum class Kind { OFFER, TRIP, SHIFT }

    private val dao = db.syncOutboxDao()
    private val mutex = Mutex()
    private var lastFailureAt = 0L
    private val _status = MutableStateFlow(UploadStatus())
    val status: StateFlow<UploadStatus> = _status.asStateFlow()

    private val enabled get() = settings.value.shareStats && BuildConfig.UPLOAD_KEY.isNotEmpty()

    fun enqueue(kind: Kind, localId: Long) {
        if (!enabled || localId <= 0) return
        scope.launch {
            dao.enqueue(SyncOutboxEntity(kind.name, localId, System.currentTimeMillis()))
            maybeUpload()
        }
    }

    /** Envia com 10 itens na fila ou item com mais de 2 min. Sem rede, espera a próxima chance. */
    fun maybeUpload() {
        if (!enabled || mutex.isLocked) return
        scope.launch {
            val now = System.currentTimeMillis()
            if (now - lastFailureAt < RETRY_AFTER_MS) return@launch
            val oldest = dao.oldestAt() ?: return@launch
            if (dao.count() >= BATCH_TRIGGER || now - oldest >= MAX_WAIT_MS) upload()
        }
    }

    suspend fun uploadNow(): Result<Int> = if (enabled) upload() else Result.failure(IllegalStateException("Envio desligado"))

    private suspend fun upload(): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            _status.value = _status.value.copy(lastAttemptAt = now)
            runCatching {
                val offers = dao.pending(Kind.OFFER.name, BATCH)
                val trips = dao.pending(Kind.TRIP.name, BATCH)
                val shifts = dao.pending(Kind.SHIFT.name, BATCH)
                if (offers.isEmpty() && trips.isEmpty() && shifts.isEmpty()) return@runCatching 0
                val body = buildJsonObject {
                    put("installId", settingsRepository.installId())
                    put("appVersionName", BuildConfig.VERSION_NAME)
                    put("appVersionCode", BuildConfig.VERSION_CODE)
                    put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("android", Build.VERSION.SDK_INT)
                    put("offers", buildJsonArray { offers.forEach { o -> db.offerDao().byId(o.localId)?.let { add(offerJson(it)) } } })
                    put("trips", buildJsonArray { trips.forEach { t -> db.tripDao().byId(t.localId)?.let { add(tripJson(it)) } } })
                    put("shifts", buildJsonArray { shifts.forEach { s -> db.shiftDao().byId(s.localId)?.let { add(shiftJson(it)) } } })
                }
                HttpJson.post("${BuildConfig.DIST_BASE_URL}/api/events", body.toString(), mapOf("X-MeuGanho-Key" to BuildConfig.UPLOAD_KEY))
                (offers + trips + shifts).forEach { dao.remove(it.kind, it.localId, it.createdAt) }
                offers.size + trips.size + shifts.size
            }.onSuccess {
                _status.value = _status.value.copy(lastSuccessAt = now, lastError = null, totalSent = _status.value.totalSent + it)
            }.onFailure {
                lastFailureAt = now
                _status.value = _status.value.copy(lastError = it.message ?: it.javaClass.simpleName)
                AppLog.e(LogCategory.SYSTEM, "Falha ao enviar estatísticas (tentará depois)", it)
            }
        }
    }

    private fun offerJson(o: OfferEntity): JsonObject {
        val value = o.effectiveCents
        val analysis = value?.let {
            OfferAnalyzer.analyze(it, o.totalMeters?.toLong(), o.totalSeconds?.toLong(), o.status.countsInStats,
                costModel.value, settings.value.thresholds)
        }
        val local = Instant.ofEpochMilli(o.detectedAt).atZone(ZoneId.systemDefault())
        return buildJsonObject {
            put("localId", o.id)
            put("platform", o.platform.name)
            put("detectedAt", o.detectedAt)
            put("localHour", local.hour)
            put("localWeekday", local.dayOfWeek.value)
            put("valueCents", value)
            put("surgeCents", o.surgeCents)
            put("pickupM", o.pickupMeters)
            put("pickupS", o.pickupSeconds)
            put("tripM", o.tripMeters)
            put("tripS", o.tripSeconds)
            put("centsPerKm", analysis?.centsPerKm)
            put("centsPerHour", analysis?.centsPerHour)
            put("category", o.category)
            put("rating", analysis?.rating?.name)
            put("status", o.status.name)
            put("outcome", o.outcome.name)
            put("channel", o.channel.name)
            put("cell", o.cell)
            put("parserVersion", o.parserVersion)
        }
    }

    private fun tripJson(t: TripEntity): JsonObject {
        val local = Instant.ofEpochMilli(t.acceptedAt).atZone(ZoneId.systemDefault())
        return buildJsonObject {
            put("localId", t.id)
            put("offerLocalId", t.offerId)
            put("platform", t.platform.name)
            put("state", t.state.name)
            put("status", t.status.name)
            put("acceptedAt", t.acceptedAt)
            put("startedAt", t.startedAt)
            put("endedAt", t.endedAt)
            put("localHour", local.hour)
            put("localWeekday", local.dayOfWeek.value)
            put("valueCents", t.effectiveCents)
            put("valueSource", t.valueSource.name)
            put("estimatedCents", t.estimatedCents)
            put("paidM", t.paidMeters)
            put("pickupM", t.pickupMeters)
            put("endCell", t.endCell)
            put("isManual", if (t.isManual) 1 else 0)
        }
    }

    private fun shiftJson(s: ShiftEntity): JsonObject = buildJsonObject {
        put("localId", s.id)
        put("startedAt", s.startedAt)
        put("endedAt", s.endedAt)
        put("onlineS", s.endedAt?.let { (it - s.startedAt - s.pausedMillis) / 1000 })
        put("gpsM", s.gpsMeters)
        put("emptyM", s.emptyMeters)
        put("pickupM", s.pickupMeters)
        put("paidM", s.paidMeters)
        put("odometerM", if (s.startOdometerKm != null && s.endOdometerKm != null) (s.endOdometerKm - s.startOdometerKm) * 1000L else null)
    }

    private companion object {
        const val BATCH = 100
        const val BATCH_TRIGGER = 10
        const val MAX_WAIT_MS = 2 * 60_000L
        const val RETRY_AFTER_MS = 2 * 60_000L
    }
}

