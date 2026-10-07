package com.app.wasalna.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val isOnboardingCompleted: Flow<Boolean>

    val isElderlyMode: Flow<Boolean>

    val alarmLeadTimeMinutes: Flow<Int>

    val isPremium: Flow<Boolean>

    val trialEndTimeMillis: Flow<Long>

    val isSilentAlarmEnabled: Flow<Boolean>

    val preferredMapEngine: Flow<String>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setElderlyMode(enabled: Boolean)

    suspend fun setAlarmLeadTimeMinutes(minutes: Int)

    suspend fun setPremium(premium: Boolean)

    suspend fun startTrial(daysFromNow: Int)

    suspend fun enforceTrialExpiry()

    suspend fun setSilentAlarmEnabled(enabled: Boolean)

    suspend fun setPreferredMapEngine(engine: String)
}
