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

    /** True once a graph is loaded and ready to route. */
    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * Loads [graphDir] (a prepared `filesDir/graphs/<id>` folder) once;
     * repeat calls with the same dir are no-ops. Returns readiness.
     */
    suspend fun ensureLoaded(graphDir: File): Boolean = withContext(Dispatchers.IO) {
        if (isReady && loadedDir == graphDir.absolutePath) return@withContext true
        mutex.withLock {
            if (isReady && loadedDir == graphDir.absolutePath) return@withLock true
            val ok = runCatching {
                // A prepared graph always carries a properties file.
                check(File(graphDir, "properties").exists()) { "no prepared graph in $graphDir" }
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
                    // RAM_STORE is the default — explicit MMAP (via
                    // init(GraphHopperConfig)) stays reserved for hundred-MB
                    // graphs, with ByteBufferCompat for its bulk IO.
                }
                // load() (not importOrLoad): the graph is baked server-side.
                check(gh.load()) { "graph load failed" }
                loadedDir = graphDir.absolutePath
                true
            }.onFailure {
                Log.w(TAG, "ensureLoaded failed: ${it.message}")
            }.getOrDefault(false)
            isReady = ok
            ok
        }
    }

    /**
     * On-device route, CH-accelerated. Null when no graph is loaded or the
     * query fails — never throws.
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

    /** Drop the loaded graph (region deleted). Swallows close() issues. */
    suspend fun reset() = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Fix: close() may throw on partially-loaded graphs — swallow.
            runCatching { gh.close() }
                .onFailure { Log.w(TAG, "close swallowed: ${it.message}") }
            isReady = false
            loadedDir = null
        }
    }

    companion object {
        private const val TAG = "GHRouting"
        private const val PROFILE_CAR = "car"
    }
}
