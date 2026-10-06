package com.app.wasalna.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val ELDERLY_MODE = booleanPreferencesKey("is_elderly_mode")
        val ALARM_LEAD_TIME_MINUTES = intPreferencesKey("alarm_lead_time_minutes")
        val PREMIUM = booleanPreferencesKey("is_premium")
    }
}
