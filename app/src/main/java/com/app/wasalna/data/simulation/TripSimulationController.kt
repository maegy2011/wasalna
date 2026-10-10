package com.app.wasalna.data.simulation

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TRIP SIMULATION (وضع محاكاة للرحلة للتجربة) — test-only virtual GPS.
 *
 * Drives the SAME production path as real GPS: TrackingService feeds every
 * synthetic fix into its existing onFix() (DynamicWakeupEstimator +
 * 500 m FAILSAFE + pre-arrival nudge + gradual alarm), so a simulated
 * trip exercises the full alarm chain end-to-end with zero network.
 *
 * Seam design (minimal, production untouched when off):
 * - TrackingService checks [isActive] in startTracking(): sim ON → collect
 *   [fixes] into onFix(); sim OFF → the FusedLocationProviderClient path
 *   runs exactly as before (zero behavior change, guarded by one boolean).
 * - Stopping the simulation deactivates the controller; the service watches
 *   [isActiveFlow] and re-requests real fused updates instantly.
 *
 * All movement math runs on Dispatchers.Default (computation, never Main).
 * State is exposed as StateFlow for Compose. No `!!` anywhere.
 */
object TripSimulationController {

    /** Base virtual speed (~43 km/h city driving); multiplied by 1x/4x/16x. */
    const val BASE_SPEED_MPS = 12f

    /** Fix emission cadence — matches the production 10 s bucket at 1x. */
    const val TICK_MS = 1_000L

    /** Adjustable speed chips (Arabic labels in the panel). */
    val SPEEDS = listOf(1, 4, 16)

    private const val PROVIDER = "wasalna-sim"
    private const val SIM_ACCURACY_M = 5f

    private val simScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    /** Seam flag read by TrackingService (sync, no suspension). */
    val isActive: Boolean get() = _isSimulating.value
    val isActiveFlow: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1)
    val speedMultiplier: StateFlow<Int> = _speedMultiplier.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _fixes = MutableSharedFlow<Location>(extraBufferCapacity = 64)
    val fixes: SharedFlow<Location> = _fixes.asSharedFlow()

    private var simJob: Job? = null

    /**
     * Starts a virtual trip from the origin to the destination. [routeGeoJson]
     * is the cached OSRM FeatureCollection (reused when present); otherwise
     * a straight GPS line is used — fully offline either way. Synchronous
     * flag flip first so TrackingService sees [isActive] immediately.
     */
    fun start(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        routeGeoJson: String?,
        baseSpeedMps: Float = BASE_SPEED_MPS,
    ) {
        stopInternal()
        _isPaused.value = false
        _progress.value = 0f
        if (_speedMultiplier.value !in SPEEDS) _speedMultiplier.value = 1
        _isSimulating.value = true
        simJob = simScope.launch {
            val points = parseRoutePoints(routeGeoJson, originLat, originLng, destLat, destLng)
            runSimulation(points, baseSpeedMps)
        }
    }

    fun setSpeedMultiplier(multiplier: Int) {
        if (multiplier in SPEEDS) _speedMultiplier.value = multiplier
    }

    fun pause() {
        _isPaused.value = true
    }

    fun resume() {
        _isPaused.value = false
    }

    /** User stop — deactivates; the service restores real GPS instantly. */
    fun stop() {
        stopInternal()
        _isSimulating.value = false
        _isPaused.value = false
    }

    /** Called by TrackingService on fireAlarm()/onDestroy() (idempotent). */
    fun notifyTripEnded() {
        stopInternal()
        _isSimulating.value = false
        _isPaused.value = false
    }

    private fun stopInternal() {
        simJob?.cancel()
        simJob = null
    }

    /**
     * Advances along [points] emitting a synthetic fix every [TICK_MS].
     * Reports the TRUE virtual speed (base × multiplier) so the TIME-mode
     * ETA counts down consistently with the movement. After arrival keeps
     * emitting the destination fix until stopped (alarm normally fires
     * first via the production failsafe).
     */
    private suspend fun runSimulation(points: List<Point>, baseSpeedMps: Float) {
        if (points.size < 2) {
            val last = points.lastOrNull() ?: return
            while (_isSimulating.value) {
                ensureActive()
                emitFix(last.lat, last.lng, baseSpeedMps, 0f)
                _progress.value = 1f
                delay(TICK_MS)
            }
            return
        }
        val cumulative = cumulativeMeters(points)
        val total = cumulative.last()
        if (total < 1f) {
            val last = points.last()
            while (_isSimulating.value) {
                ensureActive()
                emitFix(last.lat, last.lng, baseSpeedMps, 0f)
                _progress.value = 1f
                delay(TICK_MS)
            }
            return
        }
        var travelled = 0f
        while (_isSimulating.value && travelled < total) {
            ensureActive()
            if (!_isPaused.value) {
                val speed = baseSpeedMps * _speedMultiplier.value.toFloat()
                travelled = (travelled + speed * (TICK_MS / 1000f)).coerceAtMost(total)
                val pos = interpolate(points, cumulative, travelled)
                emitFix(pos.lat, pos.lng, speed, pos.bearing)
                _progress.value = (travelled / total).coerceIn(0f, 1f)
            }
            delay(TICK_MS)
        }
        val end = points.last()
        val cruise = baseSpeedMps * _speedMultiplier.value.toFloat()
        while (_isSimulating.value) {
            ensureActive()
            if (!_isPaused.value) emitFix(end.lat, end.lng, cruise, 0f)
            delay(TICK_MS)
        }
    }

    private suspend fun emitFix(lat: Double, lng: Double, speedMps: Float, bearing: Float) {
        val fix = Location(PROVIDER).apply {
            latitude = lat
            longitude = lng
            speed = speedMps
            accuracy = SIM_ACCURACY_M
            this.bearing = bearing
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        _fixes.emit(fix)
    }

    data class Point(val lat: Double, val lng: Double, val bearing: Float = 0f)

    /**
     * Reuses the cached OSRM line ([lng, lat] pairs); falls back to a
     * straight origin→destination line when absent/unparseable — offline
     * by construction. Origin prepended / destination appended when the
     * cached line starts/ends further than [SNAP_M] away.
     */
    private fun parseRoutePoints(
        routeGeoJson: String?,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): List<Point> {
        val parsed = runCatching {
            val geo = routeGeoJson?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val coords = JSONObject(geo)
                .optJSONArray("features")
                ?.optJSONObject(0)
                ?.optJSONObject("geometry")
                ?.optJSONArray("coordinates") ?: return@runCatching null
            val out = ArrayList<Point>(coords.length())
            for (i in 0 until coords.length()) {
                val pair = coords.optJSONArray(i) ?: continue
                val lng = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (!lat.isNaN() && !lng.isNaN() && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    out.add(Point(lat, lng))
                }
            }
            out.takeIf { it.size >= 2 }
        }.getOrNull()
        val line = parsed ?: listOf(Point(originLat, originLng), Point(destLat, destLng))
        val withOrigin = if (haversineM(line.first(), Point(originLat, originLng)) > SNAP_M) {
            listOf(Point(originLat, originLng)) + line
        } else {
            line
        }
        val last = withOrigin.last()
        return if (haversineM(last, Point(destLat, destLng)) > SNAP_M) {
            withOrigin + Point(destLat, destLng)
        } else {
            withOrigin
        }
    }

    private fun cumulativeMeters(points: List<Point>): FloatArray {
        val out = FloatArray(points.size)
        var acc = 0f
        for (i in 1 until points.size) {
            acc += haversineM(points[i - 1], points[i])
            out[i] = acc
        }
        return out
    }

    private fun interpolate(points: List<Point>, cumulative: FloatArray, at: Float): Point {
        var seg = 1
        while (seg < cumulative.size - 1 && cumulative[seg] < at) seg++
        val segStart = cumulative[seg - 1]
        val segLen = cumulative[seg] - segStart
        val frac = if (segLen > 0f) ((at - segStart) / segLen).coerceIn(0f, 1f) else 0f
        val a = points[seg - 1]
        val b = points[seg]
        return Point(
            lat = a.lat + (b.lat - a.lat) * frac,
            lng = a.lng + (b.lng - a.lng) * frac,
            bearing = bearingDeg(a, b),
        )
    }

    private fun haversineM(a: Point, b: Point): Float {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val s = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return (2 * r * asin(sqrt(s))).toFloat()
    }

    private fun bearingDeg(a: Point, b: Point): Float {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return ((Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360).toFloat()
    }

    /** Cancels any in-flight tick loop (test/support hook). */
    fun cancelForTests() {
        simScope.coroutineContext.cancelChildren()
    }

    companion object {
        private const val SNAP_M = 20f
    }
}
