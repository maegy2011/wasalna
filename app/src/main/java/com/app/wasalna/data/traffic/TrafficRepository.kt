package com.app.wasalna.data.traffic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

/**
 * Live-traffic data source, cloned from Vela's approach
 * (https://github.com/PimpinPumpkin/Vela — studied 9 Oct 2026, /tmp/vela-traffic-ref).
 *
 * -- VELA FINDINGS SUMMARY (see also TrafficOverlay.kt) --
 * - Endpoint: Google's public keyless raster tiles, the web map's own `/maps/vt` tile:
 *   `https://www.google.com/maps/vt/pb=!1m4!1m3!1i{z}!2i{x}!3i{y}!2m9!1e2!2straffic`
 *   `!3i999999!4m2!1sincidents!2s1!4m2!1sincidents_text!2s1`
 *   `!3m8!2sen!3sus!5e1105!12m4!1e68!2m2!1sset!2sRoadmap!4e0!5m1!1e0`
 *   (`!2straffic` = traffic layer, `!1e2` = overlay, standard XYZ tile coords.)
 * - Request format: plain raster PNG tiles fetched by the map engine itself
 *   (MapLibre RasterSource + TileSet) — NO API key, NO browser identity headers
 *   (Vela docs: "The map's own Google tiles ... go through the map engine's HTTP
 *   client. They do not carry the browser identity and are not counted.").
 *   Re-verified 9 Oct 2026 vs fresh clone: health probes here carry NO headers
 *   either (headerless GET), matching Vela's no-identity tile policy.
 * - Parameters: XYZ tile coords come from the CURRENT map viewport automatically;
 *   incidents + incidents_text sublayers on, locale en/us, Roadmap style.
 * - Response parsing: NONE — congestion is baked into the PNG (colored roads +
 *   incident icons) and composited by the engine. There is no structured
 *   congestion feed to parse; per-route congestion lives only in Google's
 *   directions reply (durationInTrafficSeconds + trafficSpans), which Wasalna
 *   deliberately does NOT call (privacy + unofficial-endpoint fragility).
 * - Update cadence: engine-native — tiles refetch on camera move + tile expiry;
 *   Vela adds rasterOpacity 0.6 + rasterFadeDuration 900ms against flicker.
 *   No manual polling, no debounce code, no tile cache of its own.
 * - Caching: MapLibre's own tile cache. Vela gates the whole layer on its
 *   "Use without Google" switch (off entirely when enabled).
 *
 * -- WASALNA PORT (this file) --
 * Rendering stays raster (see TrafficOverlay.ensureTraffic): parsing congestion
 * levels out of a PNG would be invented data, which is forbidden. This repository
 * therefore tracks OVERLAY HEALTH only (can we reach the tile host right now?)
 * and exposes it as [overlayState]; the map keeps showing the last good tiles
 * on failure (engine cache) and we silently retry. Empty = unknown/off, never fake.
 *
 * Privacy: fetching tiles sends the viewed viewport (tile x/y/z ~ location) to
 * Google. Toggle defaults OFF; an Arabic disclosure sits next to the toggle.
 *
 * Rules: all network on Dispatchers.IO, 12s timeouts, no !! anywhere,
 * all DTO/server fields nullable (there are none — raster bytes only).
 */
@Singleton
class TrafficRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    private val _overlayState =
        MutableStateFlow<TrafficOverlayState>(TrafficOverlayState.Disabled)
    val overlayState: StateFlow<TrafficOverlayState> = _overlayState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _overlayState.value = if (enabled) {
            when (_overlayState.value) {
                is TrafficOverlayState.Live -> TrafficOverlayState.Live
                else -> TrafficOverlayState.Checking
            }
        } else {
            TrafficOverlayState.Disabled
        }
    }

    /**
     * Probe reachability of the traffic tile host for the given viewport.
     * Runs fully on Dispatchers.IO. Returns true when a tile answered HTTP 200
     * with a non-empty body. NEVER throws; failure keeps the previous state
     * (Live -> Stale so the map keeps old tiles, otherwise Unavailable).
     */
    suspend fun probeViewport(
        centerLat: Double?,
        centerLng: Double?,
        zoom: Float?,
    ): Boolean = withContext(Dispatchers.IO) {
        val z = zoom?.toInt()?.coerceIn(MIN_ZOOM, MAX_ZOOM) ?: PROBE_ZOOM
        val (x, y) = tileFor(
            centerLat ?: PROBE_LAT,
            centerLng ?: PROBE_LNG,
            z,
        )
        // Retry ladder with exponential backoff (Vela routing uses 3x with
        // backoff; tiles are engine-native so this covers the health probe).
        // Headerless GET — Vela tiles carry no browser identity.
        var ok = false
        var backoffMs = RETRY_BASE_MS
        for (attempt in 0 until MAX_ATTEMPTS) {
            val single = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                runCatching { fetchTileOnce(z, x, y) }
                    .onFailure {
                        Log.w(TAG, "traffic probe attempt ${attempt + 1} failed: ${it.message}")
                    }.getOrDefault(false)
            } ?: false // timeout -> backoff + retry
            if (single) {
                ok = true
                break
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                delay(backoffMs)
                backoffMs *= 2
            }
        }

        if (_overlayState.value !is TrafficOverlayState.Disabled) {
            _overlayState.value = if (ok) {
                TrafficOverlayState.Live
            } else {
                when (_overlayState.value) {
                    is TrafficOverlayState.Live -> TrafficOverlayState.Stale
                    else -> TrafficOverlayState.Unavailable
                }
            }
        }
        ok
    }

    /** One headerless tile GET. Blocking — caller must be on Dispatchers.IO. */
    private fun fetchTileOnce(z: Int, x: Int, y: Int): Boolean {
        val request = Request.Builder()
            .url(tileUrl(z, x, y))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "traffic probe HTTP ${response.code}")
                return false
            }
            val bytes = response.body?.bytes()
            val ok = bytes != null && bytes.isNotEmpty()
            if (!ok) Log.w(TAG, "traffic probe empty body")
            return ok
        }
    }

    /** Viewport center -> XYZ tile (Web-Mercator slippy-map math). Null-safe. */
    fun tileFor(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val n = 1 shl z
        val x = floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = (lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0)
        val y = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt()
            .coerceIn(0, n - 1)
        return x to y
    }

    companion object {
        /** Vela's keyless Google traffic raster template ({z}/{x}/{y} for the engine). */
        const val TILE_TEMPLATE =
            "https://www.google.com/maps/vt/pb=!1m4!1m3!1i{z}!2i{x}!3i{y}!2m9!1e2!2straffic!3i999999" +
                "!4m2!1sincidents!2s1!4m2!1sincidents_text!2s1!3m8!2sen!3sus!5e1105!12m4!1e68!2m2!1sset!2sRoadmap!4e0!5m1!1e0"

        /** Single-tile URL for health probes. */
        fun tileUrl(z: Int, x: Int, y: Int): String =
            TILE_TEMPLATE
                .replace("{z}", z.toString())
                .replace("{x}", x.toString())
                .replace("{y}", y.toString())

        private const val TAG = "TrafficRepository"
        private const val TIMEOUT_S = 12L
        private const val PROBE_TIMEOUT_MS = 14_000L
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BASE_MS = 1_000L
        private const val MIN_ZOOM = 0
        private const val MAX_ZOOM = 19
        private const val MAX_LAT = 85.05112878
        private const val PROBE_ZOOM = 12
        private const val PROBE_LAT = 30.0444 // Cairo fallback when viewport unknown
        private const val PROBE_LNG = 31.2357
    }
}

/**
 * Health of the raster traffic overlay. No congestion values here by design:
 * the overlay is a composited image, not parsed data — an empty/unknown state
 * is honest, invented segments would be fake data.
 */
sealed interface TrafficOverlayState {
    data object Disabled : TrafficOverlayState
    data object Checking : TrafficOverlayState
    data object Live : TrafficOverlayState
    data object Stale : TrafficOverlayState // fetch failed; map keeps old tiles
    data object Unavailable : TrafficOverlayState // never succeeded yet; show nothing new
}
