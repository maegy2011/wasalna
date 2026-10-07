package com.app.wasalna.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wasalna_settings",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore: DataStore<Preferences>
        get() = context.settingsDataStore

    val isOnboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    val isElderlyMode: Flow<Boolean> =
        dataStore.data.map { it[Keys.ELDERLY_MODE] ?: false }

    val alarmLeadTimeMinutes: Flow<Int> =
        dataStore.data.map { it[Keys.ALARM_LEAD_TIME_MINUTES] ?: 5 }

    val isPremium: Flow<Boolean> =
        dataStore.data.map { it[Keys.PREMIUM] ?: false }

    // Phase 10.4.1: trial expiry timestamp (epoch millis). 0 = no trial.
    val trialEndTimeMillis: Flow<Long> =
        dataStore.data.map { it[Keys.TRIAL_END_TIME] ?: 0L }

    val isSilentAlarmEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.SILENT_ALARM_ENABLED] ?: false }

    // Phase 9-1: preferred map engine ("OSM" free default, "GOOGLE" premium).
    // Phase 10-1: "OSM" is now rendered by MapLibre + OpenFreeMap (value kept
    // for DataStore compatibility — no migration needed).
    val preferredMapEngine: Flow<String> =
        dataStore.data.map { it[Keys.PREFERRED_MAP_ENGINE] ?: MapEngines.OSM }

    // Phase 10.4.2: persisted transport mode (WALK/CAR/TRAIN name).
    val transportMode: Flow<String> =
        dataStore.data.map { it[Keys.TRANSPORT_MODE] ?: "CAR" }

    // Phase 10.4.3.1: premium custom alarm ringtone (URI string, "" = none).
    val customRingtoneUri: Flow<String> =
        dataStore.data.map { it[Keys.CUSTOM_RINGTONE_URI] ?: "" }

    // Phase 10.4.3.3: adaptive trigger distance per transport mode.
    val isAdaptiveAlarmEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.ADAPTIVE_ALARM_ENABLED] ?: true }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setElderlyMode(enabled: Boolean) {
        dataStore.edit { it[Keys.ELDERLY_MODE] = enabled }
    }

    suspend fun setAlarmLeadTimeMinutes(minutes: Int) {
        dataStore.edit { it[Keys.ALARM_LEAD_TIME_MINUTES] = minutes }
    }

    suspend fun setPremium(premium: Boolean) {
        dataStore.edit { it[Keys.PREMIUM] = premium }
    }

    /** Starts a trial: premium ON with an expiry [daysFromNow] days ahead. */
    suspend fun startTrial(daysFromNow: Int) {
        val end = System.currentTimeMillis() + daysFromNow * 24L * 60L * 60L * 1000L
        dataStore.edit {
            it[Keys.PREMIUM] = true
            it[Keys.TRIAL_END_TIME] = end
        }
    }

    /** Reverts premium when a trial has expired. Call on app/drawer start. */
    suspend fun enforceTrialExpiry(now: Long = System.currentTimeMillis()) {
        val prefs = dataStore.data.first()
        val premium = prefs[Keys.PREMIUM] ?: false
        val end = prefs[Keys.TRIAL_END_TIME] ?: 0L
        if (premium && end > 0L && now >= end) {
            dataStore.edit {
                it[Keys.PREMIUM] = false
                it[Keys.TRIAL_END_TIME] = 0L
            }
        }
    }

    suspend fun setSilentAlarmEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SILENT_ALARM_ENABLED] = enabled }
    }

    suspend fun setPreferredMapEngine(engine: String) {
        val normalized = if (engine == MapEngines.GOOGLE) MapEngines.GOOGLE else MapEngines.OSM
        dataStore.edit { it[Keys.PREFERRED_MAP_ENGINE] = normalized }
    }

    suspend fun setTransportMode(mode: String) {
        dataStore.edit { it[Keys.TRANSPORT_MODE] = mode }
    }

    suspend fun setCustomRingtoneUri(uri: String) {
        dataStore.edit { it[Keys.CUSTOM_RINGTONE_URI] = uri }
    }

    suspend fun setAdaptiveAlarmEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ADAPTIVE_ALARM_ENABLED] = enabled }
    }

    /** Phase 9-1: canonical map-engine values. */
    object MapEngines {
        const val OSM = "OSM"
        const val GOOGLE = "GOOGLE"
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val ELDERLY_MODE = booleanPreferencesKey("is_elderly_mode")
        val ALARM_LEAD_TIME_MINUTES = intPreferencesKey("alarm_lead_time_minutes")
        val PREMIUM = booleanPreferencesKey("is_premium")
        val TRIAL_END_TIME = longPreferencesKey("trial_end_time")
        val SILENT_ALARM_ENABLED = booleanPreferencesKey("is_silent_alarm_enabled")
        val PREFERRED_MAP_ENGINE = stringPreferencesKey("preferred_map_engine")
        val TRANSPORT_MODE = stringPreferencesKey("transport_mode")
        val CUSTOM_RINGTONE_URI = stringPreferencesKey("custom_ringtone_uri")
        val ADAPTIVE_ALARM_ENABLED = booleanPreferencesKey("adaptive_alarm_enabled")
    }
}
