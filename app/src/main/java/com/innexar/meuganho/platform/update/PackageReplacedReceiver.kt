package com.innexar.meuganho.platform.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.platform.foreground.ShiftForegroundService
import com.innexar.meuganho.platform.permissions.AccessibilityRestorer
import kotlinx.coroutines.launch

/**
 * Logo depois de o MeuGanho ser atualizado: religa a leitura (se autorizado) e, com turno
 * aberto, o GPS do turno. Este aviso do sistema é uma das exceções que permitem iniciar
 * o serviço em primeiro plano com o app fechado.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AccessibilityRestorer.ensureEnabled(context)
        val pending = goAsync()
        val container = context.appContainer
        container.appScope.launch {
            try {
                val shift = container.database.shiftDao().open()
                if (shift != null && !shift.isPaused && container.settingsRepository.current().gpsEnabled) {
                    ShiftForegroundService.startIfAllowed(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
