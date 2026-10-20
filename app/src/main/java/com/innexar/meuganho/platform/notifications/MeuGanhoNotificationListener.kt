package com.innexar.meuganho.platform.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.capture.core.ScreenLine
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.engine.ServiceStatus

/**
 * Fonte secundária: notificações do Uber/99. Úteis para Diagnóstico e como
 * fallback quando a Acessibilidade está desligada. Confiança limitada a MEDIUM
 * pelo parser, porque notificações são resumos sem estrutura.
 */
class MeuGanhoNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        ServiceStatus.notificationListenerConnected.value = true
    }

    override fun onListenerDisconnected() {
        ServiceStatus.notificationListenerConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (Platform.fromPackage(sbn.packageName) == null) return
        val extras = sbn.notification.extras
        val texts = buildList {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it.toString()) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
        }.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (texts.isEmpty()) return
        appContainer.captureCoordinator.submit(
            ScreenSnapshot(
                packageName = sbn.packageName,
                appVersionCode = 0,
                capturedAt = System.currentTimeMillis(),
                lines = texts.map { ScreenLine(it) },
                channel = CaptureChannel.NOTIFICATION,
            )
        )
    }
}
