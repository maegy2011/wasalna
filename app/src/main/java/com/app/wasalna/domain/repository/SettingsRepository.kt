package com.app.wasalna.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val isOnboardingCompleted: Flow<Boolean>

    val isElderlyMode: Flow<Boolean>

    val alarmLeadTimeMinutes: Flow<Int>

    val isPremium: Flow<Boolean>

    val isSilentAlarmEnabled: Flow<Boolean>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setElderlyMode(enabled: Boolean)

    suspend fun setAlarmLeadTimeMinutes(minutes: Int)

    suspend fun setPremium(premium: Boolean)

    suspend fun setSilentAlarmEnabled(enabled: Boolean)
}
