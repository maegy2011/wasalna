package com.app.wasalna.ui.screens.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.repository.LocationRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Phase 4: destination search via Places Autocomplete + save via LocationRepository.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val placesClient: PlacesClient,
    private val locationRepository: LocationRepository,
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

    companion object {
        private const val TAG = "MapViewModel"
    }
}
