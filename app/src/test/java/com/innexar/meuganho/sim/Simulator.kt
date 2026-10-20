package com.innexar.meuganho.sim

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.CaptureDiagnostics
import com.innexar.meuganho.capture.core.ParserHealthMonitor
import com.innexar.meuganho.capture.core.ScreenLine
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.data.local.MeuGanhoDatabase
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.data.preferences.SettingsRepository
import com.innexar.meuganho.data.remote.SampleUploader
import com.innexar.meuganho.data.remote.StatsSync
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.engine.CaptureCoordinator
import com.innexar.meuganho.engine.HistoryImporter
import com.innexar.meuganho.engine.OfferRecorder
import com.innexar.meuganho.engine.SampleRecorder
import com.innexar.meuganho.engine.TripReconciler
import com.innexar.meuganho.engine.TripTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/**
 * O pipeline REAL (coordenador, máquina de estados, banco Room em memória, conciliador) alimentado
 * por telas reais com horário controlado. Nada de rede: envio de estatísticas e amostras desligado.
 */
class Simulator : AutoCloseable {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    val db: MeuGanhoDatabase = Room.inMemoryDatabaseBuilder(ctx, MeuGanhoDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = MutableStateFlow(AppSettings(shareStats = false, sendSamples = false))
    private val costModel = MutableStateFlow(CostModel(fuelPriceCentsPerLiter = 600, kmPerLiter = 10.0,
        maintenanceCentsPerKm = 10.0, depreciationCentsPerKm = 10.0, otherCentsPerKm = 0.0))
    private val settingsRepo = SettingsRepository(ctx)
    private val stats = StatsSync(db, settingsRepo, settings, costModel, scope)

    val diagnostics = CaptureDiagnostics()
    val tracker = TripTracker(db.tripDao(), db.offerDao(), db.shiftDao(), stats)
    val history = HistoryImporter(db.tripDao(), db.shiftDao(), stats)
    val reconciler = TripReconciler(db.tripDao(), db.offerDao(), stats)
    val coordinator = CaptureCoordinator(
        scope = scope,
        diagnostics = diagnostics,
        health = ParserHealthMonitor(),
        recorder = OfferRecorder(db.offerDao(), db.shiftDao(), stats),
        tripTracker = tracker,
        platformTotalDao = db.platformTotalDao(),
        costModel = costModel,
        settings = settings,
        samples = SampleRecorder(db.diagnosticSampleDao(), SampleUploader(db.diagnosticSampleDao(), settingsRepo, settings, scope)),
        history = history,
        reconciler = reconciler,
    )

    /** Relógio do cenário (ms). Começa às 10:00 de 29/09/2026 (BRT). */
    var now: Long = START
        private set

    init {
        runBlocking { db.shiftDao().insert(ShiftEntity(vehicleId = null, startedAt = START - 60_000, platforms = "UBER,NINETY_NINE", fuelPriceCentsPerLiter = 600)) }
    }

    fun wait(ms: Long) = apply { now += ms }

    /** Relógio do serviço do turno (1x/min no app). */
    fun tick() = apply { runBlocking { tracker.tick(now) } }

    /** Processa a tela no instante atual (em ordem, como a fila faria). */
    fun see(snapshot: ScreenSnapshot) = apply { runBlocking { coordinator.process(snapshot.copy(capturedAt = now)) } }

    /** Toque de oferta da 99 (mesma regra do app: em corrida não conta). Direto, sem a fila, para ser determinístico. */
    fun sound99() = apply {
        if (tracker.isActive(com.innexar.meuganho.domain.model.Platform.NINETY_NINE)) return@apply
        runBlocking { coordinator.process(ScreenSnapshot("sound:NINETY_NINE", 0, now, emptyList())) }
    }

    /**
     * Roda um turno gerado: telas e toques na ordem do tempo, conciliador a cada 10 min (como o
     * serviço do turno) e uma última vez no fim.
     */
    fun run(shift: GeneratedShift, iphone: Boolean = false) {
        var nextReconcile = shift.events.firstOrNull()?.at?.plus(10 * 60_000L) ?: return
        var nextTick = shift.events.first().at + 60_000L
        for (e in shift.events) {
            while (e.at >= nextTick) {
                now = nextTick
                runBlocking { tracker.tick(nextTick) }
                nextTick += 60_000L
            }
            now = e.at
            while (e.at >= nextReconcile) {
                runBlocking { reconciler.run(nextReconcile) }
                nextReconcile += 10 * 60_000L
            }
            when (e) {
                is SimEvent.Screen -> see(if (iphone) asIphone(e.snapshot) else e.snapshot)
                is SimEvent.Sound -> sound99()
            }
        }
        while (nextTick <= shift.endAt) { now = nextTick; runBlocking { tracker.tick(nextTick) }; nextTick += 60_000L }
        now = shift.endAt
        runBlocking { reconciler.run(now) }
    }

    /**
     * Como o iPhone veria a tela: imagem da tela inteira lida por OCR (Transmissão de tela). Sem pacote
     * (o app é detectado pelo texto), sem ids de view e sem cartões clicáveis da Acessibilidade.
     */
    fun asIphone(s: ScreenSnapshot) = s.copy(
        packageName = ScreenSnapshot.WHOLE_SCREEN,
        channel = CaptureChannel.OCR,
        lines = s.lines.map { ScreenLine(it.text) },
    )

    fun offers(): List<OfferEntity> = runBlocking { db.offerDao().all().sortedBy { it.detectedAt } }
    fun trips(): List<TripEntity> = runBlocking { db.tripDao().all().sortedBy { it.acceptedAt } }

    override fun close() {
        scope.cancel()
        db.close()
    }

    companion object {
        val START: Long = java.time.ZonedDateTime.of(2026, 9, 29, 10, 0, 0, 0, java.time.ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli()

        fun uber(name: String) = Fixtures.uber(name)
        fun ninetyNine(name: String, ocr: Boolean = true) =
            Fixtures.ninetyNine(name).let { if (ocr) it.copy(channel = CaptureChannel.OCR) else it }

        /** Tela inicial do Uber com o total do dia ("HOJE") em [cents]. */
        fun uberHome(cents: Long) = uber("real_home_with_tracker").replacing("R$ 21,40", "R$ ${cents / 100},${"%02d".format(cents % 100)}")
            .let { it.copy(lines = it.lines + ScreenLine("HOJE", "ub__earnings_tracker_label_text")) }

        /** Central de ganhos da 99 com o ganho do dia em [cents]. */
        fun ninetyNineEarnings(cents: Long) = ninetyNine("real_daily_earnings")
            .replacing("R$134,58", "R$${cents / 100},${"%02d".format(cents % 100)}")
    }
}

fun ScreenSnapshot.replacing(old: String, new: String): ScreenSnapshot {
    require(lines.any { old in it.text }) { "Texto '$old' não está na tela" }
    return copy(lines = lines.map { if (old in it.text) ScreenLine(it.text.replace(old, new), it.viewId, it.group) else it })
}
