package com.innexar.meuganho.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.innexar.meuganho.domain.financial.RatingThresholds
import com.innexar.meuganho.domain.model.FuelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Fonte única das preferências. DataStore é assíncrono e emite só quando algo muda. */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    /** Id anônimo da instalação, gerado na primeira vez que for pedido. */
    suspend fun installId(): String {
        current().installId.takeIf { it.isNotEmpty() }?.let { return it }
        val id = java.util.UUID.randomUUID().toString()
        update { if (it.installId.isEmpty()) it.copy(installId = id) else it }
        return current().installId
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[K.excKm] = next.thresholds.excellentCentsPerKm
            prefs[K.excHour] = next.thresholds.excellentCentsPerHour
            prefs[K.accKm] = next.thresholds.acceptableCentsPerKm
            prefs[K.accHour] = next.thresholds.acceptableCentsPerHour
            prefs[K.fuelPrice] = next.fuelPriceCentsPerLiter
            prefs[K.kwhPrice] = next.electricityPriceCentsPerKwh
            prefs[K.currentFuel] = next.currentFuel.name
            prefs[K.observed] = next.useObservedConsumption
            prefs[K.otherCost] = next.otherCentsPerKm
            prefs[K.gps] = next.gpsEnabled
            prefs[K.overlay] = next.overlayEnabled
            prefs[K.offerDisplay] = next.offerDisplay.name
            prefs[K.overlayX] = next.overlayX
            prefs[K.overlayY] = next.overlayY
            prefs[K.overlayExpanded] = next.overlayExpanded
            prefs[K.onboarding] = next.onboardingDone
            prefs[K.retention] = next.snapshotRetentionDays
            prefs[K.sendSamples] = next.sendSamples
            prefs[K.shareStats] = next.shareStats
            prefs[K.installId] = next.installId
            prefs[K.lastUpdateCheck] = next.lastUpdateCheckAt
            prefs[K.repairedCancelled] = next.repairedCancelledTrips
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            thresholds = RatingThresholds(
                excellentCentsPerKm = this[K.excKm] ?: d.thresholds.excellentCentsPerKm,
                excellentCentsPerHour = this[K.excHour] ?: d.thresholds.excellentCentsPerHour,
                acceptableCentsPerKm = this[K.accKm] ?: d.thresholds.acceptableCentsPerKm,
                acceptableCentsPerHour = this[K.accHour] ?: d.thresholds.acceptableCentsPerHour,
            ),
            fuelPriceCentsPerLiter = this[K.fuelPrice] ?: d.fuelPriceCentsPerLiter,
            electricityPriceCentsPerKwh = this[K.kwhPrice] ?: d.electricityPriceCentsPerKwh,
            currentFuel = this[K.currentFuel]?.let { runCatching { FuelType.valueOf(it) }.getOrNull() } ?: d.currentFuel,
            useObservedConsumption = this[K.observed] ?: d.useObservedConsumption,
            otherCentsPerKm = this[K.otherCost] ?: d.otherCentsPerKm,
            gpsEnabled = this[K.gps] ?: d.gpsEnabled,
            overlayEnabled = this[K.overlay] ?: d.overlayEnabled,
            offerDisplay = this[K.offerDisplay]?.let { runCatching { OfferDisplay.valueOf(it) }.getOrNull() } ?: d.offerDisplay,
            overlayX = this[K.overlayX] ?: d.overlayX,
            overlayY = this[K.overlayY] ?: d.overlayY,
            overlayExpanded = this[K.overlayExpanded] ?: d.overlayExpanded,
            onboardingDone = this[K.onboarding] ?: d.onboardingDone,
            snapshotRetentionDays = this[K.retention] ?: d.snapshotRetentionDays,
            sendSamples = this[K.sendSamples] ?: d.sendSamples,
            shareStats = this[K.shareStats] ?: d.shareStats,
            installId = this[K.installId] ?: d.installId,
            lastUpdateCheckAt = this[K.lastUpdateCheck] ?: d.lastUpdateCheckAt,
            repairedCancelledTrips = this[K.repairedCancelled] ?: d.repairedCancelledTrips,
        )
    }

    private object K {
        val excKm = longPreferencesKey("excellent_cents_per_km")
        val excHour = longPreferencesKey("excellent_cents_per_hour")
        val accKm = longPreferencesKey("acceptable_cents_per_km")
        val accHour = longPreferencesKey("acceptable_cents_per_hour")
        val fuelPrice = longPreferencesKey("fuel_price_cents")
        val kwhPrice = longPreferencesKey("kwh_price_cents")
        val currentFuel = stringPreferencesKey("current_fuel")
        val observed = booleanPreferencesKey("use_observed_consumption")
        val otherCost = doublePreferencesKey("other_cents_per_km")
        val gps = booleanPreferencesKey("gps_enabled")
        val overlay = booleanPreferencesKey("overlay_enabled")
        val offerDisplay = stringPreferencesKey("offer_display")
        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val overlayExpanded = booleanPreferencesKey("overlay_expanded")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val retention = intPreferencesKey("snapshot_retention_days")
        val sendSamples = booleanPreferencesKey("send_samples")
        val shareStats = booleanPreferencesKey("share_stats")
        val installId = stringPreferencesKey("install_id")
        val lastUpdateCheck = longPreferencesKey("last_update_check_at")
        val repairedCancelled = booleanPreferencesKey("repaired_cancelled_trips")
    }
}
