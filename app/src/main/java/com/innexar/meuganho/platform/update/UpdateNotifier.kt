package com.innexar.meuganho.platform.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.innexar.meuganho.R
import com.innexar.meuganho.app.MainActivity
import com.innexar.meuganho.platform.notifications.NotificationChannels

/** Notificação de versão nova. Tocar abre o app, onde o popup oferece atualizar. */
object UpdateNotifier {
    private const val ID = 77

    fun show(context: Context, manifest: UpdateManifest) {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        if (!allowed) return
        val open = PendingIntent.getActivity(
            context, ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.UPDATES)
            .setSmallIcon(R.drawable.ic_stat_meuganho)
            .setContentTitle("MeuGanho ${manifest.versionName} disponível")
            .setContentText(manifest.notes.ifBlank { "Toque para atualizar" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(manifest.notes.ifBlank { "Toque para atualizar" }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID, notification)
    }
}
