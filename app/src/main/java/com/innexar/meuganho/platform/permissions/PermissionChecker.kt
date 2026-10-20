package com.innexar.meuganho.platform.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.innexar.meuganho.platform.accessibility.MeuGanhoAccessibilityService

data class PermissionState(
    val accessibility: Boolean,
    val notificationListener: Boolean,
    val location: Boolean,
    val postNotifications: Boolean,
    val batteryUnrestricted: Boolean,
    /** Autorização (dada uma vez por cabo) para religar a leitura sozinho. */
    val canSelfRestore: Boolean = false,
)

/** Consulta de permissões e atalhos para as telas de configuração do sistema. */
object PermissionChecker {

    fun state(context: Context) = PermissionState(
        accessibility = accessibilityEnabled(context),
        notificationListener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        location = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        postNotifications = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true,
        canSelfRestore = AccessibilityRestorer.canRestore(context),
    )

    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val component = ComponentName(context, MeuGanhoAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationListenerSettings() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    // A tela geral de otimização de bateria não exige permissão extra no manifest.
    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
