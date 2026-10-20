package com.innexar.meuganho.platform.permissions

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.innexar.meuganho.R
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.platform.accessibility.MeuGanhoAccessibilityService
import com.innexar.meuganho.platform.notifications.NotificationChannels

/**
 * A Acessibilidade de apps instalados fora da loja é desligada pelo Samsung depois de
 * atualizações e travamentos. Com WRITE_SECURE_SETTINGS (concedida UMA vez por cabo:
 * `adb shell pm grant com.innexar.meuganho android.permission.WRITE_SECURE_SETTINGS`)
 * o app religa a própria leitura. Sem ela, avisamos com uma notificação.
 */
object AccessibilityRestorer {
    const val ADB_COMMAND = "adb shell pm grant com.innexar.meuganho android.permission.WRITE_SECURE_SETTINGS"
    private const val ALERT_ID = 90

    fun canRestore(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Religa se estiver desligada e houver autorização. Devolve true se a leitura está ligada ao final. */
    fun ensureEnabled(context: Context): Boolean {
        if (PermissionChecker.accessibilityEnabled(context)) return true
        if (!canRestore(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val me = ComponentName(context, MeuGanhoAccessibilityService::class.java).flattenToString()
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val next = (current.split(':').filter { it.isNotBlank() && it != me } + me).joinToString(":")
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            AppLog.d(LogCategory.SYSTEM, "Leitura religada automaticamente")
            true
        }.getOrElse {
            AppLog.e(LogCategory.SYSTEM, "Não foi possível religar a leitura", it)
            false
        }
    }

    /** Aviso chamativo: trabalhar com a leitura desligada perde ofertas e corridas. */
    fun alert(context: Context, show: Boolean) {
        val nm = NotificationManagerCompat.from(context)
        if (!show) return nm.cancel(ALERT_ID)
        val open = PendingIntent.getActivity(context, ALERT_ID, PermissionChecker.accessibilitySettings(), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, NotificationChannels.ALERTS)
            .setSmallIcon(R.drawable.ic_stat_meuganho)
            .setContentTitle("Leitura do Uber/99 desligada")
            .setContentText("Toque para reativar a Acessibilidade do MeuGanho")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission")
        runCatching { nm.notify(ALERT_ID, n) }
    }
}
