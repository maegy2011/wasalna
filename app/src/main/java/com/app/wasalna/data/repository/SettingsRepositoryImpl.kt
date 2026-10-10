package com.app.wasalna.data.repository

import com.app.wasalna.data.local.datastore.SettingsDataStore
import com.app.wasalna.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : SettingsRepository {

    override val isOnboardingCompleted: Flow<Boolean>
        get() = settingsDataStore.isOnboardingCompleted

    override val isElderlyMode: Flow<Boolean>
        get() = settingsDataStore.isElderlyMode

    override val alarmLeadTimeMinutes: Flow<Int>
        get() = settingsDataStore.alarmLeadTimeMinutes

    override val isPremium: Flow<Boolean>
        get() = settingsDataStore.isPremium

    override val trialEndTimeMillis: Flow<Long>
        get() = settingsDataStore.trialEndTimeMillis

    override val isSilentAlarmEnabled: Flow<Boolean>
        get() = settingsDataStore.isSilentAlarmEnabled

    override val isHeadphoneOnlyEnabled: Flow<Boolean>
        get() = settingsDataStore.isHeadphoneOnlyEnabled

    override val preferredMapEngine: Flow<String>
        get() = settingsDataStore.preferredMapEngine

    override val transportMode: Flow<String>
        get() = settingsDataStore.transportMode

    override val hasSeenAutoStartGuidance: Flow<Boolean>
        get() = settingsDataStore.hasSeenAutoStartGuidance

    override val isProactiveAlertEnabled: Flow<Boolean>
        get() = settingsDataStore.isProactiveAlertEnabled

    override val isAdaptiveAlarmEnabled: Flow<Boolean>
        get() = settingsDataStore.isAdaptiveAlarmEnabled

    override val customRingtoneUri: Flow<String>
        get() = settingsDataStore.customRingtoneUri

    override val homeLat: Flow<Double?>
        get() = settingsDataStore.homeLat
    override val homeLng: Flow<Double?>
        get() = settingsDataStore.homeLng
    override val homeAddress: Flow<String>
        get() = settingsDataStore.homeAddress
    override val workLat: Flow<Double?>
        get() = settingsDataStore.workLat
    override val workLng: Flow<Double?>
        get() = settingsDataStore.workLng
    override val workAddress: Flow<String>
        get() = settingsDataStore.workAddress
    override val isTrafficEnabled: Flow<Boolean>
        get() = settingsDataStore.isTrafficEnabled

    override val appLanguageCode: Flow<String>
        get() = settingsDataStore.appLanguageCode

    override val isForceOffline: Flow<Boolean>
        get() = settingsDataStore.isForceOffline

    override val isGradualAlarmEnabled: Flow<Boolean>
        get() = settingsDataStore.isGradualAlarmEnabled
    override val gradualVibrationSec: Flow<Int>
        get() = settingsDataStore.gradualVibrationSec
    override val gradualRampSec: Flow<Int>
        get() = settingsDataStore.gradualRampSec
    override val gradualVibrationStrength: Flow<Int>
        get() = settingsDataStore.gradualVibrationStrength
    override val gradualRingtoneUri: Flow<String>
        get() = settingsDataStore.gradualRingtoneUri

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        settingsDataStore.setOnboardingCompleted(completed)
    }

    override suspend fun setElderlyMode(enabled: Boolean) {
        settingsDataStore.setElderlyMode(enabled)
    }

    override suspend fun setAlarmLeadTimeMinutes(minutes: Int) {
        settingsDataStore.setAlarmLeadTimeMinutes(minutes)
    }

    override suspend fun setPremium(premium: Boolean) {
        settingsDataStore.setPremium(premium)
    }

    override suspend fun startTrial(daysFromNow: Int) {
        settingsDataStore.startTrial(daysFromNow)
    }

    override suspend fun enforceTrialExpiry() {
        settingsDataStore.enforceTrialExpiry()
    }

    override suspend fun setHeadphoneOnlyEnabled(enabled: Boolean) {
        settingsDataStore.setHeadphoneOnlyEnabled(enabled)
    }

    override suspend fun setSilentAlarmEnabled(enabled: Boolean) {
        settingsDataStore.setSilentAlarmEnabled(enabled)
    }

    override suspend fun setPreferredMapEngine(engine: String) {
        settingsDataStore.setPreferredMapEngine(engine)
    }

    override suspend fun setTransportMode(mode: String) {
        settingsDataStore.setTransportMode(mode)
    }

    override suspend fun setHasSeenAutoStartGuidance(seen: Boolean) {
        settingsDataStore.setHasSeenAutoStartGuidance(seen)
    }

    override suspend fun setProactiveAlertEnabled(enabled: Boolean) {
        settingsDataStore.setProactiveAlertEnabled(enabled)
    }

    override suspend fun setAdaptiveAlarmEnabled(enabled: Boolean) {
        settingsDataStore.setAdaptiveAlarmEnabled(enabled)
    }

    override suspend fun setCustomRingtoneUri(uri: String) {
        settingsDataStore.setCustomRingtoneUri(uri)
    }

    override suspend fun setHome(lat: Double?, lng: Double?, address: String) {
        settingsDataStore.setHome(lat, lng, address)
    }

    override suspend fun setWork(lat: Double?, lng: Double?, address: String) {
        settingsDataStore.setWork(lat, lng, address)
    }

    override suspend fun setForceOffline(enabled: Boolean) {
        settingsDataStore.setForceOffline(enabled)
    }

    override suspend fun setGradualAlarmEnabled(enabled: Boolean) {
        settingsDataStore.setGradualAlarmEnabled(enabled)
    }

    override suspend fun setGradualVibrationSec(seconds: Int) {
        settingsDataStore.setGradualVibrationSec(seconds)
    }

    override suspend fun setGradualRampSec(seconds: Int) {
        settingsDataStore.setGradualRampSec(seconds)
    }

    override suspend fun setGradualVibrationStrength(level: Int) {
        settingsDataStore.setGradualVibrationStrength(level)
    }

    override suspend fun setGradualRingtoneUri(uri: String) {
        settingsDataStore.setGradualRingtoneUri(uri)
    }

    override suspend fun setTrafficEnabled(enabled: Boolean) {
        settingsDataStore.setTrafficEnabled(enabled)
    }

    override suspend fun setAppLanguageCode(code: String) {
        settingsDataStore.setAppLanguageCode(code)
    }
}
