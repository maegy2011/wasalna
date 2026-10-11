package com.app.wasalna.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 9-3-1-fix-1: free OSM search engine (Nominatim).
 * Keyless, no billing.
 *
 * CRITICAL: Nominatim usage policy requires a custom User-Agent header;
 * requests without one risk being banned. We send "WasalnaApp/1.0".
 */
@Singleton
class NominatimRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Forward search: query → up to 5 results with display_name + lat/lon.
     * Egypt-only (Oct 2026, user order): countrycodes + bounded viewbox
     * (same EGYPT_FULL bbox as the Photon path). Currently used by no
     * UI path (Map uses Photon; this stays for fallback) — kept correct.
     * Runs on Dispatchers.IO — never blocks the UI thread.
     */
    suspend fun searchPlaces(query: String): List<OsmSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = HttpUrlBuilder.SEARCH +
                    "?q=${encode(query)}&format=json&limit=5&addressdetails=0" +
                    "&countrycodes=eg&viewbox=24.5,31.8,35.0,21.5&bounded=1"
                val body = get(url) ?: return@runCatching emptyList<OsmSearchResult>()
                val arr = JSONArray(body)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    OsmSearchResult(
                        name = o.optString("display_name", query),
                        lat = o.optString("lat").toDoubleOrNull(),
                        lng = o.optString("lon").toDoubleOrNull(),
                    )
                }
            }.onFailure {
                Log.e(TAG, "Nominatim search failed: ${it.message}")
            }.getOrDefault(emptyList())
        }

    /**
     * Reverse geocode: lat/lon → human-readable name for map taps.
     * Runs on Dispatchers.IO with try-catch; null when unavailable.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = HttpUrlBuilder.REVERSE +
                    "?lat=$lat&lon=$lng&format=json&addressdetails=0"
                val body = get(url) ?: return@runCatching null
                val o = JSONObject(body)
                o.optString("display_name").ifBlank { null }
                    ?: o.optString("name").ifBlank { null }
            }.onFailure {
                Log.e(TAG, "Nominatim reverse failed: ${it.message}")
            }.getOrNull()
        }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Nominatim HTTP ${response.code} for $url")
                return null
            }
            return response.body?.string()
        }
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private object HttpUrlBuilder {
        const val SEARCH = "https://nominatim.openstreetmap.org/search"
        const val REVERSE = "https://nominatim.openstreetmap.org/reverse"
    }

    companion object {
        private const val TAG = "Nominatim"
        const val USER_AGENT = "WasalnaApp/1.0"
    }
}

/** Raw OSM place hit from Nominatim (lat/lng always present on success). */
data class OsmSearchResult(
    val name: String,
    val lat: Double?,
    val lng: Double?,
)
