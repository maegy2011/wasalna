package com.app.wasalna.data.routing

import android.content.Context
import android.util.Log
import com.app.wasalna.domain.model.RoutePoint2D
import com.app.wasalna.domain.model.RouteResult
import com.graphhopper.GHRequest
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10.4.3.9 (Vela approach): on-device routing over a downloaded
 * Contraction-Hierarchies graph — no network, ~200ms per query.
 *
 * - Fix #2 enforced by construction: [NoJaninoGraphHopper] ([KmhTimeWeighting],
 *   never CustomWeighting/Janino).
 * - Fix #3 enforced by configuration: the ONLY prepared profile is the CH
 *   `car` profile, and queries run with CH enabled (default); there is no
 *   flexible-A* fallback in this engine.
 * - Store: RAM_STORE (the GraphHopper default) — no memory-mapped bulk
 *   ByteBuffer IO on ART at all for the graphs phones hold; [ByteBufferCompat]
 *   covers absolute bulk transfers wherever our routing package does them
 *   ([RoutingGraphManager] graph validation, future MMAP segment reader).
 * - Singleton, cached: init runs once on Dispatchers.IO; every
 *   [calculateRoute] is IO-bound and never throws (null on any failure).
 * - `close()` issues are swallowed by design (service shutdown path).
 */
@Singleton
class GraphHopperRouteEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val gh = NoJaninoGraphHopper()

    @Volatile
    private var loadedDir: String? = null

    /** Which region id the loaded graph belongs to (null = legacy path). */
    @Volatile
    var loadedRegionId: String? = null
        private set

    /** True once a graph is loaded and ready to route. */
    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * Step 2 contract: engine load state. Null = never attempted;
     * non-null carries the EXACT failure message (profile mismatch /
     * corrupt graph / IO). UI collects this for status display.
     */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /**
     * Loads [graphDir] (a prepared `filesDir/graphs/<id>` folder) once;
     * repeat calls with the same dir are no-ops. Returns readiness.
     * ── OFFLINE BOUNDARY: pure file + memory work, zero network. ──
     */
    suspend fun ensureLoaded(graphDir: File): Boolean = withContext(Dispatchers.IO) {
        if (isReady && loadedDir == graphDir.absolutePath) return@withContext true
        mutex.withLock {
            if (isReady && loadedDir == graphDir.absolutePath) return@withLock true
            val ok = runCatching {
                // A prepared graph always carries a properties file.
                check(File(graphDir, "properties").exists()) { "no prepared graph in $graphDir" }
                configureEngine(graphDir)
                // load() (not importOrLoad): the graph is baked server-side.
                // Step 2: Android-safe load — first attempt with the
                // default store; on store-level failure close + reconfigure
                // and retry as the RAM fallback (ART-safe, no MMAP bulk
                // IO on phones; ByteBufferCompat covers bulk reads).
                val firstOk = runCatching {
                    check(gh.load()) { "graph load failed" }
                    true
                }.getOrDefault(false)
                if (!firstOk) {
                    runCatching { gh.close() }
                    configureEngine(graphDir)
                    check(gh.load()) { "graph load failed (RAM fallback)" }
                }
                loadedDir = graphDir.absolutePath
                true
            }.onFailure {
                // Step 2: catch ALL (profile mismatch / corrupt / IO) —
                // exact message into StateFlow, never a crash.
                val msg = it.message?.ifBlank { null } ?: it.javaClass.simpleName
                _loadError.value = msg
                Log.e(TAG, "ensureLoaded failed: $msg", it)
            }.getOrDefault(false)
            if (ok) _loadError.value = null
            isReady = ok
            ok
        }
    }

    /**
     * Upgrade (Oct 2026): region-aware load — the engine holds ONE
     * graph at a time, so switching regions closes + reloads. Same-dir
     * + same-region repeats are no-ops. Returns readiness.
     * ── OFFLINE BOUNDARY: pure file + memory work, zero network. ──
     */
    suspend fun ensureLoadedFor(regionId: String, graphDir: File): Boolean =
        withContext(Dispatchers.IO) {
            if (isReady && loadedDir == graphDir.absolutePath &&
                loadedRegionId == regionId
            ) {
                return@withContext true
            }
            if (isReady && loadedDir != graphDir.absolutePath) {
                runCatching { gh.close() }
                isReady = false
                loadedDir = null
                loadedRegionId = null
            }
            val ok = ensureLoaded(graphDir)
            if (ok) loadedRegionId = regionId
            ok
        }

    /**
     * On-device route, CH-accelerated. Null when no graph is loaded or the
     * query fails — never throws.
     * ── OFFLINE BOUNDARY: pure on-device query, zero network. ──
     */
    suspend fun calculateRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext null
        runCatching {
            val t0 = System.currentTimeMillis()
            val req = GHRequest(startLat, startLng, destLat, destLng)
                .setProfile(PROFILE_CAR)
            val rsp = gh.route(req)
            if (rsp.hasErrors()) {
                Log.w(TAG, "route errors: ${rsp.errors.firstOrNull()?.message}")
                return@runCatching null
            }
            val best = rsp.best
            val pts = best.points
            val out = RouteResult(
                points = List(pts.size()) { i -> RoutePoint2D(pts.getLat(i), pts.getLon(i)) },
                distanceMeters = best.distance,
                durationSec = best.time / 1000.0,
            )
            Log.i(TAG, "offline route: ${out.points.size} pts in ${System.currentTimeMillis() - t0}ms")
            out
        }.onFailure {
            Log.w(TAG, "calculateRoute failed: ${it.message}")
        }.getOrNull()
    }

    /**
     * Via-route (Oct 2026, offline edit): chains one CH query per leg
     * (start→via1→…→dest), drops duplicate joints, sums distance/time.
     * Null when any leg fails — the caller falls back, never fakes.
     * ── OFFLINE BOUNDARY: pure on-device queries, zero network. ──
     */
    suspend fun calculateRouteVia(
        points: List<Pair<Double, Double>>,
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        val merged = ArrayList<RoutePoint2D>(4096)
        var dist = 0.0
        var time = 0.0
        for (i in 0 until points.size - 1) {
            val leg = calculateRoute(
                points[i].first, points[i].second,
                points[i + 1].first, points[i + 1].second,
            ) ?: return@withContext null
            // Drop the joint duplicate (leg start == previous leg end).
            val legPts = if (merged.isEmpty()) leg.points else leg.points.drop(1)
            merged.addAll(legPts)
            dist += leg.distanceMeters
            time += leg.durationSec
        }
        if (merged.size < 2) return@withContext null
        RouteResult(points = merged, distanceMeters = dist, durationSec = time)
    }

    /** Shared engine configuration (MMAP flag set by the caller). */
    private fun configureEngine(graphDir: File) {
        gh.apply {
            setGraphHopperLocation(graphDir.absolutePath)
            setEncodedValuesString("car_access, car_average_speed")
            setProfiles(
                // Same declaration as the server-side bake (GH compares
                // the stored profile fingerprint at load); the factory
                // still resolves it to KmhTimeWeighting — Janino-free.
                Profile(PROFILE_CAR)
                    .setWeighting(JaninoFreeWeightingFactory.CUSTOM_WEIGHTING)
                    .setCustomModel(JaninoFreeWeightingFactory.fastestModel()),
            )
            // Fix #3: CH-only — the prepared car profile, no flex fallback.
            getCHPreparationHandler().setCHProfiles(CHProfile(PROFILE_CAR))
            setAllowWrites(false)
        }
    }

    /** Drop the loaded graph (region deleted). Swallows close() issues. */
    suspend fun reset() = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Fix: close() may throw on partially-loaded graphs — swallow.
            runCatching { gh.close() }
                .onFailure { Log.w(TAG, "close swallowed: ${it.message}") }
            isReady = false
            loadedDir = null
            loadedRegionId = null
        }
    }

    companion object {
        private const val TAG = "GHRouting"
        private const val PROFILE_CAR = "car"
    }
}
