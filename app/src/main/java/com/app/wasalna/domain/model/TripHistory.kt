package com.app.wasalna.domain.model

/**
 * Phase 10.4.2.1 (trips history): domain view of one tracked trip.
 * ACTIVE = ongoing (Home widget + Current tab); PAST = finished history.
 */
data class TripHistory(
    val id: Long = 0,
    val locationName: String,
    val destLat: Double,
    val destLng: Double,
    val startTime: Long,
    val endTime: Long? = null,
    val status: String = "ACTIVE",
    val transportMode: TransportMode = TransportMode.CAR,
    val etaMinutes: Int? = null,
)
