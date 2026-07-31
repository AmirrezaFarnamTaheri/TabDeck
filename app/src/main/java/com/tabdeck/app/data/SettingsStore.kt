package com.tabdeck.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tabdeck.app.model.AccentStyle
import com.tabdeck.app.model.AppSettings
import com.tabdeck.app.model.BridgeScope
import com.tabdeck.app.model.BridgeSession
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.LibraryLayout
import com.tabdeck.app.model.SyncMissingPolicy
import com.tabdeck.app.model.ThemeMode
import com.tabdeck.app.model.TransferPacing
import com.tabdeck.app.model.ViewDensity
import com.tabdeck.app.model.newBridgeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tabdeck")

class SettingsStore(private val context: Context) {
    private val defaults = AppSettings()
    private object Keys {
        val legacySnapshot = stringPreferencesKey("snapshot_json_v1")
        val migratedV2 = booleanPreferencesKey("legacy_migrated_v2")
        val bridgeToken = stringPreferencesKey("bridge_token_v2")
        val bridgeSessionMinutes = intPreferencesKey("bridge_session_minutes")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val autoCategorize = booleanPreferencesKey("auto_categorize")
        val stripTracking = booleanPreferencesKey("strip_tracking")
        val dedupeMode = stringPreferencesKey("default_dedupe_mode")
        val keepPolicy = stringPreferencesKey("default_keep_policy")
        val transferPacing = stringPreferencesKey("transfer_pacing")
        val transferBatchLimit = intPreferencesKey("transfer_batch_limit")
        val viewDensity = stringPreferencesKey("view_density")
        val libraryLayout = stringPreferencesKey("library_layout")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accentStyle = stringPreferencesKey("accent_style")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val hapticFeedback = booleanPreferencesKey("haptic_feedback")
        val syncMissingPolicy = stringPreferencesKey("sync_missing_policy")
        val staleAfterDays = intPreferencesKey("stale_after_days")
        val showAdvancedControls = booleanPreferencesKey("show_advanced_controls")
        val bridgeEnabled = booleanPreferencesKey("bridge_enabled")
        val bridgeStartedAt = longPreferencesKey("bridge_started_at")
        val bridgeExpiresAt = longPreferencesKey("bridge_expires_at")
        val bridgeAccepted = intPreferencesKey("bridge_accepted")
        val bridgeRejected = intPreferencesKey("bridge_rejected")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map(::toSettings)
    val bridgeSession: Flow<BridgeSession> = context.settingsDataStore.data.map(::toBridgeSession)

    suspend fun currentSettings(): AppSettings = settings.first()

    /** Persist generated defaults exactly once so secrets never change between collectors. */
    suspend fun ensureDefaults() {
        context.settingsDataStore.edit { prefs ->
            if (prefs[Keys.bridgeToken].isNullOrBlank()) {
                writeSettings(prefs, toSettings(prefs))
            }
        }
    }
    suspend fun currentBridgeSession(): BridgeSession = bridgeSession.first()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences -> writeSettings(preferences, transform(toSettings(preferences))) }
    }

    suspend fun setBridgeSession(session: BridgeSession) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.bridgeEnabled] = session.enabled
            session.startedAtEpochMs?.let { prefs[Keys.bridgeStartedAt] = it } ?: prefs.remove(Keys.bridgeStartedAt)
            session.expiresAtEpochMs?.let { prefs[Keys.bridgeExpiresAt] = it } ?: prefs.remove(Keys.bridgeExpiresAt)
            prefs[Keys.bridgeAccepted] = session.acceptedRequests
            prefs[Keys.bridgeRejected] = session.rejectedRequests
        }
    }

    suspend fun recordBridgeRequest(accepted: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val key = if (accepted) Keys.bridgeAccepted else Keys.bridgeRejected
            prefs[key] = (prefs[key] ?: 0) + 1
        }
    }

    suspend fun regenerateBridgeToken() = updateSettings { it.copy(bridgeToken = newBridgeToken()) }

    suspend fun legacyMigrationPayload(): Pair<Boolean, String?> {
        val prefs = context.settingsDataStore.data.first()
        return (prefs[Keys.migratedV2] ?: false) to prefs[Keys.legacySnapshot]
    }

    suspend fun finishLegacyMigration() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.migratedV2] = true
            prefs.remove(Keys.legacySnapshot)
        }
    }

    suspend fun reset() {
        context.settingsDataStore.edit { prefs ->
            val migrated = prefs[Keys.migratedV2] ?: true
            prefs.clear()
            prefs[Keys.migratedV2] = migrated
            writeSettings(prefs, AppSettings())
        }
    }

    private fun toSettings(prefs: Preferences): AppSettings = AppSettings(
        bridgeToken = prefs[Keys.bridgeToken].orEmpty().ifBlank { defaults.bridgeToken },
        bridgeScope = BridgeScope.THIS_DEVICE,
        bridgeSessionMinutes = (prefs[Keys.bridgeSessionMinutes] ?: 20).coerceIn(5, 120),
        onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
        autoCategorizeImports = prefs[Keys.autoCategorize] ?: true,
        stripTrackingParameters = prefs[Keys.stripTracking] ?: true,
        defaultDedupeMode = enumOrDefault(prefs[Keys.dedupeMode], DedupeMode.NORMALIZED_URL),
        defaultKeepPolicy = enumOrDefault(prefs[Keys.keepPolicy], KeepPolicy.RICHEST),
        transferPacing = enumOrDefault(prefs[Keys.transferPacing], TransferPacing.BALANCED),
        transferBatchLimit = (prefs[Keys.transferBatchLimit] ?: 80).coerceIn(1, 250),
        viewDensity = enumOrDefault(prefs[Keys.viewDensity], ViewDensity.COMFORTABLE),
        libraryLayout = enumOrDefault(prefs[Keys.libraryLayout], LibraryLayout.LIST),
        themeMode = enumOrDefault(prefs[Keys.themeMode], ThemeMode.SYSTEM),
        dynamicColor = prefs[Keys.dynamicColor] ?: true,
        accentStyle = enumOrDefault(prefs[Keys.accentStyle], AccentStyle.VIOLET),
        reduceMotion = prefs[Keys.reduceMotion] ?: false,
        hapticFeedback = prefs[Keys.hapticFeedback] ?: true,
        syncMissingPolicy = enumOrDefault(prefs[Keys.syncMissingPolicy], SyncMissingPolicy.KEEP),
        staleAfterDays = (prefs[Keys.staleAfterDays] ?: 30).coerceIn(1, 3650),
        showAdvancedControls = prefs[Keys.showAdvancedControls] ?: false,
    )

    private fun toBridgeSession(prefs: Preferences): BridgeSession {
        val expiresAt = prefs[Keys.bridgeExpiresAt]
        val stillValid = expiresAt == null || expiresAt > System.currentTimeMillis()
        return BridgeSession(
            enabled = (prefs[Keys.bridgeEnabled] ?: false) && stillValid,
            startedAtEpochMs = prefs[Keys.bridgeStartedAt],
            expiresAtEpochMs = expiresAt,
            acceptedRequests = prefs[Keys.bridgeAccepted] ?: 0,
            rejectedRequests = prefs[Keys.bridgeRejected] ?: 0,
        )
    }

    private fun writeSettings(prefs: androidx.datastore.preferences.core.MutablePreferences, value: AppSettings) {
        prefs[Keys.bridgeToken] = value.bridgeToken
        prefs[Keys.bridgeSessionMinutes] = value.bridgeSessionMinutes.coerceIn(5, 120)
        prefs[Keys.onboardingComplete] = value.onboardingComplete
        prefs[Keys.autoCategorize] = value.autoCategorizeImports
        prefs[Keys.stripTracking] = value.stripTrackingParameters
        prefs[Keys.dedupeMode] = value.defaultDedupeMode.name
        prefs[Keys.keepPolicy] = value.defaultKeepPolicy.name
        prefs[Keys.transferPacing] = value.transferPacing.name
        prefs[Keys.transferBatchLimit] = value.transferBatchLimit.coerceIn(1, 250)
        prefs[Keys.viewDensity] = value.viewDensity.name
        prefs[Keys.libraryLayout] = value.libraryLayout.name
        prefs[Keys.themeMode] = value.themeMode.name
        prefs[Keys.dynamicColor] = value.dynamicColor
        prefs[Keys.accentStyle] = value.accentStyle.name
        prefs[Keys.reduceMotion] = value.reduceMotion
        prefs[Keys.hapticFeedback] = value.hapticFeedback
        prefs[Keys.syncMissingPolicy] = value.syncMissingPolicy.name
        prefs[Keys.staleAfterDays] = value.staleAfterDays.coerceIn(1, 3650)
        prefs[Keys.showAdvancedControls] = value.showAdvancedControls
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)
}
