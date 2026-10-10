package com.app.wasalna.domain.model

/**
 * Live transit plan from Google Routes API **v2**
 * (`directions/v2:computeRoutes`, `travelMode: TRANSIT`).
 *
 * - Pure Kotlin + org.json (same GeoJSON shape OSRMRepository emits, so
 *   MapLibre renders it with zero map-layer changes).
 * - A plan is walk + ride steps with lines/stops/times; WITHOUT a key
 *   the app never builds one and stays on the APPROXIMATE guide
 *   (see CairoTransitStations) — never a faked route.
 * - Offline mode never requests one (MapViewModel returns before any
 *   network call; TrackingService never calls the repository).
 */
data class LatLngD(
    val lat: Double,
    val lng: Double,
)

data class TransitStep(
    /** "WALK" or "TRANSIT" (any other mode degrades to a walk leg). */
    val travelMode: String,
    val lineName: String? = null,
    val lineShort: String? = null,
    /** SUBWAY / BUS / RAIL / TRAIN / LIGHT_RAIL / ... (raw API token). */
    val vehicleType: String? = null,
    val departStop: String? = null,
    val arriveStop: String? = null,
    /** Localized display texts from the API (languageCode=ar requested). */
    val departTime: String? = null,
    val arriveTime: String? = null,
    val stopCount: Int? = null,
    /** Straight-line distance in meters (API distanceMeters, walk legs). */
    val distanceM: Double = 0.0,
    val points: List<LatLngD> = emptyList(),
) {
    val isRide: Boolean get() = travelMode == "TRANSIT"

    /** Display line: short code preferred, full name as fallback. */
    fun lineLabel(): String =
        lineShort?.ifBlank { null } ?: lineName?.ifBlank { null } ?: ""
}

data class TransitPlan(
    val steps: List<TransitStep>,
    /** routes[0].duration seconds (whole trip incl. walking). */
    val durationSec: Double = -1.0,
) {
    val rideSteps: List<TransitStep> get() = steps.filter { it.isRide }

    /** Concatenated step polylines for the map overlay. */
    fun allPoints(): List<LatLngD> = steps.flatMap { it.points }

    /** MapLibre-ready FeatureCollection (LineString), like OSRM output. */
    fun toGeoJson(): String {
        val coords = org.json.JSONArray()
        allPoints().forEach { p ->
            coords.put(org.json.JSONArray().put(p.lng).put(p.lat))
        }
        return org.json.JSONObject()
            .put("type", "FeatureCollection")
            .put(
                "features",
                org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("type", "Feature")
                        .put(
                            "geometry",
                            org.json.JSONObject()
                                .put("type", "LineString")
                                .put("coordinates", coords),
                        )
                        .put("properties", org.json.JSONObject()),
                ),
            ).toString()
    }
}

/**
 * Google encoded-polyline decoder (pure, thread-safe). Returns empty on
 * malformed input — never throws.
 */
object PolylineDecoder {
    fun decode(encoded: String?): List<LatLngD> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return runCatching {
            val out = ArrayList<LatLngD>(encoded.length / 4)
            var index = 0
            var lat = 0
            var lng = 0
            while (index < encoded.length) {
                var b: Int
                var shift = 0
                var result = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lat += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
                shift = 0
                result = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lng += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
                out.add(LatLngD(lat / 1E5, lng / 1E5))
            }
            out
        }.getOrDefault(emptyList())
    }
}
