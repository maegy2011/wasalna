package com.app.wasalna.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.data.repository.NominatimRepository
import com.app.wasalna.data.repository.OSRMRepository
import com.app.wasalna.data.repository.OsrmRoute
import com.app.wasalna.data.repository.VelaSearchRepository
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.model.SearchResult
import com.app.wasalna.domain.repository.LocationRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Phase 10-Fix: 100% MapLibre — no engine toggle, no Google Places.
 * - Search: Photon keyless autocomplete (real places + coords, one step).
 * - Map taps: Nominatim reverse-geocode (free).
 * - Route: OSRM GeoJSON from the GPS fix to the confirmed destination.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val locationRepository: LocationRepository,
    private val nominatim: NominatimRepository,
    private val vela: VelaSearchRepository,
    private val osrm: OSRMRepository,
) : ViewModel() {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Photon hits (real places with coordinates) — rendered directly.
    private val _predictions = MutableStateFlow<List<SearchResult>>(emptyList())
    val predictions: StateFlow<List<SearchResult>> = _predictions.asStateFlow()

    // OSRM route GeoJSON (FeatureCollection string) from the user's GPS
    // position to the confirmed destination. Null = none yet.
    private val _routeGeoJson = MutableStateFlow<String?>(null)
    val routeGeoJson: StateFlow<String?> = _routeGeoJson.asStateFlow()

    // Phase 10.4.0: multimodal ETA — user-selected transport mode +
    // live minutes estimate. Null = not computed yet.
    private val _transportMode = MutableStateFlow(TransportMode.CAR)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

    private val _selectedName = MutableStateFlow<String?>(null)
    val selectedName: StateFlow<String?> = _selectedName.asStateFlow()

    private val _selectedLatLng = MutableStateFlow<LatLng?>(null)
    val selectedLatLng: StateFlow<LatLng?> = _selectedLatLng.asStateFlow()

    // Human-readable address for the tapped/searched point — shown above
    // the search bar and the Save FAB; used as the saved Location name.
    private val _selectedAddress = MutableStateFlow<String?>(null)
    val selectedAddress: StateFlow<String?> = _selectedAddress.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // One-shot UI messages.
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // Surfaced search error for UI (empty = no error).
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Trigger logic — queries shorter than 2 chars never hit the API;
     * an emptied query clears predictions immediately.
     */
    fun onQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _predictions.value = emptyList()
            _searchError.value = null
            return
        }
        if (query.trim().length < 2) {
            _predictions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            searchPlaces(query)
        }
    }

    /**
     * Phase 10-Fix: Photon keyless autocomplete — one step, coords included.
     * Phase 10.4.0: localized — the current GPS fix biases results toward
     * the user's country/city; without a fix it falls back to global search.
     */
    fun searchPlaces(query: String) {
        viewModelScope.launch {
            val fix = currentFix()
            runCatching {
                vela.suggest(query, lat = fix?.latitude, lon = fix?.longitude)
            }.onSuccess {
                _predictions.value = it
                _searchError.value = null
            }.onFailure { e ->
                Log.e(TAG, "Photon search failed: ${e.message}")
                _predictions.value = emptyList()
                _searchError.value = "Search failed: check your connection"
            }
        }
    }

    /** Last GPS fix, guarded by permission with a short timeout. Null = none. */
    private suspend fun currentFix(): android.location.Location? {
        if (!hasLocationPermission()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(LOC_TIMEOUT_MS) {
                runCatching { fusedClient.lastLocation.await() }.getOrNull()
            }
        }
    }

    /**
     * Phase 10.4.0: route + ETA refresh for the current destination under
     * the selected transport mode. Called on every confirmed destination
     * and on every mode change. Silent skip without a fix — never crashes.
     */
    private suspend fun refreshRouteAndEta(destLat: Double, destLng: Double) {
        val start = currentFix() ?: return
        val profile = _transportMode.value.osrmProfile
        val route: OsrmRoute? = runCatching {
            osrm.fetchRoute(start.latitude, start.longitude, destLat, destLng, profile)
        }.onFailure {
            Log.e(TAG, "route fetch failed: ${it.message}")
        }.getOrNull()
        _routeGeoJson.value = route?.geoJson
        _etaMinutes.value = route?.durationSec
            ?.takeIf { it >= 0 }
            ?.let { kotlin.math.round(it / 60.0).toInt().coerceAtLeast(1) }
    }

    /**
     * Phase 10.4.1: mode switch only flips state — the route/ETA refetch
     * is driven reactively by LaunchedEffect(selectedLatLng, transportMode)
     * in MapScreen, so EVERY mode change guarantees a fresh OSRM call.
     */
    fun setTransportMode(mode: TransportMode) {
        _transportMode.value = mode
    }

    /** Result click — Photon hits always carry lat/lng: select immediately. */
    fun fetchPlaceDetails(result: SearchResult) {
        val lat = result.lat ?: return
        val lng = result.lng ?: return
        _selectedName.value = result.name
        _selectedAddress.value = result.name
        _selectedLatLng.value = LatLng(lat, lng)
        _predictions.value = emptyList()
        _searchError.value = null
        _searchQuery.value = result.name
        // Phase 10.4.1: route follows reactively via
        // LaunchedEffect(selectedLatLng, transportMode) in MapScreen.
    }

    /**
     * Phase 10.4.1: reactive route entry point — reads the current
     * selection + transport mode. Safe to call from LaunchedEffect on
     * every selection or mode change (idempotent, IO-bound).
     */
    fun refreshRouteAndEta() {
        val dest = _selectedLatLng.value ?: return
        viewModelScope.launch { refreshRouteAndEta(dest.latitude, dest.longitude) }
    }

    /**
     * Map tap entry point — marker/camera source of truth, then
     * Nominatim reverse-geocode (Dispatchers.IO, try-catch) into a
     * readable address used as the saved name.
     */
    fun onMapClicked(lat: Double, lng: Double) {
        _selectedLatLng.value = LatLng(lat, lng)
        _predictions.value = emptyList()
        viewModelScope.launch {
            val address = nominatim.reverseGeocode(lat, lng) ?: fallbackName(lat, lng)
            _selectedAddress.value = address
            _selectedName.value = address
            _searchQuery.value = address
        }
    }

    private fun fallbackName(lat: Double, lng: Double): String =
        "Selected Location (${lat.toString().take(5)}, ${lng.toString().take(5)})"

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun clearSelection() {
        _selectedName.value = null
        _selectedAddress.value = null
        _selectedLatLng.value = null
        _routeGeoJson.value = null
        _etaMinutes.value = null
    }

    /**
     * Persists the selected destination, then invokes [onSaved].
     * Phase 10.4.1: aborts with "already saved" on duplicates; on success
     * the selection is cleared BEFORE navigating so MapLibre never
     * recomposes wildly mid-save (no tile loss / camera jump).
     */
    fun saveDestination(onSaved: () -> Unit = {}) {
        val name = _selectedName.value ?: return
        val latLng = _selectedLatLng.value ?: return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val duplicate = runCatching {
                locationRepository.isDuplicate(name, latLng.latitude, latLng.longitude)
            }.getOrDefault(false)
            if (duplicate) {
                _isSaving.value = false
                _events.emit(MSG_ALREADY_SAVED)
                return@launch
            }
            runCatching {
                locationRepository.addLocation(
                    Location(
                        name = name,
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                    ),
                )
            }.onSuccess {
                clearSelection()
                onSaved()
            }.onFailure { Log.e(TAG, "saveDestination failed: ${it.message}") }
            _isSaving.value = false
        }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val LOC_TIMEOUT_MS = 8_000L
        const val MSG_ALREADY_SAVED = "This place is already saved!"
    }
}

/**
 * Phase 10.4.0: user-selected transport mode for the Vela-style ETA.
 * Car and Train both route on the OSRM `driving` profile (train as a
 * proxy); Walk uses the `foot` profile.
 */
enum class TransportMode(val osrmProfile: String) {
    WALK("foot"),
    CAR("driving"),
    TRAIN("driving"),
}
