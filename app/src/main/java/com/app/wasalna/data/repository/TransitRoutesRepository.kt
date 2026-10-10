package com.app.wasalna.data.repository

import android.util.Log
import com.app.wasalna.domain.model.LatLngD
import com.app.wasalna.domain.model.PolylineDecoder
import com.app.wasalna.domain.model.TransitPlan
import com.app.wasalna.domain.model.TransitStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live public-transit routing via Google **Routes API v2**
 * (`POST https://routes.googleapis.com/directions/v2:computeRoutes`,
 * `travelMode: TRANSIT` — docs rev 2026-10-01, studied Oct 2026).
 *
 * BYOK contract (user order Oct 2026):
 * - The key is entered BY THE USER in Settings and read from DataStore
 *   at call time — NO key is embedded in the code, in resources, or in
 *   BuildConfig. Empty/blank key → null (caller keeps APPROXIMATE).
 * - The key travels ONLY as the `X-Goog-Api-Key` HTTPS header and is
 *   NEVER logged (success/failure logs carry status codes only).
 * - Call runs on Dispatchers.IO with 15 s connect/read timeouts; ANY
 *   failure (network, quota, bad key, unparsable body) returns null —
 *   the caller falls back to the OSRM reference corridor + approximate
 *   guide with an Arabic notice. Never throws, never blocks Main.
 * - OFFLINE BOUNDARY: MapViewModel checks force-offline BEFORE calling
 *   here; TrackingService (alarm path) never calls here at all.
 *
 * Request: latLng origin/destination (no intermediate waypoints —
 * transit forbids them), `transitPreferences.routingPreference:
 * LESS_WALKING` (clearer alighting for an alarm app), `languageCode:
 * ar` so stop/line names arrive Arabic-first where Google has them.
 * Field mask pulls duration + per-step travelMode/transitDetails/
 * polyline/distance — nothing more (minimal billing surface).
 */
@Singleton
class TransitRoutesRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPlan(
        apiKey: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): TransitPlan? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        runCatching {
            val body = JSONObject()
                .put(
                    "origin",
                    JSONObject().put(
                        "location",
                        JSONObject().put(
                            "latLng",
                            JSONObject()
                                .put("latitude", originLat)
                                .put("longitude", originLng),
                        ),
                    ),
                )
                .put(
                    "destination",
                    JSONObject().put(
                        "location",
                        JSONObject().put(
                            "latLng",
                            JSONObject()
                                .put("latitude", destLat)
                                .put("longitude", destLng),
                        ),
                    ),
                )
                .put("travelMode", "TRANSIT")
                .put("languageCode", "ar")
                .put(
                    "transitPreferences",
                    JSONObject().put("routingPreference", "LESS_WALKING"),
                )
                .toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey.trim())
                .header("X-Goog-FieldMask", FIELD_MASK)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "transit HTTP ${response.code}")
                    return@runCatching null
                }
                val text = response.body?.string() ?: return@runCatching null
                parsePlan(JSONObject(text))
            }
        }.onFailure {
            Log.e(TAG, "transit fetch failed: ${it.message}")
        }.getOrNull()
    }

    /** First route → steps; null when the shape is unusable (never throws). */
    private fun parsePlan(root: JSONObject): TransitPlan? = runCatching {
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null
        val first = routes.getJSONObject(0)
        val legs = first.optJSONArray("legs") ?: return null
        if (legs.length() == 0) return null
        val stepsJson = legs.getJSONObject(0).optJSONArray("steps")
            ?: return null
        val steps = ArrayList<TransitStep>(stepsJson.length())
        for (i in 0 until stepsJson.length()) {
            val s = stepsJson.optJSONObject(i) ?: continue
            val mode = s.optString("travelMode", "WALK").ifBlank { "WALK" }
            val td = s.optJSONObject("transitDetails")
            val line = td?.optJSONObject("transitLine")
            val stops = td?.optJSONObject("stopDetails")
            val loc = td?.optJSONObject("localizedValues")
            val points = PolylineDecoder.decode(
                s.optJSONObject("polyline")?.optString("points"),
            ).ifEmpty {
                // No polyline (rare): bridge start→end so the map line
                // stays continuous — endpoints are real API coordinates.
                val a = s.optJSONObject("startLocation")?.optJSONObject("latLng")
                val b = s.optJSONObject("endLocation")?.optJSONObject("latLng")
                if (a != null && b != null) {
                    listOf(
                        LatLngD(a.optDouble("latitude"), a.optDouble("longitude")),
                        LatLngD(b.optDouble("latitude"), b.optDouble("longitude")),
                    )
                } else {
                    emptyList()
                }
            }
            steps.add(
                TransitStep(
                    travelMode = mode,
                    lineName = line?.optString("name")?.ifBlank { null },
                    lineShort = line?.optString("nameShort")?.ifBlank { null },
                    vehicleType = line?.optJSONObject("vehicle")
                        ?.optString("type")?.ifBlank { null },
                    departStop = stops?.optJSONObject("departureStop")
                        ?.optString("name")?.ifBlank { null },
                    arriveStop = stops?.optJSONObject("arrivalStop")
                        ?.optString("name")?.ifBlank { null },
                    departTime = loc?.optJSONObject("departureTime")
                        ?.optJSONObject("time")?.optString("text")?.ifBlank { null },
                    arriveTime = loc?.optJSONObject("arrivalTime")
                        ?.optJSONObject("time")?.optString("text")?.ifBlank { null },
                    stopCount = td?.optInt("stopCount", -1)
                        ?.takeIf { it >= 0 },
                    distanceM = s.optDouble("distanceMeters", 0.0),
                    points = points,
                ),
            )
        }
        if (steps.isEmpty()) return null
        // v2 expresses duration as a string ("1234s") — strip + parse.
        val durSec = first.optString("duration", "")
            .trim().removeSuffix("s").toDoubleOrNull() ?: -1.0
        TransitPlan(steps = steps, durationSec = durSec)
    }.getOrNull()

    companion object {
        private const val TAG = "TransitRoutes"
        const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val FIELD_MASK = "routes.duration," +
            "routes.legs.steps.travelMode," +
            "routes.legs.steps.transitDetails," +
            "routes.legs.steps.polyline," +
            "routes.legs.steps.distanceMeters," +
            "routes.legs.steps.startLocation," +
            "routes.legs.steps.endLocation"
    }
}
