package com.innexar.meuganho.platform.foreground

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.innexar.meuganho.R
import com.innexar.meuganho.app.MainActivity
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.domain.model.DriveSegment
import com.innexar.meuganho.engine.ServiceStatus
import com.innexar.meuganho.platform.location.GpsDistanceAccumulator
import com.innexar.meuganho.platform.location.ShiftLocationTracker
import com.innexar.meuganho.platform.notifications.NotificationChannels
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que existe APENAS durante um turno aberto com GPS
 * ligado. Sem turno, não há serviço nem localização.
 *
 * A notificação usa cronômetro do sistema (setUsesChronometer): o tempo de turno
 * avança sozinho sem nenhuma atualização nossa, custo zero.
 */
class ShiftForegroundService : LifecycleService() {

    private data class DistanceKey(val segment: DriveSegment, val tripId: Long?)

    private var shiftId: Long? = null
    private var started = false
    private lateinit var accumulator: GpsDistanceAccumulator<DistanceKey>
    private lateinit var tracker: ShiftLocationTracker

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_TOGGLE_PAUSE && started) {
            appContainer.appScope.launch { appContainer.togglePause(System.currentTimeMillis()) }
            return START_STICKY
        }
        val notification = buildNotification(System.currentTimeMillis())
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android 12+: quando o sistema reinicia o serviço sozinho (START_STICKY) com o app em
            // segundo plano, iniciar localização em primeiro plano é proibido e lançaria exceção,
            // fechando o app. Encerramos em silêncio; ao abrir o app, MainActivity religa o GPS.
            AppLog.e(LogCategory.GPS, "Não foi possível retomar o GPS em segundo plano", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            begin()
        }
        return START_STICKY
    }

    private val watchdog = android.os.Handler(android.os.Looper.getMainLooper())
    private var misses = 0
    private var ticks = 0

    /**
     * A cada minuto, com turno aberto: a leitura (Acessibilidade) está ligada? Se não, tenta
     * religar (se autorizado) e, persistindo por 2 verificações, avisa com notificação.
     */
    private val accessibilityCheck = object : Runnable {
        override fun run() {
            val ok = com.innexar.meuganho.platform.permissions.AccessibilityRestorer.ensureEnabled(this@ShiftForegroundService)
            misses = if (ok) 0 else misses + 1
            val now = System.currentTimeMillis()
            appContainer.appScope.launch {
                appContainer.tripTracker.tick(now)
                appContainer.autoPause.check(now)
                if (++ticks % 10 == 0) appContainer.reconciler.run(now) // a cada 10 min
            }
            com.innexar.meuganho.platform.permissions.AccessibilityRestorer.alert(this@ShiftForegroundService, show = misses >= 2)
            watchdog.postDelayed(this, 60_000L)
        }
    }

    private fun begin() {
        val container = appContainer
        ServiceStatus.shiftServiceRunning.value = true
        watchdog.postDelayed(accessibilityCheck, 30_000L)
        accumulator = GpsDistanceAccumulator(DistanceKey(DriveSegment.EMPTY, null)) { key, meters ->
            val id = shiftId ?: return@GpsDistanceAccumulator
            container.appScope.launch { container.shiftRepository.addDistance(id, key.tripId, key.segment, meters) }
        }
        tracker = ShiftLocationTracker(this) { fix ->
            com.innexar.meuganho.engine.LocationState.update(fix.latitude, fix.longitude, fix.timeMillis)
            com.innexar.meuganho.engine.WorkActivity.onFix(fix.latitude, fix.longitude, fix.timeMillis)
            val live = container.tripTracker.live.value
            accumulator.onFix(fix, DistanceKey(live.segment, live.tripId))
        }
        lifecycleScope.launch {
            container.shiftRepository.openShift.collect { shift ->
                when {
                    shift == null -> stopSelf()
                    // Pausa automática: o GPS segue ligado para perceber a volta ao trabalho (e retomar sozinho).
                    shift.isPaused && container.autoPause.isAuto(shift.id) -> {
                        shiftId = shift.id
                        updateNotification(shift.startedAt, paused = true, auto = true)
                        tracker.start()
                    }
                    shift.isPaused -> {
                        shiftId = shift.id
                        accumulator.flush()
                        tracker.stop()
                        updateNotification(shift.startedAt, paused = true)
                    }
                    else -> {
                        updateNotification(shift.startedAt, paused = false)
                        shiftId = shift.id
                        tracker.start()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        watchdog.removeCallbacksAndMessages(null)
        com.innexar.meuganho.platform.permissions.AccessibilityRestorer.alert(this, show = false)
        if (started) {
            tracker.stop()
            accumulator.flush()
        }
        ServiceStatus.shiftServiceRunning.value = false
        super.onDestroy()
    }

    private var lastPaused: Pair<Boolean, Boolean>? = null

    private fun updateNotification(startedAt: Long, paused: Boolean, auto: Boolean = false) {
        if (lastPaused == (paused to auto)) return
        lastPaused = paused to auto
        getSystemService(android.app.NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(startedAt, paused, auto))
    }

    private fun buildNotification(startedAt: Long, paused: Boolean = false, auto: Boolean = false): Notification {
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, ShiftForegroundService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.SHIFT)
            .setSmallIcon(R.drawable.ic_stat_meuganho)
            .setContentTitle(if (auto) "Pausado automaticamente" else if (paused) "Turno pausado" else "Turno ativo")
            .setContentText(when {
                auto -> "Parado sem ofertas · volta sozinho com oferta, corrida ou movimento"
                paused -> "GPS parado · o tempo pausado não conta como online"
                else -> "Registrando km e tempo do turno"
            })
            .setWhen(startedAt)
            .setUsesChronometer(!paused)
            .addAction(0, if (paused) "Retomar" else "Pausar", toggle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_TOGGLE_PAUSE = "com.innexar.meuganho.TOGGLE_PAUSE"

        /** Só inicia se houver permissão de localização (exigência do Android 14 para FGS de localização). */
        fun startIfAllowed(context: Context): Boolean {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
            ContextCompat.startForegroundService(context, Intent(context, ShiftForegroundService::class.java))
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShiftForegroundService::class.java))
        }
    }
}
