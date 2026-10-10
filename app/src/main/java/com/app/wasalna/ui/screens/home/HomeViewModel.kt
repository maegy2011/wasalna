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

    // True offline mode (GPS-chip only): the SAME DataStore-backed flow the
    // Map header cloud button writes (single source of truth, two-way
    // synced) — the Home header icon always reflects the current mode.
    val isOfflineMode: StateFlow<Boolean> =
        settingsRepository.isForceOffline
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setForceOffline(enabled) }
        }
    }

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

    // Map UX fixes: Home & Work quick places for the My Places row.
    // Tap starts a quick trip (callers gate the free-plan interstitial);
    // long-press opens the set-location dialog.
    val homeLat: StateFlow<Double?> = settingsRepository.homeLat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val homeLng: StateFlow<Double?> = settingsRepository.homeLng
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val homeAddress: StateFlow<String> = settingsRepository.homeAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val workLat: StateFlow<Double?> = settingsRepository.workLat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val workLng: StateFlow<Double?> = settingsRepository.workLng
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val workAddress: StateFlow<String> = settingsRepository.workAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setHome(lat: Double?, lng: Double?, address: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setHome(lat, lng, address) }
                .onFailure { Log.e(TAG, "persist home failed: ${it.message}") }
        }
    }

    fun setWork(lat: Double?, lng: Double?, address: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setWork(lat, lng, address) }
                .onFailure { Log.e(TAG, "persist work failed: ${it.message}") }
        }
    }

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

    /**
     * Phase 10.8.0b: restore a places backup (SAF import).
     * Skips duplicates via [LocationRepository.isDuplicate].
     * Returns imported count, or -1 on malformed JSON.
     */
    suspend fun importBackup(json: String): Int =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val parsed = runCatching {
                com.app.wasalna.utils.PlacesBackup.parse(json)
            }.getOrNull() ?: return@withContext -1
            if (parsed.homeLat != null && parsed.homeLng != null) {
                runCatching {
                    settingsRepository.setHome(parsed.homeLat, parsed.homeLng, parsed.homeAddress)
                }
            }
            if (parsed.workLat != null && parsed.workLng != null) {
                runCatching {
                    settingsRepository.setWork(parsed.workLat, parsed.workLng, parsed.workAddress)
                }
            }
            var count = 0
            parsed.places.forEach { place ->
                runCatching {
                    if (!locationRepository.isDuplicate(place.name, place.latitude, place.longitude)) {
                        locationRepository.addLocation(place)
                        count += 1
                    }
                }
            }
            count
        }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
