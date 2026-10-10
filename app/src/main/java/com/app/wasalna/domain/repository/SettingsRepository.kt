package com.app.wasalna.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val isOnboardingCompleted: Flow<Boolean>

    val isElderlyMode: Flow<Boolean>

    val alarmLeadTimeMinutes: Flow<Int>

    val isPremium: Flow<Boolean>

    val trialEndTimeMillis: Flow<Long>

    val isSilentAlarmEnabled: Flow<Boolean>

    val isHeadphoneOnlyEnabled: Flow<Boolean>

    val preferredMapEngine: Flow<String>

    val transportMode: Flow<String>

    val hasSeenAutoStartGuidance: Flow<Boolean>

    val isProactiveAlertEnabled: Flow<Boolean>

    val isAdaptiveAlarmEnabled: Flow<Boolean>

    val customRingtoneUri: Flow<String>

    val isForceOffline: Flow<Boolean>

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
    val isTrafficEnabled: Flow<Boolean>

    val appLanguageCode: Flow<String>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setElderlyMode(enabled: Boolean)

    suspend fun setAlarmLeadTimeMinutes(minutes: Int)

    suspend fun setPremium(premium: Boolean)

    suspend fun startTrial(daysFromNow: Int)

    suspend fun enforceTrialExpiry()

    suspend fun setHeadphoneOnlyEnabled(enabled: Boolean)

    suspend fun setSilentAlarmEnabled(enabled: Boolean)

    suspend fun setTransportMode(mode: String)

    suspend fun setHasSeenAutoStartGuidance(seen: Boolean)

    suspend fun setProactiveAlertEnabled(enabled: Boolean)

    suspend fun setAdaptiveAlarmEnabled(enabled: Boolean)

    suspend fun setCustomRingtoneUri(uri: String)

    suspend fun setHome(lat: Double?, lng: Double?, address: String)

    suspend fun setWork(lat: Double?, lng: Double?, address: String)

    suspend fun setForceOffline(enabled: Boolean)

    suspend fun setGradualAlarmEnabled(enabled: Boolean)
    suspend fun setGradualVibrationSec(seconds: Int)
    suspend fun setGradualRampSec(seconds: Int)
    suspend fun setGradualVibrationStrength(level: Int)
    suspend fun setGradualRingtoneUri(uri: String)

    suspend fun setTrafficEnabled(enabled: Boolean)

    suspend fun setAppLanguageCode(code: String)

    suspend fun setPreferredMapEngine(engine: String)
}
