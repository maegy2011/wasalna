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
    if (covering != null) return OfflineRouteDecision.Routable(covering)
    return OfflineRouteDecision.SpansUnstored(
        manager.knownRegionFor(sLat, sLng, dLat, dLng),
    )
}

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
