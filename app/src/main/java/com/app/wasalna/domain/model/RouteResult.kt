package com.app.wasalna.domain.model

/**
 * Phase 10.4.3.9: offline route result — the contract every routing engine
 * (OSRM online today, GraphHopper on-device with this phase) fulfils.
 * Points are [lat, lng] in travel order.
 */
data class RouteResult(
    val points: List<RoutePoint2D>,
    val distanceMeters: Double,
    val durationSec: Double,
) {
    /** ETA minutes, at least 1 when a route exists. */
    val etaMinutes: Int
        get() = (durationSec / 60.0).toInt().coerceAtLeast(1)

    /** MapLibre-compatible LineString FeatureCollection for the route layer. */
    fun toGeoJson(): String {
        val coords = points.joinToString(",") { "[${it.lng},${it.lat}]" }
        return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coords]}," +
            "\"properties\":{}}]}"
    }
}

/** Phase 10.4.3.9: one routing polyline vertex (no trip binding). */
data class RoutePoint2D(
    val lat: Double,
    val lng: Double,
)
