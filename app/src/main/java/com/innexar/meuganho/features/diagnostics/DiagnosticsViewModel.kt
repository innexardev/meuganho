package com.innexar.meuganho.features.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.capture.core.ParserHealth
import com.innexar.meuganho.capture.core.PlatformDiagnostics
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogEntry
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.engine.ServiceStatus
import com.innexar.meuganho.platform.permissions.PermissionChecker
import com.innexar.meuganho.platform.permissions.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiagnosticsState(
    val permissions: PermissionState? = null,
    val accessibilityConnected: Boolean = false,
    val notificationConnected: Boolean = false,
    val shiftServiceRunning: Boolean = false,
    val platforms: Map<Platform, PlatformDiagnostics> = emptyMap(),
    val health: Map<Platform, ParserHealth> = emptyMap(),
    val logs: List<LogEntry> = emptyList(),
    val recorded: String? = null,
    val recordingArmed: Boolean = false,
)

class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {
    private val permissions = MutableStateFlow<PermissionState?>(null)
    private val armed = MutableStateFlow(false)

    val state: StateFlow<DiagnosticsState> = combine(
        combine(permissions, ServiceStatus.accessibilityConnected, ServiceStatus.notificationListenerConnected, ServiceStatus.shiftServiceRunning) { p, a, n, s ->
            DiagnosticsState(permissions = p, accessibilityConnected = a, notificationConnected = n, shiftServiceRunning = s)
        },
        container.diagnostics.platforms,
        container.parserHealth.health,
        AppLog.entries,
        combine(container.diagnostics.recorded, armed) { r, a -> r to a },
    ) { base, platforms, health, logs, (recorded, isArmed) ->
        base.copy(platforms = platforms, health = health, logs = logs.takeLast(60).reversed(), recorded = recorded,
            recordingArmed = isArmed && recorded == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsState())

    fun refreshPermissions(context: Context) {
        permissions.value = PermissionChecker.state(context)
    }

    /** Grava a próxima tela lida do Uber/99 (sanitizada) para virar fixture de teste. */
    fun armRecording() {
        container.diagnostics.armRecording()
        armed.value = true
    }

    fun resetHealth(platform: Platform) = container.parserHealth.reset(platform)
}
