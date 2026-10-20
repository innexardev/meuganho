package com.innexar.meuganho.engine

import kotlinx.coroutines.flow.MutableStateFlow

/** Quais serviços do sistema estão conectados agora (para Diagnóstico e decisões de captura). */
object ServiceStatus {
    val accessibilityConnected = MutableStateFlow(false)
    val notificationListenerConnected = MutableStateFlow(false)
    val shiftServiceRunning = MutableStateFlow(false)
}
