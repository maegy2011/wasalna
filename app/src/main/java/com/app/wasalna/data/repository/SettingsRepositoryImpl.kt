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

    override val isSilentAlarmEnabled: Flow<Boolean>
        get() = settingsDataStore.isSilentAlarmEnabled

    override val preferredMapEngine: Flow<String>
        get() = settingsDataStore.preferredMapEngine

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

    override suspend fun setSilentAlarmEnabled(enabled: Boolean) {
        settingsDataStore.setSilentAlarmEnabled(enabled)
    }

    override suspend fun setPreferredMapEngine(engine: String) {
        settingsDataStore.setPreferredMapEngine(engine)
    }
}
