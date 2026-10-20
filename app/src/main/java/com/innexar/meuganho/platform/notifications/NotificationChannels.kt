package com.innexar.meuganho.platform.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val SHIFT = "shift"
    const val UPDATES = "updates"
    /** "v2": canal novo; o Android não deixa o app alterar um canal já criado (o antigo "offers" é apagado). */
    const val OFFERS = "offers_v2"
    const val ALERTS = "alerts"

    fun register(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Importância baixa: notificação fixa do turno sem som nem vibração.
        manager.createNotificationChannel(
            NotificationChannel(SHIFT, "Turno ativo", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mantém o registro de km do turno funcionando"
                setShowBadge(false)
            }
        )
        manager.deleteNotificationChannel("offers")
        // Alta prioridade para aparecer no topo, mas sem som nem vibração: o som é da plataforma.
        manager.createNotificationChannel(
            NotificationChannel(OFFERS, "Análise de ofertas (aparece no topo)", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                description = "R$/km, R$/h e classificação de cada oferta do Uber e da 99"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERTS, "Alertas do turno", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisa quando a leitura do Uber/99 é desligada durante o turno"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(UPDATES, "Atualizações do app", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa quando há uma versão nova do MeuGanho"
            }
        )
    }
}
