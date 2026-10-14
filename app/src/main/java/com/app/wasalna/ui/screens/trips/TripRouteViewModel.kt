package com.app.wasalna.ui.screens.trips

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.data.local.entity.TripStatus
import com.app.wasalna.domain.model.RoutePoint
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.domain.repository.TripRepository
import com.app.wasalna.data.repository.NominatimRepository
import com.app.wasalna.data.repository.OSRMRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 10.4.3.8: live trip route map — trip details (one-shot) plus a
 * reactive breadcrumb stream. While the trip is ACTIVE the service keeps
 * inserting points, so [points] re-emits and the line redraws itself.
 */
@HiltViewModel
class TripRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val nominatim: NominatimRepository,
    private val osrm: OSRMRepository,
    private val graphManager: com.app.wasalna.data.routing.RoutingGraphManager,
    private val ghEngine: com.app.wasalna.data.routing.GraphHopperRouteEngine,
) : ViewModel() {

    val tripId: Long = savedStateHandle.get<Long>("tripId") ?: 0L

    private val _trip = MutableStateFlow<TripHistory?>(null)
    val trip: StateFlow<TripHistory?> = _trip.asStateFlow()

    val points: StateFlow<List<RoutePoint>> =
        tripRepository.routePoints(tripId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // User order Oct 2026: trip sharing is First Class — the screen
    // gates both share buttons on this.
    val isPremium: StateFlow<Boolean> =
        settingsRepository.isPremium.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * Past-trip summary (user order Oct 2026): shown when browsing a
     * finished trip — recorded stats + resolved origin name (a saved
     * place name when the origin matches one, else the real reverse-
     * geocoded name, else null and the UI falls back to raw coords).
     */
    data class PastTripSummary(
        val originName: String?,
        val destName: String,
        val startTime: Long,
        val endTime: Long?,
        val distanceKm: Double,
        val durationMin: Long?,
        val avgKmh: Double?,
    )

    private val _pastSummary = MutableStateFlow<PastTripSummary?>(null)
    val pastSummary: StateFlow<PastTripSummary?> = _pastSummary.asStateFlow()
    private var geocodedTripId: Long = -1L
    private var geocodedName: String? = null

    // Planned route (user order Oct 2026): origin → destination road
    // polyline drawn UNDER the travelled breadcrumb trail. Road modes +
    // generic TRANSIT ride the OSRM corridor (offline engine fallback,
    // straight-line last resort); static transit systems (METRO/MONORAIL/
    // TRAIN) draw nothing — their ride legs must never be faked.
    private val _plannedRoute = MutableStateFlow<String?>(null)
    val plannedRouteGeoJson: StateFlow<String?> = _plannedRoute.asStateFlow()
    private var planJob: Job? = null

    init {
        viewModelScope.launch {
            _trip.value = runCatching { tripRepository.tripById(tripId) }
                .getOrNull()
        }
        viewModelScope.launch {
            combine(_trip, points, transportMode) { trip, pts, mode ->
                Triple(trip, pts, mode)
            }.collect { (trip, pts, mode) ->
                val origin = pts.firstOrNull()
                // Past-trip summary resolves alongside the map data.
                if (trip != null && trip.status == TripStatus.PAST) {
                    _pastSummary.value = runCatching {
                        buildPastSummary(trip, pts)
                    }.getOrNull()
                } else if (_pastSummary.value != null) {
                    _pastSummary.value = null
                }
                planJob?.cancel()
                if (trip == null || origin == null || mode.isTransit && mode != TransportMode.TRANSIT) {
                    _plannedRoute.value = null
                    return@collect
                }
                planJob = viewModelScope.launch {
                    _plannedRoute.value = runCatching {
                        fetchPlannedRoute(
                            origin.lat, origin.lng,
                            trip.destLat, trip.destLng, mode,
                        )
                    }.getOrNull()
                }
            }
        }
    }

    private suspend fun buildPastSummary(
        trip: TripHistory,
        pts: List<RoutePoint>,
    ): PastTripSummary {
        val distanceKm = pts.zipWithNext { a, b ->
            haversineKm(a.lat, a.lng, b.lat, b.lng)
        }.sum()
        val durationMin = trip.endTime?.let { (it - trip.startTime) / 60_000L }
            ?.takeIf { it >= 0 }
        val avgKmh = if (durationMin != null && durationMin > 0) {
            distanceKm / (durationMin / 60.0)
        } else {
            null
        }
        val origin = pts.firstOrNull()
        val originName = if (origin != null) {
            resolveOriginName(trip.id, origin.lat, origin.lng)
        } else {
            null
        }
        return PastTripSummary(
            originName = originName,
            destName = trip.locationName,
            startTime = trip.startTime,
            endTime = trip.endTime,
            distanceKm = distanceKm,
            durationMin = durationMin,
            avgKmh = avgKmh,
        )
    }

    /** Saved-place name → real reverse-geocoded name → null (coords). */
    private suspend fun resolveOriginName(
        tripId: Long,
        lat: Double,
        lng: Double,
    ): String? {
        val saved = runCatching {
            locationRepository.getAllLocations().first()
        }.getOrNull().orEmpty()
        saved.minByOrNull { haversineKm(lat, lng, it.latitude, it.longitude) }
            ?.takeIf { haversineKm(lat, lng, it.latitude, it.longitude) < 0.15 }
            ?.let {
                return it.name
            }
        // One reverse-geocode per trip (cached while browsing it).
        if (geocodedTripId == tripId) return geocodedName
        val real = runCatching { nominatim.reverseGeocode(lat, lng) }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        geocodedTripId = tripId
        geocodedName = real
        return real
    }

    private suspend fun fetchPlannedRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        mode: TransportMode,
    ): String? {
        // Online corridor first (same profile semantics as MapScreen).
        val road = runCatching {
            osrm.fetchRoute(startLat, startLng, destLat, destLng, mode.osrmProfile)
        }.getOrNull()
        if (road != null) return road.geoJson
        // On-device engine for car-baked graphs (CAR/MOTORCYCLE only).
        if (mode.offlineRoutable) {
            val local = runCatching {
                com.app.wasalna.data.routing.routeOfflineWithAutoSelect(
                    graphManager, ghEngine, startLat, startLng, destLat, destLng,
                )?.second
            }.getOrNull()
            if (local != null) return local.toGeoJson()
        }
        // Last resort: honest straight-line connector, never a fake road.
        return straightLineGeoJson(startLat, startLng, destLat, destLng)
    }

    private fun straightLineGeoJson(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
    ): String =
        "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"geometry\":{\"type\":\"LineString\",\"coordinates\":" +
            "[[$startLng,$startLat],[$destLng,$destLat]]},\"properties\":{}}]}"

    /**
     * VEHICLE (user order Oct 2026): mid-trip vehicle switch — persisted
     * to the same DataStore key the pre-trip sheet writes; the running
     * TrackingService re-reads it on the next fix and recomputes the
     * route with the new OSRM profile (see TrackingService.onFix).
     */
    val transportMode: StateFlow<TransportMode> =
        settingsRepository.transportMode.map { TransportMode.fromName(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransportMode.CAR,
            )

    fun setTransportMode(mode: TransportMode) {
        viewModelScope.launch {
            runCatching { settingsRepository.setTransportMode(mode.name) }
                .onFailure { Log.e(TAG, "persist vehicle failed: ${it.message}") }
        }
    }

    /**
     * REDESIGN 10 Oct 2026: prominent end-trip action — reuses the
     * existing TripRepository.finishTrip storage (same as the tracking
     * service path). Clears local state, then navigates back via [onDone].
     * BUGFIX Oct 2026: finishing ONLY the DB row left TrackingService
     * running (GPS + notification) while the row sat in Past — the
     * running trip appeared in previous trips. Now the service is
     * stopped first; its onDestroy finishes the row as the single
     * source of truth (idempotent double-finish is harmless).
     * No `!!`, no new permissions.
     */
    fun finishTrip(context: android.content.Context, onDone: () -> Unit = {}) {
        // Stop the live tracker first so the row cannot outlive the trip.
        com.app.wasalna.data.service.TripStarter.stopTrip(context)
        viewModelScope.launch {
            val existing = runCatching { tripRepository.tripById(tripId) }.getOrNull()
            if (existing != null) {
                runCatching { tripRepository.finishTrip(tripId) }
                _trip.value = runCatching { tripRepository.tripById(tripId) }
                    .getOrNull()
            }
            onDone()
        }
    }

    companion object {
        private const val TAG = "TripRouteViewModel"
    }
}
