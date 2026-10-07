package com.app.wasalna.ui.screens.trips

import androidx.lifecycle.ViewModel
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/**
 * Phase 10.4.2.1: backing store for TripsHistoryScreen —
 * Current (active trip), Future (saved places), Past (finished trips).
 */
@HiltViewModel
class TripsViewModel @Inject constructor(
    tripRepository: TripRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    val activeTrip: StateFlow<TripHistory?> =
        tripRepository.activeTrip().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val pastTrips: StateFlow<List<TripHistory>> =
        tripRepository.pastTrips().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val savedPlaces: StateFlow<List<Location>> =
        locationRepository.getAllLocations().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
