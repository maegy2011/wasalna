package com.app.wasalna.domain.repository

import com.app.wasalna.domain.model.RoutePoint
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.model.TripHistory
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10.4.2.1 (trips history): active-trip tracking, history and stats.
 */
interface TripRepository {

    fun activeTrip(): Flow<TripHistory?>

    fun pastTrips(): Flow<List<TripHistory>>

    fun pastCount(): Flow<Int>

    fun totalEtaMinutes(): Flow<Int>

    /** Closes stale ACTIVE rows, inserts the new trip, returns its id. */
    suspend fun startTrip(
        name: String,
        lat: Double,
        lng: Double,
        mode: TransportMode,
    ): Long

    suspend fun updateEta(id: Long, etaMin: Int)

    suspend fun finishTrip(id: Long)

    suspend fun closeOpenTrips()

    /** Phase 10.4.3.7 (Part 2): persist one GPS breadcrumb. */
    suspend fun recordRoutePoint(tripId: Long, lat: Double, lng: Double)

    /** Phase 10.4.3.7 (Part 2): travelled path, oldest first. */
    fun routePoints(tripId: Long): Flow<List<RoutePoint>>

    /** Phase 10.4.3.8: one-shot trip lookup for the route map. */
    suspend fun tripById(id: Long): TripHistory?
}
