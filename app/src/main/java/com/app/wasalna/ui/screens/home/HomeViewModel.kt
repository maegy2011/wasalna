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
import kotlinx.coroutines.flow.first
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
        settingsRepository.transportMode.map {
            // Four-choices migration (Oct 2026): retired stored modes
            // fall back to CAR until the user re-picks.
            TransportMode.fromName(it)
                .takeIf { mode -> mode in TransportMode.primaryEntries }
                ?: TransportMode.CAR
        }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransportMode.CAR,
            )

    /** Vehicle picker persistence (pre-trip sheet + mid-trip change). */
    fun setTransportMode(mode: TransportMode) {
        viewModelScope.launch {
            runCatching { settingsRepository.setTransportMode(mode.name) }
                .onFailure { Log.e(TAG, "persist vehicle failed: ${it.message}") }
        }
    }

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

    // Alarm lead time (shared Settings default).
    val alarmLeadTimeMinutes: StateFlow<Int> = settingsRepository.alarmLeadTimeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    fun setAlarmLeadTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAlarmLeadTimeMinutes(minutes) }
                .onFailure { Log.e(TAG, "persist lead time failed: ${it.message}") }
        }
    }

    // DYNAMIC WAKE-UP (التنبيه المرتبط بالزحام): DISTANCE (legacy default)
    // vs TIME (minutes-before-arrival) — chosen in the trip-setup dialog.
    val alarmMode: StateFlow<String> = settingsRepository.alarmMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "DISTANCE")
    val alarmTimeMinutes: StateFlow<Int> = settingsRepository.alarmTimeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    fun setAlarmMode(mode: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAlarmMode(mode) }
                .onFailure { Log.e(TAG, "persist alarm mode failed: ${it.message}") }
        }
    }

    fun setAlarmTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAlarmTimeMinutes(minutes) }
                .onFailure { Log.e(TAG, "persist alarm minutes failed: ${it.message}") }
        }
    }

    init {
        // Revert premium if a trial expired while the app was closed.
        viewModelScope.launch {
            runCatching { settingsRepository.enforceTrialExpiry() }
                .onFailure { Log.e(TAG, "enforceTrialExpiry failed: ${it.message}") }
        }
        // Phase 10.4.2.1: heal stale ACTIVE trips (killed service, reboot).
        // BUGFIX Oct 2026: never close while the service is alive — that
        // moved the RUNNING trip into Past while it was still tracked.
        viewModelScope.launch {
            if (!com.app.wasalna.data.service.TrackingService.isServiceRunning) {
                runCatching { tripRepository.closeOpenTrips() }
                    .onFailure { Log.e(TAG, "closeOpenTrips failed: ${it.message}") }
            }
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

    /** Elderly safety net (user order Oct 2026): re-adds a just-deleted place. */
    fun restoreLocation(location: Location) {
        viewModelScope.launch {
            runCatching { locationRepository.addLocation(location) }
                .onFailure { Log.e(TAG, "restoreLocation failed: ${it.message}") }
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

    /**
     * User order Oct 2026: Google Takeout import result.
     * [imported] rows added, [skipped] entries without usable data
     * (or duplicates), [capped] when the free 2-place ceiling stopped
     * the import early (premium imports everything).
     */
    data class TakeoutResult(val imported: Int, val skipped: Int, val capped: Boolean)

    /**
     * Imports a Google Takeout "saved places" GeoJSON export.
     * Returns null on malformed input. Skips duplicates via
     * [LocationRepository.isDuplicate]. Free plan stops at 2 total
     * saved places ([capped] = true); First Class has no ceiling.
     */
    suspend fun importTakeout(json: String): TakeoutResult? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val parsed = runCatching {
                com.app.wasalna.utils.GoogleTakeoutImport.parse(json)
            }.getOrNull() ?: return@withContext null
            val premium = runCatching {
                settingsRepository.isPremium.first()
            }.getOrDefault(false)
            var existing = runCatching {
                locationRepository.getAllLocations().first().size
            }.getOrDefault(0)
            var imported = 0
            var skipped = parsed.skipped
            var capped = false
            parsed.places.forEach { place ->
                if (!premium && existing >= 2) {
                    capped = true
                    skipped += 1
                    return@forEach
                }
                runCatching {
                    if (!locationRepository.isDuplicate(place.name, place.latitude, place.longitude)) {
                        locationRepository.addLocation(place)
                        imported += 1
                        existing += 1
                    } else {
                        skipped += 1
                    }
                }.onFailure { skipped += 1 }
            }
            TakeoutResult(imported, skipped, capped)
        }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
