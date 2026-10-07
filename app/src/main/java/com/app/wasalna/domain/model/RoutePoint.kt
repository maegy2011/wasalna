package com.app.wasalna.domain.model

/** Phase 10.4.3.7 (Part 2): one recorded GPS breadcrumb of a trip. */
data class RoutePoint(
    val tripId: Long,
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
)
