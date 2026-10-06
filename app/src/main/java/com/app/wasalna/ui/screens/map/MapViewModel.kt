package com.app.wasalna.ui.screens.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.BuildConfig
import com.app.wasalna.data.local.datastore.SettingsDataStore.MapEngines
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Phase 4: destination search via Places Autocomplete + save via LocationRepository.
 * Phase 9-1: dual map engine — Free OSM default, Google Maps for premium.
 * Places autocomplete search is kept for both engines.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val placesClient: PlacesClient,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _predictions = MutableStateFlow<List<AutocompletePrediction>>(emptyList())
    val predictions: StateFlow<List<AutocompletePrediction>> = _predictions.asStateFlow()

    private val _selectedName = MutableStateFlow<String?>(null)
    val selectedName: StateFlow<String?> = _selectedName.asStateFlow()

    private val _selectedLatLng = MutableStateFlow<LatLng?>(null)
    val selectedLatLng: StateFlow<LatLng?> = _selectedLatLng.asStateFlow()

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
    }

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _predictions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            searchPlaces(query)
        }
    }

    fun searchPlaces(query: String) {
        viewModelScope.launch {
            runCatching {
                val request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                    .build()
                val response = placesClient.findAutocompletePredictions(request).await()
                response.autocompletePredictions
            }.onSuccess { _predictions.value = it }
                .onFailure {
                    Log.e(TAG, "Autocomplete failed: ${it.message}")
                    _predictions.value = emptyList()
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
                _selectedLatLng.value = place.latLng
                _predictions.value = emptyList()
                _searchQuery.value = place.name ?: _searchQuery.value
            }.onFailure {
                Log.e(TAG, "FetchPlace failed: ${it.message}")
            }
        }
    }

    fun clearSelection() {
        _selectedName.value = null
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
        const val MSG_GOOGLE_UNAVAILABLE = "Google Maps unavailable, using Free Maps"
        const val MSG_UPGRADE_FOR_GOOGLE = "Upgrade to First Class to use Google Maps"
    }
}
