package com.app.wasalna.ui.screens.restrictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.utils.AutoStartUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 10.4.3.6: gate for the strict-OEM guidance.
 * [needsGuidance] is false on lenient devices or after the user confirms —
 * the entry flow auto-skips the screen in both cases.
 */
@HiltViewModel
class DeviceRestrictionsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val needsGuidance: StateFlow<Boolean> =
        settingsRepository.hasSeenAutoStartGuidance
            .map { seen -> AutoStartUtils.isStrictDevice() && !seen }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AutoStartUtils.isStrictDevice(),
            )

    fun markSeen() {
        viewModelScope.launch {
            runCatching { settingsRepository.setHasSeenAutoStartGuidance(true) }
        }
    }
}
