package com.app.wasalna.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 5: Home — saved alarm destinations as StateFlow + delete support.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // Phase 8: premium gates ads + freemium location limit.
    val isPremium: StateFlow<Boolean> =
        settingsRepository.isPremium
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    val locations: StateFlow<List<Location>> =
        locationRepository.getAllLocations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun deleteLocation(location: Location) {
        viewModelScope.launch {
            runCatching { locationRepository.removeLocation(location) }
                .onFailure { Log.e(TAG, "deleteLocation failed: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
