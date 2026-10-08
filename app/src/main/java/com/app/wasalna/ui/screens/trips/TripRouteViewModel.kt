package com.app.wasalna.ui.screens.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.model.RoutePoint
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        viewModelScope.launch {
            _trip.value = runCatching { tripRepository.tripById(tripId) }
                .getOrNull()
        }
    }
}
