package com.app.wasalna.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Phase 3: Splash gate — null = still loading from DataStore.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val isOnboardingCompleted: StateFlow<Boolean?> =
        settingsRepository.isOnboardingCompleted
            .map { it as Boolean? }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
}
