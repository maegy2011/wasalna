package com.app.wasalna.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 7: Settings state — elderly mode + alarm lead time via [SettingsRepository].
 * All Flows are DataStore-backed; setters run in viewModelScope.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val elderlyMode: StateFlow<Boolean> =
        settingsRepository.isElderlyMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val leadTimeMinutes: StateFlow<Int> =
        settingsRepository.alarmLeadTimeMinutes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 5,
        )

    val isPremium: StateFlow<Boolean> =
        settingsRepository.isPremium.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val silentAlarm: StateFlow<Boolean> =
        settingsRepository.isSilentAlarmEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    // Phase 10.4.3.1: premium custom ringtone URI ("" = system default).
    val customRingtoneUri: StateFlow<String> =
        settingsRepository.customRingtoneUri.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    fun setCustomRingtoneUri(uri: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setCustomRingtoneUri(uri) }
        }
    }

    fun setElderlyMode(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setElderlyMode(enabled) }
        }
    }

    fun setLeadTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAlarmLeadTimeMinutes(minutes.coerceIn(1, 30)) }
        }
    }

    fun setSilentAlarm(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setSilentAlarmEnabled(enabled) }
        }
    }

    // Phase 10.4.3.3: adaptive trigger distance per transport mode.
    val adaptiveAlarm: StateFlow<Boolean> =
        settingsRepository.isAdaptiveAlarmEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    fun setAdaptiveAlarm(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAdaptiveAlarmEnabled(enabled) }
        }
    }
}
