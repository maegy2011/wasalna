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
     * Keyless place autocomplete. When [lat]/[lon] are supplied Photon
     * biases results toward the user's actual country/city; without them
     * it falls back to unbiased global search. Empty list on any failure —
     * never throws (runs on Dispatchers.IO).
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
            runCatching {
                val bias = if (lat != null && lon != null) "&lat=$lat&lon=$lon" else ""
                val url = PHOTON_URL + "?q=${encode(query)}$bias&limit=10&lang=en"
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

    private fun parsePhoton(body: String): List<SearchResult> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return List(features.length()) { i ->
            val feature = features.optJSONObject(i) ?: return@List null
            val props = feature.optJSONObject("properties")
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            val lng = coords?.optDouble(0)
            val lat = coords?.optDouble(1)
            if (props == null || lat == null || lng == null) return@List null
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
    }
}
