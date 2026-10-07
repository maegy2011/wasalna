package com.app.wasalna.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10-2: free OSRM routing (driving profile, GeoJSON geometry).
 * NO API key. All calls on Dispatchers.IO with try-catch — null when
 * the service is unreachable, never a crash.
 */
@Singleton
class OSRMRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Phase 10.4.0: route + duration in one call. [profile] is an OSRM
     * profile (`driving` for car/train proxy, `foot` for walk).
     * Returns geometry (FeatureCollection string for MapLibre) plus
     * routes[0].duration in seconds. Null when unavailable — never throws.
     */
    suspend fun fetchRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        profile: String = "driving",
    ): OsrmRoute? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/route/v1/$profile/" +
                "$startLng,$startLat;$destLng,$destLat" +
                "?overview=full&geometries=geojson"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NominatimRepository.USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "OSRM HTTP ${response.code}")
                    return@runCatching null
                }
                val body = response.body?.string() ?: return@runCatching null
                val root = JSONObject(body)
                if (root.optString("code") != "Ok") {
                    Log.w(TAG, "OSRM code=${root.optString("code")}")
                    return@runCatching null
                }
                val routes = root.optJSONArray("routes") ?: return@runCatching null
                if (routes.length() == 0) return@runCatching null
                val first = routes.getJSONObject(0)
                val geometry = first.optJSONObject("geometry")
                    ?: return@runCatching null
                // Wrap the LineString in a FeatureCollection for MapLibre.
                val geoJson = JSONObject()
                    .put("type", "FeatureCollection")
                    .put(
                        "features",
                        org.json.JSONArray().put(
                            JSONObject()
                                .put("type", "Feature")
                                .put("geometry", geometry)
                                .put("properties", JSONObject()),
                        ),
                    ).toString()
                OsrmRoute(geoJson = geoJson, durationSec = first.optDouble("duration", -1.0))
            }
        }.onFailure {
            Log.e(TAG, "OSRM route failed: ${it.message}")
        }.getOrNull()
    }

    companion object {
        private const val TAG = "OSRM"
        const val BASE = "https://router.project-osrm.org"
    }
}

/** Phase 10.4.0: route geometry + ETA source. Negative duration = unknown. */
data class OsrmRoute(
    val geoJson: String,
    val durationSec: Double,
)
