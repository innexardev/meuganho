package com.innexar.meuganho.engine

import android.content.Context
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.local.dao.ShiftDao
import com.innexar.meuganho.domain.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Último sinal de trabalho: oferta, corrida ou carro em movimento. */
object WorkActivity {
    private val _last = MutableStateFlow(0L)
    val last: StateFlow<Long> = _last.asStateFlow()

    fun mark(now: Long) {
        if (now > _last.value) _last.value = now
    }

    // Movimento real: saiu 300 m do último ponto parado (o GPS "tremendo" parado não conta).
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN

    fun onFix(lat: Double, lon: Double, at: Long) {
        if (anchorLat.isNaN()) {
            anchorLat = lat; anchorLon = lon
            return
        }
        val d = FloatArray(1)
        android.location.Location.distanceBetween(anchorLat, anchorLon, lat, lon, d)
        if (d[0] >= MOVE_METERS) {
            anchorLat = lat; anchorLon = lon
            mark(at)
        }
    }

    private const val MOVE_METERS = 300f
}

/**
 * Tempo de turno inteligente. Real (29/09/2026): um turno esquecido aberto de madrugada somou
 * 15 h num dia de 8,5 h rodadas.
 *
 * - Sem oferta, sem corrida e com o carro parado por [IDLE_MS]: pausa AUTOMÁTICA, contada desde o
 *   último sinal de trabalho (o tempo parado não conta como online).
 * - Qualquer oferta, corrida ou movimento: retoma sozinho. Pausa manual só o motorista retoma.
 * - Pausado automaticamente por [CLOSE_AFTER_MS]: o turno é encerrado no último sinal de trabalho.
 */
class ShiftAutoPause(
    context: Context,
    private val shiftDao: ShiftDao,
    private val tripTracker: TripTracker,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("auto_pause", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** Pausa atual foi automática? (guardado por turno para sobreviver ao app ser fechado) */
    private var autoPausedShift: Long
        get() = prefs.getLong("shift", -1L)
        set(v) = prefs.edit().putLong("shift", v).apply()

    val isAutoPaused = MutableStateFlow(false)

    fun start() {
        prefs.getLong("last", 0L).let(WorkActivity::mark)
        scope.launch {
            var savedAt = 0L
            WorkActivity.last.collect { at ->
                if (at <= 0) return@collect
                // Disco no máximo 1x/min (os sinais chegam a cada poucos segundos durante a corrida).
                if (at - savedAt >= 60_000L) { prefs.edit().putLong("last", at).apply(); savedAt = at }
                runCatching { resumeIfAuto(at) }.onFailure { AppLog.e(LogCategory.SYSTEM, "Retomada automática", it) }
            }
        }
    }

    /** Chamado a cada minuto pelo serviço do turno e na abertura do app. */
    suspend fun check(now: Long) = mutex.withLock {
        var shift = shiftDao.open() ?: return@withLock
        if (shift.pausedAt == null) {
            if (Platform.entries.any(tripTracker::isActive)) return@withLock
            val last = maxOf(WorkActivity.last.value, shift.startedAt)
            if (now - last < IDLE_MS) return@withLock
            autoPausedShift = shift.id // antes do update: o serviço decide manter o GPS pela marca
            isAutoPaused.value = true
            shift = shift.copy(pausedAt = last)
            shiftDao.update(shift)
            AppLog.d(LogCategory.SYSTEM, "Turno pausado automaticamente (parado desde $last)")
        }
        val pausedAt = shift.pausedAt ?: return@withLock
        val auto = autoPausedShift == shift.id
        isAutoPaused.value = auto
        // Pausa automática longa (ou turno esquecido aberto): fecha no último sinal de trabalho.
        if (auto && now - pausedAt >= CLOSE_AFTER_MS) {
            shiftDao.update(shift.copy(endedAt = pausedAt, pausedAt = null))
            autoPausedShift = -1L
            isAutoPaused.value = false
            AppLog.d(LogCategory.SYSTEM, "Turno encerrado automaticamente após ${CLOSE_AFTER_MS / 3_600_000}h parado")
        }
    }

    /** A pausa do turno [shiftId] foi automática (o GPS continua para detectar a volta ao trabalho). */
    fun isAuto(shiftId: Long) = autoPausedShift == shiftId

    private suspend fun resumeIfAuto(at: Long) = mutex.withLock {
        val shift = shiftDao.open() ?: return@withLock
        val pausedAt = shift.pausedAt ?: return@withLock
        if (autoPausedShift != shift.id || at <= pausedAt) return@withLock
        shiftDao.update(shift.copy(pausedMillis = shift.pausedMillis + (at - pausedAt), pausedAt = null))
        autoPausedShift = -1L
        isAutoPaused.value = false
        AppLog.d(LogCategory.SYSTEM, "Turno retomado automaticamente")
    }

    /** O motorista mexeu na pausa: a partir daqui ela é manual. */
    fun onManualToggle(now: Long) {
        WorkActivity.mark(now) // a pausa automática conta daqui, não de antes da retomada manual
        autoPausedShift = -1L
        isAutoPaused.value = false
    }

    companion object {
        const val IDLE_MS = 15 * 60_000L
        const val CLOSE_AFTER_MS = 3 * 60 * 60_000L
    }
}
