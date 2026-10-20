package com.innexar.meuganho.app

import android.content.Context
import com.innexar.meuganho.capture.core.CaptureDiagnostics
import com.innexar.meuganho.capture.core.ParserHealthMonitor
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.backup.BackupManager
import com.innexar.meuganho.data.local.MeuGanhoDatabase
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.data.preferences.SettingsRepository
import com.innexar.meuganho.data.repository.CostModelProvider
import com.innexar.meuganho.data.repository.ShiftRepository
import com.innexar.meuganho.data.repository.StatsRepository
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.engine.CaptureCoordinator
import com.innexar.meuganho.data.remote.SampleUploader
import com.innexar.meuganho.data.remote.StatsSync
import com.innexar.meuganho.engine.OfferRecorder
import com.innexar.meuganho.engine.SampleRecorder
import com.innexar.meuganho.platform.update.UpdateManager
import com.innexar.meuganho.engine.TripTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Injeção de dependências manual. Para um app deste tamanho, Hilt/Dagger
 * acrescentaria geração de código e tempo de inicialização sem benefício real.
 * Tudo é criado sob demanda (lazy) e vive enquanto o processo viver.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Escopo de longa duração para trabalho de fundo (captura, gravações). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { MeuGanhoDatabase.build(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val costModelProvider by lazy { CostModelProvider(database.vehicleDao(), database.fuelLogDao(), settingsRepository) }
    val shiftRepository by lazy { ShiftRepository(database.shiftDao(), database.tripDao(), database.vehicleDao()) }
    val backupManager by lazy { BackupManager(database, appContext.contentResolver) }

    val statsRepository by lazy {
        StatsRepository(
            database.tripDao(), database.shiftDao(), database.fuelLogDao(), database.expenseDao(),
            database.goalDao(), database.platformTotalDao(), database.offerDao(), costModelProvider,
        )
    }

    val settings: StateFlow<AppSettings> by lazy {
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())
    }
    val costModel: StateFlow<CostModel> by lazy {
        costModelProvider.costModel.stateIn(appScope, SharingStarted.Eagerly, CostModel.fallback(AppSettings().fuelPriceCentsPerLiter))
    }

    val sampleUploader by lazy {
        SampleUploader(database.diagnosticSampleDao(), settingsRepository, settings, appScope)
    }
    val statsSync by lazy { StatsSync(database, settingsRepository, settings, costModel, appScope) }
    val sampleRecorder by lazy { SampleRecorder(database.diagnosticSampleDao(), sampleUploader) }
    val updateManager by lazy {
        UpdateManager(appContext, settingsRepository, appScope).apply {
            canInstallNow = { database.shiftDao().open() == null }
        }
    }

    val diagnostics by lazy { CaptureDiagnostics() }
    val parserHealth by lazy { ParserHealthMonitor() }
    val tripTracker by lazy { TripTracker(database.tripDao(), database.offerDao(), database.shiftDao(), statsSync) }
    val remoteRules by lazy { com.innexar.meuganho.data.remote.RemoteRulesSync(appContext).also { it.loadCached() } }
    val reconciler by lazy { com.innexar.meuganho.engine.TripReconciler(database.tripDao(), database.offerDao(), statsSync) }
    val autoPause by lazy { com.innexar.meuganho.engine.ShiftAutoPause(context, database.shiftDao(), tripTracker, appScope).also { it.start() } }

    val captureCoordinator by lazy {
        CaptureCoordinator(
            scope = appScope,
            diagnostics = diagnostics,
            health = parserHealth,
            recorder = OfferRecorder(database.offerDao(), database.shiftDao(), statsSync),
            tripTracker = tripTracker,
            platformTotalDao = database.platformTotalDao(),
            costModel = costModel,
            settings = settings,
            samples = sampleRecorder,
            history = com.innexar.meuganho.engine.HistoryImporter(database.tripDao(), database.shiftDao(), statsSync),
            reconciler = reconciler,
        )
    }

    /**
     * Até a 0.3.0 a tela "Destino de" do Uber não era reconhecida: corridas concluídas viravam
     * "canceladas" com valor zero. Uma única vez, TODAS as canceladas dos últimos 3 dias vão para
     * Revisar: o motorista confirma as reais (as sem valor pedem o valor) e descarta os cancelamentos
     * verdadeiros. Duração e valor não servem de filtro: o "fim" gravado é o do falso cancelamento.
     */
    /**
     * Turnos antigos esquecidos abertos (antes da pausa automática): com 12 h ou mais, o fim passa a
     * ser o último sinal de trabalho (oferta ou corrida) + 15 min. Real: 28/09 11:05 → 29/09 06:43.
     */
    private suspend fun repairForgottenShifts(now: Long) {
        val shifts = database.shiftDao()
        shifts.all().filter { it.endedAt != null && it.endedAt - it.startedAt >= 12 * 3_600_000L && it.startedAt > now - 7 * 86_400_000L }
            .forEach { s ->
                val last = listOfNotNull(
                    database.offerDao().lastDetectedBetween(s.startedAt, s.endedAt!!),
                    database.tripDao().lastActivityBetween(s.startedAt, s.endedAt),
                ).maxOrNull() ?: s.startedAt
                val end = minOf(s.endedAt, last + com.innexar.meuganho.engine.ShiftAutoPause.IDLE_MS)
                if (end >= s.endedAt - 3_600_000L) return@forEach // não estava esquecido
                shifts.update(s.copy(endedAt = end, pausedMillis = minOf(s.pausedMillis, end - s.startedAt)))
                statsSync.enqueue(com.innexar.meuganho.data.remote.StatsSync.Kind.SHIFT, s.id)
                AppLog.d(LogCategory.DATABASE, "Turno ${s.id} esquecido aberto: fim corrigido")
            }
    }

    private suspend fun repairCancelledTrips() {
        if (settingsRepository.current().repairedCancelledTrips) return
        val dao = database.tripDao()
        dao.cancelledSince(System.currentTimeMillis() - 3 * 86_400_000L).forEach { trip ->
            dao.update(trip.copy(
                state = com.innexar.meuganho.domain.model.TripState.UNKNOWN,
                status = com.innexar.meuganho.domain.model.DataStatus.NEEDS_REVIEW,
                reasons = "Marcada como cancelada pela versão anterior. Se você fez esta corrida, confirme o valor; se foi cancelada mesmo, descarte.",
            ))
            statsSync.enqueue(com.innexar.meuganho.data.remote.StatsSync.Kind.TRIP, trip.id)
        }
        settingsRepository.update { it.copy(repairedCancelledTrips = true) }
    }

    /** Envia o último travamento (erro técnico, sem dados de tela) como amostra de diagnóstico. */
    private suspend fun reportLastCrash() {
        val crash = com.innexar.meuganho.core.crash.CrashRecorder.consume(appContext) ?: return
        AppLog.e(LogCategory.SYSTEM, "Travamento anterior registrado")
        database.diagnosticSampleDao().insert(com.innexar.meuganho.data.local.entity.DiagnosticSampleEntity(
            capturedAt = System.currentTimeMillis(),
            platform = com.innexar.meuganho.domain.model.Platform.UBER, // campo obrigatório; o motivo CRASH identifica
            screenType = com.innexar.meuganho.capture.core.ScreenType.UNKNOWN,
            reason = "CRASH",
            parserVersion = com.innexar.meuganho.BuildConfig.VERSION_NAME,
            sourceAppVersion = 0,
            sanitizedText = crash,
            parseSummary = "{}",
        ))
        sampleUploader.maybeUpload(urgent = true)
    }

    /** Pausa/retomada pelo motorista (botão, notificação): vira pausa manual. */
    suspend fun togglePause(now: Long) {
        autoPause.onManualToggle(now)
        shiftRepository.togglePause(now)
    }

    /** Tarefas únicas de inicialização, fora da main thread. */
    fun onAppStart() {
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                tripTracker.restore(now)
                autoPause.check(now) // turno esquecido aberto (serviço morto à noite) é pausado/fechado aqui
                val retentionMs = settingsRepository.current().snapshotRetentionDays * 86_400_000L
                val cleared = database.offerDao().clearSnapshotsBefore(now - retentionMs)
                if (cleared > 0) AppLog.d(LogCategory.DATABASE, "Snapshots expirados removidos: $cleared")
                repairCancelledTrips()
                repairForgottenShifts(now)
                reconciler.run(now)
                remoteRules.refreshIfDue(now)
                reportLastCrash()
            }.onFailure { AppLog.e(LogCategory.SYSTEM, "Falha na inicialização", it) }
        }
    }
}
