package com.app.wasalna.domain.repository

import com.app.wasalna.domain.model.CustomShortcut
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val isOnboardingCompleted: Flow<Boolean>

    val isElderlyMode: Flow<Boolean>

    val alarmLeadTimeMinutes: Flow<Int>

    val isPremium: Flow<Boolean>

    val trialEndTimeMillis: Flow<Long>

    val isSilentAlarmEnabled: Flow<Boolean>

    val isWatchOnlyAlert: Flow<Boolean>

    val isHeadphoneOnlyEnabled: Flow<Boolean>

    val preferredMapEngine: Flow<String>

    val transportMode: Flow<String>

    val hasSeenAutoStartGuidance: Flow<Boolean>

    val isProactiveAlertEnabled: Flow<Boolean>

    // Pre-arrival NUDGE lead (minutes, 1..10, default 3).
    val nudgeLeadMinutes: Flow<Int>

    val isAdaptiveAlarmEnabled: Flow<Boolean>

    // DYNAMIC WAKE-UP: DISTANCE (legacy default) vs TIME (minutes-before-arrival).
    val alarmMode: Flow<String>
    val alarmTimeMinutes: Flow<Int>

    val customRingtoneUri: Flow<String>

    // Voice announcements (Oct 2026): system TTS + custom voice.
    val isVoiceAnnounceEnabled: Flow<Boolean>
    val voiceName: Flow<String>

    // Dynamic routing data (Oct 2026): Wi-Fi auto-fetch of trip graphs.
    val isAutoGraphDownloadEnabled: Flow<Boolean>

    val isForceOffline: Flow<Boolean>

    val isWizardSeen: Flow<Boolean>

    val isSimulationVisible: Flow<Boolean>
    suspend fun setSimulationVisible(visible: Boolean)

    val isGradualAlarmEnabled: Flow<Boolean>
    val gradualVibrationSec: Flow<Int>
    val gradualRampSec: Flow<Int>
    val gradualVibrationStrength: Flow<Int>
    val gradualRingtoneUri: Flow<String>

    val homeLat: Flow<Double?>
    val homeLng: Flow<Double?>
    val homeAddress: Flow<String>
    val workLat: Flow<Double?>
    val workLng: Flow<Double?>
    val workAddress: Flow<String>
    // Premium custom shortcuts (user order Oct 2026): user-named quick
    // places beside Home & Work.
    val customShortcuts: Flow<List<CustomShortcut>>
    val isTrafficEnabled: Flow<Boolean>

    val appLanguageCode: Flow<String>

    // TRANSIT BYOK: user's own Google Routes API v2 key ("" = none).
    val routesApiKey: Flow<String>

    // Silent-mode (DND) bypass choice — persisted, default RESPECT (safe).
    val dndChoice: Flow<String>

    // Phase 10.13-exp: Google account (local-only, "" = signed out).
    val accountDisplayName: Flow<String>
    val accountEmail: Flow<String>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setElderlyMode(enabled: Boolean)

    suspend fun setAlarmLeadTimeMinutes(minutes: Int)

    suspend fun setPremium(premium: Boolean)

    suspend fun startTrial(daysFromNow: Int)

    suspend fun enforceTrialExpiry()

    suspend fun setHeadphoneOnlyEnabled(enabled: Boolean)

    suspend fun setSilentAlarmEnabled(enabled: Boolean)

    suspend fun setDndChoice(choice: String)

    suspend fun setWatchOnlyAlert(enabled: Boolean)

    suspend fun setTransportMode(mode: String)

    suspend fun setHasSeenAutoStartGuidance(seen: Boolean)

    suspend fun setNudgeLeadMinutes(minutes: Int)

    suspend fun setProactiveAlertEnabled(enabled: Boolean)

    suspend fun setAdaptiveAlarmEnabled(enabled: Boolean)

    suspend fun setAlarmMode(mode: String)
    suspend fun setAlarmTimeMinutes(minutes: Int)

    suspend fun setCustomRingtoneUri(uri: String)

    suspend fun setVoiceAnnounceEnabled(enabled: Boolean)
    suspend fun setVoiceName(name: String)
    suspend fun setAutoGraphDownloadEnabled(enabled: Boolean)

    suspend fun setHome(lat: Double?, lng: Double?, address: String)

    suspend fun setWork(lat: Double?, lng: Double?, address: String)

    suspend fun setCustomShortcuts(shortcuts: List<CustomShortcut>)

    suspend fun setForceOffline(enabled: Boolean)

    suspend fun setWizardSeen(seen: Boolean)

    suspend fun setGradualAlarmEnabled(enabled: Boolean)
    suspend fun setGradualVibrationSec(seconds: Int)
    suspend fun setGradualRampSec(seconds: Int)
    suspend fun setGradualVibrationStrength(level: Int)
    suspend fun setGradualRingtoneUri(uri: String)

    suspend fun setTrafficEnabled(enabled: Boolean)

    val isTransitLayerEnabled: Flow<Boolean>
    suspend fun setTransitLayerEnabled(enabled: Boolean)

    val isAqiLayerEnabled: Flow<Boolean>
    suspend fun setAqiLayerEnabled(enabled: Boolean)

    val waqiApiKey: Flow<String>
    suspend fun setWaqiApiKey(key: String)

    // Temperature layer (Oct 2026): OpenWeatherMap BYOK + toggle + opacity.
    val isTempLayerEnabled: Flow<Boolean>
    suspend fun setTempLayerEnabled(enabled: Boolean)
    val owmApiKey: Flow<String>
    suspend fun setOwmApiKey(key: String)
    val tempOpacity: Flow<Float>
    suspend fun setTempOpacity(opacity: Float)

    suspend fun setRoutesApiKey(key: String)

    suspend fun setAppLanguageCode(code: String)

    suspend fun setGoogleAccount(displayName: String, email: String)

    suspend fun clearGoogleAccount()

    suspend fun setPreferredMapEngine(engine: String)
}
