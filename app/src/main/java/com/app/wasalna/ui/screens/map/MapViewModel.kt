package com.app.wasalna.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.BuildConfig
import com.app.wasalna.data.local.datastore.SettingsDataStore.MapEngines
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.SettingsRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phase 4: destination search via Places Autocomplete + save via LocationRepository.
 * Phase 9-1: dual map engine — Free OSM default, Google Maps for premium.
 * Phase 9-2: map-click selection + GPS-based localized search (country filter)
 * + reverse-geocoded address for tapped points.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val placesClient: PlacesClient,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _predictions = MutableStateFlow<List<AutocompletePrediction>>(emptyList())
    val predictions: StateFlow<List<AutocompletePrediction>> = _predictions.asStateFlow()

    private val _selectedName = MutableStateFlow<String?>(null)
    val selectedName: StateFlow<String?> = _selectedName.asStateFlow()

    private val _selectedLatLng = MutableStateFlow<LatLng?>(null)
    val selectedLatLng: StateFlow<LatLng?> = _selectedLatLng.asStateFlow()

    // Phase 9-2: human-readable address for the tapped/searched point.
    // Shown in/above the search bar and above the Save FAB; used as the
    // saved Location name for map taps.
    private val _selectedAddress = MutableStateFlow<String?>(null)
    val selectedAddress: StateFlow<String?> = _selectedAddress.asStateFlow()

    // Phase 9-2: GPS-derived ISO country code (e.g. "EG"), detected once
    // per session. Null = unknown/denied -> search runs unfiltered.
    private val _countryCode = MutableStateFlow<String?>(null)
    val countryCode: StateFlow<String?> = _countryCode.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // Phase 9-1: premium + preferred engine flows.
    val isPremium: StateFlow<Boolean> = settingsRepository.isPremium
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val preferredMapEngine: StateFlow<String> = settingsRepository.preferredMapEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapEngines.OSM)

    /**
     * Effective engine: non-premium always OSM; premium follows preference,
     * with fallback to OSM when no real Google Maps key is configured.
     */
    val activeMapEngine: StateFlow<String> = combine(
        isPremium,
        preferredMapEngine,
    ) { premium, preferred ->
        when {
            !premium -> MapEngines.OSM
            preferred == MapEngines.GOOGLE && !hasGoogleKey() -> MapEngines.OSM
            else -> preferred
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapEngines.OSM)

    // One-shot UI messages (fallback notice, etc.).
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var fallbackNotifiedForGoogle = false
    private var countryDetectStarted = false

    init {
        // Notify once if GOOGLE is preferred but no key is configured.
        viewModelScope.launch {
            combine(isPremium, preferredMapEngine) { premium, preferred ->
                premium to preferred
            }.collect { (premium, preferred) ->
                if (premium && preferred == MapEngines.GOOGLE && !hasGoogleKey()
                    && !fallbackNotifiedForGoogle
                ) {
                    fallbackNotifiedForGoogle = true
                    _events.emit(MSG_GOOGLE_UNAVAILABLE)
                }
            }
        }
        // Phase 9-2: detect country once per session for localized search.
        detectCountryCode()
    }

    // Phase 9-3-2-fix: surfaced search error for UI (Snackbar/error text).
    // Null = no error. Set on Places failures (API key / billing issues).
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Phase 9-3-2-fix: trigger logic — queries shorter than 2 chars never
     * hit the API (avoids empty/useless calls); an emptied query clears
     * predictions immediately so the dropdown collapses.
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
     * Phase 9-2: GPS-based country detection, once per session.
     * Guards on runtime location permission; when denied the country stays
     * null and autocomplete search runs unfiltered.
     */
    fun detectCountryCode() {
        if (countryDetectStarted) return
        countryDetectStarted = true
        viewModelScope.launch {
            runCatching {
                if (!hasLocationPermission()) {
                    Log.w(TAG, "detectCountryCode: permission denied, search unfiltered")
                    return@launch
                }
                val location = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(COUNTRY_LOC_TIMEOUT_MS) {
                        runCatching { fusedClient.lastLocation.await() }.getOrNull()
                    }
                } ?: run {
                    Log.w(TAG, "detectCountryCode: lastLocation null/timeout")
                    return@launch
                }
                val code = withContext(Dispatchers.IO) {
                    runCatching {
                        getCountryCodeFromLocation(location.latitude, location.longitude)
                    }.getOrNull()
                }
                if (!code.isNullOrBlank()) {
                    _countryCode.value = code.uppercase(Locale.US)
                    Log.i(TAG, "detectCountryCode: $code")
                }
            }.onFailure {
                Log.e(TAG, "detectCountryCode failed: ${it.message}")
            }
        }
    }

    fun searchPlaces(query: String) {
        viewModelScope.launch {
            runCatching {
                val builder = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                // Phase 9-3-2-fix: country restriction REMOVED entirely —
                // global search restored to verify the Places API works and
                // rule out the GPS country code as the request breaker.
                // (GPS detection still runs for future use; it just no longer
                // filters requests.)
                // .setCountries(...) — disabled, see above.
                val response = placesClient.findAutocompletePredictions(builder.build()).await()
                response.autocompletePredictions
            }.onSuccess {
                _predictions.value = it
                _searchError.value = null
            }.onFailure { e ->
                // Phase 9-3-2-fix: explicit Logcat + surfaced UI error so
                // API key / billing denials (e.g. PLACES_API_ACCESS_DENIED)
                // are visible instead of failing silently.
                Log.e("PlacesAPI", "Search failed: ${e.message}")
                Log.e(TAG, "Autocomplete failed: ${e.message}")
                _predictions.value = emptyList()
                _searchError.value = "Search failed: Check API Key or Billing"
            }
        }
    }

    fun fetchPlaceDetails(prediction: AutocompletePrediction) {
        viewModelScope.launch {
            runCatching {
                val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
                val request = FetchPlaceRequest.builder(prediction.placeId, fields).build()
                placesClient.fetchPlace(request).await().place
            }.onSuccess { place ->
                _selectedName.value = place.name
                _selectedAddress.value = place.name
                _selectedLatLng.value = place.latLng
                _predictions.value = emptyList()
                _searchQuery.value = place.name ?: _searchQuery.value
            }.onFailure {
                Log.e(TAG, "FetchPlace failed: ${it.message}")
            }
        }
    }

    /**
     * Phase 9-2: map tap entry point (Google onMapClick + OSM tap helper).
     * Updates marker/camera source of truth, then reverse-geocodes the point
     * into a short readable address used as the saved name.
     */
    fun onMapClicked(lat: Double, lng: Double) {
        _selectedLatLng.value = LatLng(lat, lng)
        _predictions.value = emptyList()
        viewModelScope.launch {
            val address = getAddressFromLatLng(lat, lng)
            _selectedAddress.value = address
            _selectedName.value = address
            _searchQuery.value = address
        }
    }

    /**
     * Phase 9-2: reverse-geocode off the main thread.
     * API 33+: async listener wrapped in suspendCancellableCoroutine.
     * Older: blocking getFromLocation on Dispatchers.IO.
     * Returns a short readable name, falling back to "lat,lng" on
     * IOException or empty results.
     */
    suspend fun getAddressFromLatLng(lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) {
            // Phase 9-2-fix: friendly fallback when Geocoder fails
            // (network issue / no address found) — never crash, never blank.
            val fallback =
                "Selected Location (${lat.toString().take(5)}, ${lng.toString().take(5)})"
            try {
                val geocoder = Geocoder(appContext, Locale.getDefault())
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocodeApi33(geocoder, lat, lng)
                } else {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull()
                }
                val first = addresses?.firstOrNull() ?: return@withContext fallback
                first.subLocality
                    ?: first.thoroughfare
                    ?: first.locality
                    ?: first.adminArea
                    ?: fallback
            } catch (e: IOException) {
                Log.e(TAG, "getAddressFromLatLng IO failed: ${e.message}")
                fallback
            } catch (e: Exception) {
                Log.e(TAG, "getAddressFromLatLng failed: ${e.message}")
                fallback
            }
        }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun getCountryCodeFromLocation(lat: Double, lng: Double): String? {
        val geocoder = Geocoder(appContext, Locale.getDefault())
        val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodeApi33(geocoder, lat, lng)
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull()
        }
        return addresses?.firstOrNull()?.countryCode
    }

    private suspend fun geocodeApi33(
        geocoder: Geocoder,
        lat: Double,
        lng: Double,
    ): List<Address>? = suspendCancellableCoroutine { cont ->
        try {
            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (!cont.isCompleted) cont.resume(addresses.toList())
                }

                override fun onError(errorMessage: String?) {
                    if (!cont.isCompleted) cont.resume(null)
                }
            })
        } catch (e: Exception) {
            if (!cont.isCompleted) cont.resumeWithException(e)
        }
    }

    fun clearSelection() {
        _selectedName.value = null
        _selectedAddress.value = null
        _selectedLatLng.value = null
    }

    /**
     * Phase 9-1: switch preferred engine. Only effective for premium users;
     * non-premium calls are ignored. Selecting GOOGLE without a real key
     * still persists the preference but the active engine falls back to OSM
     * with a one-shot notice.
     */
    fun setMapEngine(engine: String) {
        val normalized =
            if (engine == MapEngines.GOOGLE) MapEngines.GOOGLE else MapEngines.OSM
        viewModelScope.launch {
            val premium = try {
                isPremium.first()
            } catch (e: Exception) {
                Log.e(TAG, "setMapEngine: isPremium read failed: ${e.message}")
                false
            }
            if (!premium) {
                Log.w(TAG, "setMapEngine ignored: not premium")
                return@launch
            }
            settingsRepository.setPreferredMapEngine(normalized)
            if (normalized == MapEngines.GOOGLE && !hasGoogleKey()) {
                _events.emit(MSG_GOOGLE_UNAVAILABLE)
            }
        }
    }

    /** Persists the selected destination, then invokes [onSaved]. */
    fun saveDestination(onSaved: () -> Unit = {}) {
        val name = _selectedName.value ?: return
        val latLng = _selectedLatLng.value ?: return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            runCatching {
                locationRepository.addLocation(
                    Location(
                        name = name,
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                    ),
                )
            }.onSuccess { onSaved() }
                .onFailure { Log.e(TAG, "saveDestination failed: ${it.message}") }
            _isSaving.value = false
        }
    }

    private fun hasGoogleKey(): Boolean {
        val key = BuildConfig.MAPS_API_KEY.trim()
        return key.isNotEmpty() && key != "YOUR_API_KEY_HERE"
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val COUNTRY_LOC_TIMEOUT_MS = 8_000L
        const val MSG_GOOGLE_UNAVAILABLE = "Google Maps unavailable, using Free Maps"
        const val MSG_UPGRADE_FOR_GOOGLE = "Upgrade to First Class to use Google Maps"
    }
}
