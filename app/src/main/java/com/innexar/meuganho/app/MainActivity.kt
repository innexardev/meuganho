package com.innexar.meuganho.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.innexar.meuganho.app.navigation.AppNavHost
import com.innexar.meuganho.core.designsystem.theme.MeuGanhoTheme
import com.innexar.meuganho.engine.ServiceStatus
import com.innexar.meuganho.platform.foreground.ShiftForegroundService
import com.innexar.meuganho.features.onboarding.PermissionsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @Volatile private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // A splash fica até as configurações carregarem, evitando piscar o onboarding.
        installSplashScreen().setKeepOnScreenCondition { !settingsLoaded }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        lifecycleScope.launch {
            val settings = container.settingsRepository.settings.first()
            settingsLoaded = true
            // Rede só com o app em primeiro plano: verifica versão nova (no máx. a cada 6 h)
            // e esvazia a fila de amostras de calibração.
            // Ao abrir o app, sempre verifica (independe do intervalo do serviço).
            container.updateManager.check()
            container.updateManager.installIfIdle()
            container.sampleUploader.maybeUpload()
            container.statsSync.maybeUpload()
            // Se o sistema matou o processo com turno aberto, religa o GPS ao abrir o app.
            if (settings.gpsEnabled && !ServiceStatus.shiftServiceRunning.value &&
                container.database.shiftDao().open()?.isPaused == false
            ) {
                ShiftForegroundService.startIfAllowed(this@MainActivity)
            }
        }
        // Sem esta permissão (Android 13+) a análise das ofertas não aparece: pedimos ao abrir.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MeuGanhoTheme {
                val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
                val scope = rememberCoroutineScope()
                when {
                    settings == null -> Unit
                    settings?.onboardingDone == false -> PermissionsScreen(
                        onboarding = true,
                        onDone = { scope.launch { container.settingsRepository.update { it.copy(onboardingDone = true) } } },
                    )
                    else -> {
                        AppNavHost()
                        com.innexar.meuganho.features.update.UpdateDialog()
                    }
                }
            }
        }
    }
}
