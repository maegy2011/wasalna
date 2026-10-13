package com.app.wasalna.utils

import com.app.wasalna.domain.model.Location
import org.json.JSONObject

/**
 * User order Oct 2026: Google Maps Takeout import ("الأماكن المحفوظة.json").
 * GeoJSON FeatureCollection — name from properties.location.name,
 * coords are GeoJSON [lng, lat]. Entries with [0,0] or blank names are
 * counted as skipped (Google omits coords for some list items).
 * Pure org.json — no new dependencies. Never throws.
 */
object GoogleTakeoutImport {

    data class ParsedTakeout(
        val places: List<Location>,
        val skipped: Int,
    )

    fun parse(json: String): ParsedTakeout? = runCatching {
        val root = JSONObject(json)
        if (root.optString("type") != "FeatureCollection") return null
        val features = root.optJSONArray("features") ?: return null
        val places = mutableListOf<Location>()
        var skipped = 0
        for (i in 0 until features.length()) {
            val place = parseFeature(features.optJSONObject(i))
            if (place != null) places.add(place) else skipped += 1
        }
        ParsedTakeout(places, skipped)
    }.getOrNull()

    private fun parseFeature(feature: JSONObject?): Location? {
        feature ?: return null
        val geometry = feature.optJSONObject("geometry") ?: return null
        if (geometry.optString("type") != "Point") return null
        val coords = geometry.optJSONArray("coordinates") ?: return null
        if (coords.length() < 2) return null
        val lng = coords.optDouble(0, Double.NaN)
        val lat = coords.optDouble(1, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null
        if (lat == 0.0 && lng == 0.0) return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        val location = feature.optJSONObject("properties")
            ?.optJSONObject("location")
        val name = location?.optString("name").orEmpty().trim()
        val address = location?.optString("address").orEmpty().trim()
        // Shape the label exactly as stored: NO hidden tags or codes.
        val label = name.ifBlank { address }
        if (label.isBlank()) return null
        return Location(name = label, latitude = lat, longitude = lng)
    }
}
