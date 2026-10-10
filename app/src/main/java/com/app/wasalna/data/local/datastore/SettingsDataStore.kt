package com.app.wasalna.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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

    // Headphone-only alerts (premium): the destination alarm plays ONLY
    // through connected headphones; the phone speaker stays silent.
    val isHeadphoneOnlyEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.HEADPHONE_ONLY_ENABLED] ?: false }

    // Preferred map engine ("OSM" free default). Kept for DataStore
    // compatibility — the app renders 100% MapLibre + OpenFreeMap.
    val preferredMapEngine: Flow<String> =
        dataStore.data.map { it[Keys.PREFERRED_MAP_ENGINE] ?: MapEngines.OSM }

    // Phase 10.4.3.6: strict-OEM guidance shown once.
    val hasSeenAutoStartGuidance: Flow<Boolean> =
        dataStore.data.map { it[Keys.HAS_SEEN_AUTOSTART_GUIDANCE] ?: false }

    // Phase 10.4.3.4: gentle heads-up ~3 min before the main alarm.
    val isProactiveAlertEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.PROACTIVE_ALERT_ENABLED] ?: true }

    // Pre-arrival NUDGE (silent): lead minutes before arrival (1..10,
    // default 3). Strictly-earlier companion of the main alarm — see
    // TrackingService.evaluateNudge for the ordering validation.
    val nudgeLeadMinutes: Flow<Int> =
        dataStore.data.map { it[Keys.NUDGE_LEAD_MINUTES]?.coerceIn(1, 10) ?: 3 }

    // Phase 10.4.2: persisted transport mode (WALK/CAR/TRAIN name).
    val transportMode: Flow<String> =
        dataStore.data.map { it[Keys.TRANSPORT_MODE] ?: "CAR" }

    // Phase 10.4.3.1: premium custom alarm ringtone (URI string, "" = none).
    val customRingtoneUri: Flow<String> =
        dataStore.data.map { it[Keys.CUSTOM_RINGTONE_URI] ?: "" }

    // Phase 10.4.3.3: adaptive trigger distance per transport mode.
    val isAdaptiveAlarmEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.ADAPTIVE_ALARM_ENABLED] ?: true }

    // DYNAMIC WAKE-UP (التنبيه المرتبط بالزحام): DISTANCE = legacy radius
    // behavior (default, unchanged); TIME = fire N minutes before arrival
    // from live ETA. New keys only — existing installs keep DISTANCE.
    val alarmMode: Flow<String> =
        dataStore.data.map {
            val raw = it[Keys.ALARM_MODE] ?: AlarmModes.DISTANCE
            if (raw == AlarmModes.TIME) AlarmModes.TIME else AlarmModes.DISTANCE
        }

    // Minutes-before-arrival for TIME mode (1..30, default 5).
    val alarmTimeMinutes: Flow<Int> =
        dataStore.data.map { it[Keys.ALARM_TIME_MINUTES]?.coerceIn(1, 30) ?: 5 }

    // Silent-mode (DND) bypass choice — persisted 3-way value
    // (respect/bypass/ask). Default RESPECT (safe, zero behaviour change).
    val dndChoice: Flow<String> =
        dataStore.data.map { com.app.wasalna.utils.DndChoice.normalize(it[Keys.DND_CHOICE]) }

    suspend fun setDndChoice(choice: String) {
        val normalized = com.app.wasalna.utils.DndChoice.normalize(choice)
        dataStore.edit { it[Keys.DND_CHOICE] = normalized }
    }

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

    // Phase 10.10: watch-only destination alert (PREMIUM-gated). When true
    // and a paired watch with the companion app is reachable, the phone
    // stays silent and only the watch vibrates/rings. Default false →
    // legacy phone alarm path unchanged.
    val isWatchOnlyAlert: Flow<Boolean> =
        dataStore.data.map { it[Keys.WATCH_ONLY_ALERT] ?: false }

    suspend fun setWatchOnlyAlert(enabled: Boolean) {
        dataStore.edit { it[Keys.WATCH_ONLY_ALERT] = enabled }
    }

    suspend fun setHeadphoneOnlyEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HEADPHONE_ONLY_ENABLED] = enabled }
    }

    suspend fun setPreferredMapEngine(engine: String) {
        val normalized = if (engine == MapEngines.GOOGLE) MapEngines.GOOGLE else MapEngines.OSM
        dataStore.edit { it[Keys.PREFERRED_MAP_ENGINE] = normalized }
    }

    suspend fun setHasSeenAutoStartGuidance(seen: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_AUTOSTART_GUIDANCE] = seen }
    }

    suspend fun setProactiveAlertEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PROACTIVE_ALERT_ENABLED] = enabled }
    }

    suspend fun setNudgeLeadMinutes(minutes: Int) {
        dataStore.edit { it[Keys.NUDGE_LEAD_MINUTES] = minutes.coerceIn(1, 10) }
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

    suspend fun setAlarmMode(mode: String) {
        val normalized = if (mode == AlarmModes.TIME) AlarmModes.TIME else AlarmModes.DISTANCE
        dataStore.edit { it[Keys.ALARM_MODE] = normalized }
    }

    suspend fun setAlarmTimeMinutes(minutes: Int) {
        dataStore.edit { it[Keys.ALARM_TIME_MINUTES] = minutes.coerceIn(1, 30) }
    }

    /** Canonical dynamic-wake-up mode values. */
    object AlarmModes {
        const val DISTANCE = "DISTANCE"
        const val TIME = "TIME"
    }

    // Map UX fixes: Home & Work quick places (lat/lng + address, nullable = unset).
    val homeLat: Flow<Double?> =
        dataStore.data.map { it[Keys.HOME_LAT] }
    val homeLng: Flow<Double?> =
        dataStore.data.map { it[Keys.HOME_LNG] }
    val homeAddress: Flow<String> =
        dataStore.data.map { it[Keys.HOME_ADDRESS] ?: "" }
    val workLat: Flow<Double?> =
        dataStore.data.map { it[Keys.WORK_LAT] }
    val workLng: Flow<Double?> =
        dataStore.data.map { it[Keys.WORK_LNG] }
    val workAddress: Flow<String> =
        dataStore.data.map { it[Keys.WORK_ADDRESS] ?: "" }

    suspend fun setHome(lat: Double?, lng: Double?, address: String) {
        dataStore.edit {
            if (lat == null || lng == null) {
                it.remove(Keys.HOME_LAT)
                it.remove(Keys.HOME_LNG)
            } else {
                it[Keys.HOME_LAT] = lat
                it[Keys.HOME_LNG] = lng
            }
            it[Keys.HOME_ADDRESS] = address
        }
    }

    suspend fun setWork(lat: Double?, lng: Double?, address: String) {
        dataStore.edit {
            if (lat == null || lng == null) {
                it.remove(Keys.WORK_LAT)
                it.remove(Keys.WORK_LNG)
            } else {
                it[Keys.WORK_LAT] = lat
                it[Keys.WORK_LNG] = lng
            }
            it[Keys.WORK_ADDRESS] = address
        }
    }

    // Map UX fixes: traffic overlay toggle (persisted; MapLibre free tiles
    // show a graceful note — see Phase 2e note in MapScreen).
    val isTrafficEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.TRAFFIC_ENABLED] ?: false }

    suspend fun setTrafficEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TRAFFIC_ENABLED] = enabled }
    }

    // In-app language code ("" = follow device, "ar", "en"). Default ""
    // follows the device language automatically — no first-run write needed.
    val appLanguageCode: Flow<String> =
        dataStore.data.map { it[Keys.APP_LANGUAGE_CODE] ?: "" }

    suspend fun setAppLanguageCode(code: String) {
        val normalized = if (code == "ar" || code == "en") code else ""
        dataStore.edit { it[Keys.APP_LANGUAGE_CODE] = normalized }
    }

    // True offline mode (GPS-chip only) — user-facing flag, single source
    // of truth for the header cloud button + Settings toggle (two-way
    // synced, same key). When true, TrackingService + MapViewModel bypass
    // ALL network repositories (OSRM, Photon/search, Nominatim, traffic
    // probes + raster) and use GPS fixes + offline tiles + the local
    // GraphHopper engine (with GPS straight-line fallback).
    val isForceOffline: Flow<Boolean> =
        dataStore.data.map { it[Keys.FORCE_OFFLINE] ?: false }

    suspend fun setForceOffline(enabled: Boolean) {
        dataStore.edit { it[Keys.FORCE_OFFLINE] = enabled }
    }

    /** Canonical map-engine values (legacy — app is 100% MapLibre). */
    object MapEngines {
        const val OSM = "OSM"
        const val GOOGLE = "GOOGLE"
    }

    // Phase 10.9.x: smart gradual alarm — vibration first, then a quiet
    // sound that ramps up slowly (wake without startling). Disabled by
    // default → legacy full-volume path unchanged when off.
    val isGradualAlarmEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.GRADUAL_ALARM_ENABLED] ?: false }

    // Phase 1 duration: strong vibration only, no sound (seconds, 5..20).
    val gradualVibrationSec: Flow<Int> =
        dataStore.data.map { it[Keys.GRADUAL_VIBRATION_SEC]?.coerceIn(5, 20) ?: 10 }

    // Phase 2 duration: ramp STREAM_ALARM from ~10% to max (seconds, 15..60).
    val gradualRampSec: Flow<Int> =
        dataStore.data.map { it[Keys.GRADUAL_RAMP_SEC]?.coerceIn(15, 60) ?: 35 }

    // Vibration strength level: 1 = medium, 2 = strong, 3 = maximum.
    val gradualVibrationStrength: Flow<Int> =
        dataStore.data.map { it[Keys.GRADUAL_VIBRATION_STRENGTH]?.coerceIn(1, 3) ?: 3 }

    // Gradual-alarm tone (URI string, "" = fall back to customRingtoneUri,
    // then the system alarm tone). Set from the in-app ringtone picker.
    val gradualRingtoneUri: Flow<String> =
        dataStore.data.map { it[Keys.GRADUAL_RINGTONE_URI] ?: "" }

    suspend fun setGradualAlarmEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GRADUAL_ALARM_ENABLED] = enabled }
    }

    suspend fun setGradualVibrationSec(seconds: Int) {
        dataStore.edit { it[Keys.GRADUAL_VIBRATION_SEC] = seconds.coerceIn(5, 20) }
    }

    suspend fun setGradualRampSec(seconds: Int) {
        dataStore.edit { it[Keys.GRADUAL_RAMP_SEC] = seconds.coerceIn(15, 60) }
    }

    suspend fun setGradualVibrationStrength(level: Int) {
        dataStore.edit { it[Keys.GRADUAL_VIBRATION_STRENGTH] = level.coerceIn(1, 3) }
    }

    suspend fun setGradualRingtoneUri(uri: String) {
        dataStore.edit { it[Keys.GRADUAL_RINGTONE_URI] = uri }
    }

    // Phase 10.13-exp: Google account sign-in (EXPERIMENTAL, local-only).
    // Display name + email only — no tokens, no server upload, no secrets.
    // "" = signed out. New keys — no duplicates, no behavior change.
    val accountDisplayName: Flow<String> =
        dataStore.data.map { it[Keys.ACCOUNT_DISPLAY_NAME] ?: "" }
    val accountEmail: Flow<String> =
        dataStore.data.map { it[Keys.ACCOUNT_EMAIL] ?: "" }

    suspend fun setGoogleAccount(displayName: String, email: String) {
        dataStore.edit {
            it[Keys.ACCOUNT_DISPLAY_NAME] = displayName
            it[Keys.ACCOUNT_EMAIL] = email
        }
    }

    suspend fun clearGoogleAccount() {
        dataStore.edit {
            it.remove(Keys.ACCOUNT_DISPLAY_NAME)
            it.remove(Keys.ACCOUNT_EMAIL)
        }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val ELDERLY_MODE = booleanPreferencesKey("is_elderly_mode")
        val ALARM_LEAD_TIME_MINUTES = intPreferencesKey("alarm_lead_time_minutes")
        val PREMIUM = booleanPreferencesKey("is_premium")
        val TRIAL_END_TIME = longPreferencesKey("trial_end_time")
        val SILENT_ALARM_ENABLED = booleanPreferencesKey("is_silent_alarm_enabled")
        val WATCH_ONLY_ALERT = booleanPreferencesKey("watch_only_alert_enabled")
        val HEADPHONE_ONLY_ENABLED = booleanPreferencesKey("headphone_only_enabled")
        val PREFERRED_MAP_ENGINE = stringPreferencesKey("preferred_map_engine")
        val TRANSPORT_MODE = stringPreferencesKey("transport_mode")
        val PROACTIVE_ALERT_ENABLED = booleanPreferencesKey("proactive_alert_enabled")
        val NUDGE_LEAD_MINUTES = intPreferencesKey("nudge_lead_minutes")
        val HAS_SEEN_AUTOSTART_GUIDANCE = booleanPreferencesKey("has_seen_autostart_guidance")
        val CUSTOM_RINGTONE_URI = stringPreferencesKey("custom_ringtone_uri")
        val ADAPTIVE_ALARM_ENABLED = booleanPreferencesKey("adaptive_alarm_enabled")
        val ALARM_MODE = stringPreferencesKey("alarm_mode")
        val ALARM_TIME_MINUTES = intPreferencesKey("alarm_time_minutes")
        val HOME_LAT = doublePreferencesKey("home_lat")
        val HOME_LNG = doublePreferencesKey("home_lng")
        val HOME_ADDRESS = stringPreferencesKey("home_address")
        val WORK_LAT = doublePreferencesKey("work_lat")
        val WORK_LNG = doublePreferencesKey("work_lng")
        val WORK_ADDRESS = stringPreferencesKey("work_address")
        val TRAFFIC_ENABLED = booleanPreferencesKey("traffic_enabled")
        val APP_LANGUAGE_CODE = stringPreferencesKey("app_language_code")
        val FORCE_OFFLINE = booleanPreferencesKey("force_offline_routing")
        val GRADUAL_ALARM_ENABLED = booleanPreferencesKey("gradual_alarm_enabled")
        val GRADUAL_VIBRATION_SEC = intPreferencesKey("gradual_vibration_sec")
        val GRADUAL_RAMP_SEC = intPreferencesKey("gradual_ramp_sec")
        val GRADUAL_VIBRATION_STRENGTH = intPreferencesKey("gradual_vibration_strength")
        val GRADUAL_RINGTONE_URI = stringPreferencesKey("gradual_ringtone_uri")
        val ACCOUNT_DISPLAY_NAME = stringPreferencesKey("account_display_name")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val DND_CHOICE = stringPreferencesKey("dnd_choice")
    }
}
