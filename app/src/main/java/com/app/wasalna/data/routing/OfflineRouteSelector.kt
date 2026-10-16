package com.app.wasalna.data.routing

import com.app.wasalna.domain.model.RouteResult

/**
 * Upgrade (Oct 2026): offline-routing decision point, single place.
 *
 * GraphHopper loads ONE graph at a time (no multi-graph merge), so a
 * route is offline-routable iff origin→destination both sit inside ONE
 * INSTALLED graph bbox. Anything else → [SpansUnstored]: the caller keeps
 * the map + GPS straight-line fallback and shows an Arabic notice —
 * never a faked route.
 *
 * Pure logic + IO-bound engine calls only — zero network here.
 * ── OFFLINE BOUNDARY: this whole file is offline-safe. ──
 */
sealed interface OfflineRouteDecision {
    /** Routable: load [region] then query the engine. */
    data class Routable(val region: RoutingRegion) : OfflineRouteDecision

    /**
     * Not routable offline: [knownRegion] names the graph to download
     * (null = no known graph covers the route at all).
     */
    data class SpansUnstored(val knownRegion: RoutingRegion?) : OfflineRouteDecision
}

/** Pure decision: which installed graph (if any) covers the route. */
fun decideOfflineRoute(
    manager: RoutingGraphManager,
    sLat: Double,
    sLng: Double,
    dLat: Double,
    dLng: Double,
): OfflineRouteDecision {
    val covering = manager.coveringRegionFor(sLat, sLng, dLat, dLng)
        ?.takeIf { regionCoversMidpoints(it, sLat, sLng, dLat, dLng) }
    if (covering != null) return OfflineRouteDecision.Routable(covering)
    return OfflineRouteDecision.SpansUnstored(
        manager.knownRegionFor(sLat, sLng, dLat, dLng),
    )
}

/**
 * R2 road-aware guard: bbox endpoints are not enough — roads near a
 * graph border can leave the baked network mid-route. Samples the
 * 25/50/75% points of every leg; all must sit inside the bbox or the
 * region is rejected (caller falls back, never fakes). The engine
 * query itself is the final arbiter (null on true road gaps).
 */
fun regionCoversMidpoints(
    region: RoutingRegion,
    sLat: Double,
    sLng: Double,
    dLat: Double,
    dLng: Double,
): Boolean = MIDPOINT_FRACTIONS.all { f ->
    region.contains(
        sLat + (dLat - sLat) * f,
        sLng + (dLng - sLng) * f,
    )
}

/** Same guard for via routes: every leg's midpoints must stay inside. */
fun regionCoversAllMidpoints(
    region: RoutingRegion,
    points: List<Pair<Double, Double>>,
): Boolean {
    if (points.size < 2) return false
    for (i in 0 until points.size - 1) {
        val (sLat, sLng) = points[i]
        val (dLat, dLng) = points[i + 1]
        if (!regionCoversMidpoints(region, sLat, sLng, dLat, dLng)) return false
    }
    return true
}

private val MIDPOINT_FRACTIONS = doubleArrayOf(0.25, 0.5, 0.75)

/**
 * Executes the decision: loads the covering graph (region-aware,
 * auto-switches when the route moves regions) and queries the engine.
 * Null on any failure — caller applies the GPS straight-line fallback.
 * All heavy work on Dispatchers.IO inside manager/engine — never Main.
 * ── OFFLINE BOUNDARY: no network in this path. ──
 */
suspend fun routeOfflineWithAutoSelect(
    manager: RoutingGraphManager,
    engine: GraphHopperRouteEngine,
    sLat: Double,
    sLng: Double,
    dLat: Double,
    dLng: Double,
): Pair<RoutingRegion, RouteResult>? {
    val decision = decideOfflineRoute(manager, sLat, sLng, dLat, dLng)
    val region = (decision as? OfflineRouteDecision.Routable)?.region
        ?: return null
    val dir = manager.graphDir(region.id)
    if (!engine.ensureLoadedFor(region.id, dir)) return null
    val result = engine.calculateRoute(sLat, sLng, dLat, dLng)
        ?: return null
    return region to result
}

/**
 * Via-route executor (Oct 2026, offline edit): origin + stops +
 * destination must ALL sit inside one installed graph; legs chain in
 * the engine. Null on any failure — caller falls back, never fakes.
 * ── OFFLINE BOUNDARY: no network in this path. ──
 */
suspend fun routeOfflineWithAutoSelectVia(
    manager: RoutingGraphManager,
    engine: GraphHopperRouteEngine,
    points: List<Pair<Double, Double>>,
): Pair<RoutingRegion, RouteResult>? {
    if (points.size < 2) return null
    val region = manager.coveringRegionForAll(points)
        ?.takeIf { regionCoversAllMidpoints(it, points) } ?: return null
    val dir = manager.graphDir(region.id)
    if (!engine.ensureLoadedFor(region.id, dir)) return null
    val result = engine.calculateRouteVia(points) ?: return null
    return region to result
}
