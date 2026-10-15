package com.app.wasalna.data.repository

import android.util.Log
import com.app.wasalna.domain.model.SearchResult
import com.app.wasalna.utils.NetworkGuards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10-Fix: Vela search now runs on the Photon API (keyless, free).
 * Photon returns REAL places as GeoJSON — name + coordinates arrive
 * together, so NO second geocoding step is needed (unlike the old
 * suggest-queries string flow, which returned general web text).
 *
 * Response shape: { features: [ { properties: {name, city, country, ...},
 * geometry: { coordinates: [lon, lat] } } ] }
 */
@Singleton
class VelaSearchRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Keyless place autocomplete, GPS-scoped (Oct 2026): the search
     * window follows the live fix (±3°, country-scale) — no hardcoded
     * country; without a fix it falls back to the home-market bbox.
     * Empty list on any failure — never throws (runs on Dispatchers.IO).
     */
    /**
     * Phase 10.4.3.2: true when the last call failed from no connectivity.
     * The UI reads it to show the offline message (map still renders).
     */
    @Volatile
    var lastWasOffline: Boolean = false
        private set

    suspend fun suggest(
        query: String,
        lat: Double? = null,
        lon: Double? = null,
    ): List<SearchResult> =
        withContext(Dispatchers.IO) {
            lastWasOffline = false
            rLat = lat
            rLng = lon
            runCatching {
                val bias = if (lat != null && lon != null) "&lat=$lat&lon=$lon" else ""
                // GPS-scoped (Oct 2026, user order): the search window
                // follows the LIVE fix — no hardcoded country. With a
                // fix the window is ±3° around it (country-scale); without
                // one we fall back to the home-market bbox (Egypt).
                val scope = scopeFor(lat, lon)
                val url = PHOTON_URL + "?q=${encode(query)}$bias&limit=10&lang=en" +
                    "&bbox=${scope[0]},${scope[1]},${scope[2]},${scope[3]}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", NominatimRepository.USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "photon HTTP ${response.code}")
                        return@runCatching emptyList<SearchResult>()
                    }
                    val body = response.body?.string() ?: return@runCatching emptyList<SearchResult>()
                    parsePhoton(body)
                }
            }.onFailure {
                lastWasOffline = NetworkGuards.isOfflineCause(it)
                Log.e(TAG, "photon suggest failed: ${it.message}")
            }.getOrDefault(emptyList())
        }

    // Request scope remembered for the client-side backstop (same call).
    @Volatile private var rLat: Double? = null
    @Volatile private var rLng: Double? = null

    private fun parsePhoton(body: String): List<SearchResult> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return List(features.length()) { i ->
            val feature = runCatching { features.optJSONObject(i) }.getOrNull()
                ?: return@List null
            val props = feature.optJSONObject("properties")
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            val lng = coords?.optDouble(0)
            val lat = coords?.optDouble(1)
            if (props == null || lat == null || lng == null) return@List null
            // SEARCH-CRASH FIX: a malformed mirror reply (NaN, infinite or
            // out-of-range coords) used to reach MapLibre's LatLng(), whose
            // constructor throws and kills the app on select. Drop them here.
            if (!lat.isFinite() || !lng.isFinite()) return@List null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@List null
            // Backstop: Photon honors bbox, but a misbehaving mirror or
            // future API change must not leak out-of-scope results.
            if (!inScope(lat, lng, rLat, rLng)) return@List null
            SearchResult(
                name = displayName(props),
                lat = lat,
                lng = lng,
            )
        }.filterNotNull()
    }

    private fun displayName(props: JSONObject): String {
        val name = props.optString("name").ifBlank { null }
        val city = listOf("city", "town", "village", "county", "state")
            .firstNotNullOfOrNull { props.optString(it).ifBlank { null } }
        val country = props.optString("country").ifBlank { null }
        return listOfNotNull(name, city, country).distinct().joinToString(", ")
            .ifBlank { "Unnamed place" }
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val TAG = "VelaSearch"
        const val PHOTON_URL = "https://photon.komoot.io/api/"

        /** GPS window half-size (degrees ≈ ±330 km) — country-scale. */
        const val SCOPE_HALF_DEG = 3.0

        /** Home-market fallback bbox (minLon,minLat,maxLon,maxLat). */
        const val EGYPT_BBOX = "24.5,21.5,35.0,31.8"

        /**
         * Search scope for a fix: [minLon,minLat,maxLon,maxLat] window
         * around the live position, clamped to valid ranges. Null fix →
         * home-market (Egypt) default — the app cannot scope blind.
         */
        fun scopeFor(lat: Double?, lon: Double?): DoubleArray {
            if (lat == null || lon == null) {
                return doubleArrayOf(24.5, 21.5, 35.0, 31.8)
            }
            val h = SCOPE_HALF_DEG
            return doubleArrayOf(
                (lon - h).coerceIn(-180.0, 180.0),
                (lat - h).coerceIn(-90.0, 90.0),
                (lon + h).coerceIn(-180.0, 180.0),
                (lat + h).coerceIn(-90.0, 90.0),
            )
        }

        /** True when the point lies in the request's scope window. */
        fun inScope(lat: Double, lng: Double, cLat: Double?, cLng: Double?): Boolean {
            if (cLat == null || cLng == null) {
                return lat in 21.5..31.8 && lng in 24.5..35.0
            }
            val h = SCOPE_HALF_DEG
            return lat in (cLat - h)..(cLat + h) && lng in (cLng - h)..(cLng + h)
        }

        /** True inside Egypt (inclusive) — legacy guard, kept for callers. */
        fun inEgypt(lat: Double, lng: Double): Boolean =
            lat in 21.5..31.8 && lng in 24.5..35.0
    }
}
