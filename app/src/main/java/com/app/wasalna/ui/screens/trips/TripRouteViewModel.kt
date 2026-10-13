package com.app.wasalna.ui.screens.trips

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.domain.model.RoutePoint
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        viewModelScope.launch {
            _trip.value = runCatching { tripRepository.tripById(tripId) }
                .getOrNull()
        }
    }

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
