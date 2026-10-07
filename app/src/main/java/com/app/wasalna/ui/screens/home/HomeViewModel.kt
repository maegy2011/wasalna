package com.app.wasalna.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 5: Home — saved alarm destinations as StateFlow + delete support.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    // Phase 8: premium gates ads + freemium location limit.
    val isPremium: StateFlow<Boolean> =
        settingsRepository.isPremium
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    // Phase 10.4.2: persisted transport mode for notification icon +
    // alarm setup continuity (chosen on Map, used when tracking starts).
    val transportMode: StateFlow<TransportMode> =
        settingsRepository.transportMode.map { TransportMode.fromName(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransportMode.CAR,
            )

    // Phase 10.4.1: trial countdown for the drawer plan header.
    val trialEndTimeMillis: StateFlow<Long> =
        settingsRepository.trialEndTimeMillis
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0L,
            )

    init {
        // Revert premium if a trial expired while the app was closed.
        viewModelScope.launch {
            runCatching { settingsRepository.enforceTrialExpiry() }
                .onFailure { Log.e(TAG, "enforceTrialExpiry failed: ${it.message}") }
        }
        // Phase 10.4.2.1: heal stale ACTIVE trips (killed service, reboot).
        viewModelScope.launch {
            runCatching { tripRepository.closeOpenTrips() }
                .onFailure { Log.e(TAG, "closeOpenTrips failed: ${it.message}") }
        }
    }

    // Phase 10.4.2.1: live active trip (Home widget) + impact stats.
    val activeTrip: StateFlow<TripHistory?> =
        tripRepository.activeTrip()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val successfulTrips: StateFlow<Int> =
        tripRepository.pastCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    val timeSavedHours: StateFlow<Float> =
        tripRepository.totalEtaMinutes()
            .map { it / 60f }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f,
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

    /** Phase 10-Fix: rename a saved place (My Places Edit dialog). */
    fun renameLocation(location: Location, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == location.name) return
        viewModelScope.launch {
            runCatching {
                locationRepository.updateLocation(location.copy(name = trimmed))
            }.onFailure { Log.e(TAG, "renameLocation failed: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
