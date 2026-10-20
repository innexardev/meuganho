package com.innexar.meuganho.platform.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory

/**
 * Localização com a API nativa do Android (sem Google Play Services, menos
 * dependências e menos RAM).
 *
 * Intervalo 5 s + distância mínima 15 m: o sistema só nos acorda quando o carro
 * anda. Parado no semáforo ou esperando corrida, praticamente não há callbacks.
 * Preferimos o provedor "fused" (Android 12+), que combina GPS/rede/sensores e
 * gasta menos bateria que o GPS puro.
 */
class ShiftLocationTracker(
    context: Context,
    private val onFix: (GpsFix) -> Unit,
) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var running = false

    private val listener = LocationListener { location: Location ->
        onFix(GpsFix(location.latitude, location.longitude, location.accuracy, location.time))
    }

    @SuppressLint("MissingPermission") // Verificado por quem inicia o serviço.
    fun start() {
        if (running || manager == null) return
        val provider = pickProvider() ?: run {
            AppLog.e(LogCategory.GPS, "Nenhum provedor de localização disponível")
            return
        }
        manager.requestLocationUpdates(provider, INTERVAL_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper())
        running = true
        AppLog.d(LogCategory.GPS, "GPS iniciado ($provider)")
    }

    fun stop() {
        if (!running) return
        manager?.removeUpdates(listener)
        running = false
        AppLog.d(LogCategory.GPS, "GPS parado")
    }

    private fun pickProvider(): String? {
        val providers = manager?.getProviders(true).orEmpty()
        return when {
            Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    private companion object {
        const val INTERVAL_MS = 5_000L
        const val MIN_DISTANCE_M = 15f
    }
}
