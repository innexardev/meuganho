package com.innexar.meuganho.platform.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.innexar.meuganho.R
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.platform.notifications.NotificationChannels
import java.io.File

/**
 * Instala a atualização pela API de sessão do Android.
 *
 * Android 12+: se o MeuGanho foi quem instalou a versão atual (a partir da 1ª atualização
 * feita por aqui), USER_ACTION_NOT_REQUIRED permite atualizar SEM confirmação. Na primeira
 * vez o sistema pede confirmação: chega em [UpdateStatusReceiver], que abre a tela (app na
 * frente) ou mostra uma notificação "toque para concluir".
 */
object SessionInstaller {
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, id, Intent(context, UpdateStatusReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
        AppLog.d(LogCategory.SYSTEM, "Atualização enviada ao instalador")
    }
}

class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                // Com o app na frente, abre direto; em segundo plano o Android pode bloquear: notificação garante.
                runCatching { context.startActivity(confirm) }
                val tap = PendingIntent.getActivity(context, 78, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(context, NotificationChannels.UPDATES)
                    .setSmallIcon(R.drawable.ic_stat_meuganho)
                    .setContentTitle("Atualização do MeuGanho pronta")
                    .setContentText("Toque para concluir. As próximas serão automáticas.")
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .build()
                @Suppress("MissingPermission")
                runCatching { NotificationManagerCompat.from(context).notify(77, n) }
            }
            PackageInstaller.STATUS_SUCCESS -> AppLog.d(LogCategory.SYSTEM, "Atualização instalada")
            else -> AppLog.e(LogCategory.SYSTEM,
                "Atualização falhou ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
