package com.app.wasalna.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Station-to-station transit guidance (user order Oct 2026: train /
 * metro / Cairo monorail inside the pre-trip vehicle picker).
 *
 * HONESTY CONTRACT (no invented routing — OSRM has no transit profile
 * and open-source GraphHopper has no transit engine):
 * - METRO: REAL station coords + REAL order + REAL interchange topology
 *   (L1/L2 official GTFS mdb-3354; L3 Wikipedia DMS). L1/L2 ride times
 *   are REAL GTFS schedule segment seconds; L3 is distance-estimated
 *   ([TransitTiming.ESTIMATED]) and the plan is flagged [approximate].
 * - MONORAIL / LRT: station ORDER is real (published), but per-station
 *   coords are unavailable (no GTFS; OSM mapping incomplete) — the plan
 *   is line-level (line, direction, stop count, est. minutes) and ALWAYS
 *   flagged [approximate] with a verify-schedules notice. NO fake
 *   polyline is ever drawn for these legs.
 * - West Nile monorail is under construction (no date) → not routed.
 * - Heavy-rail (ENR) has no open data → not offered; TRAIN = LRT Capital
 *   Train. Outside the LRT corridor the plan is NONE + corridor notice.
 *
 * Pure Kotlin, no Android deps — safe on Dispatchers.Default (never Main).
 * All haversine math is O(stations) — trivial (<1 ms).
 */
object TransitPlanner {

    /** Beyond this walk to/from a station the mode is refused (honest). */
    const val MAX_WALK_M = 3000.0

    /** Flat wait assumption for ESTIMATED systems (labeled approximate). */
    const val EST_WAIT_MIN = 5.0

    /** Single-transfer penalty (walk + wait), minutes. */
    const val TRANSFER_MIN = 8.0

    /** Walk speed for access/egress legs (5 km/h stroll). */
    const val WALK_M_PER_MIN = 80.0

    data class RideSegment(
        val line: TransitLine,
        val from: TransitStop,
        val to: TransitStop,
        /** Stations boarded (excluding [from], including [to]). */
        val stops: List<TransitStop>,
        val minutes: Double,
        val scheduled: Boolean,
    )

    data class TransitItinerary(
        /** DIRECT (no transfer) / TRANSFER (one) / NONE (not routable). */
        val kind: String,
        val board: TransitStop?,
        val alight: TransitStop?,
        val segments: List<RideSegment>,
        val walkOriginM: Double,
        val walkDestM: Double,
        /** Total door-to-door minutes (walk + wait + ride + transfer). */
        val totalMin: Int,
        /** True when ANY part is estimated → UI must show the notice. */
        val approximate: Boolean,
        /** Machine-readable reason for NONE (corridor/offline/…). */
        val reason: String = "",
    ) {
        /** Ride stations in travel order (for map polyline + sim stops). */
        fun ridePoints(): List<TransitStop> {
            val out = ArrayList<TransitStop>()
            segments.forEach { seg ->
                if (out.isEmpty()) out.add(seg.from)
                out.addAll(seg.stops)
            }
            return out
        }
    }

    fun linesFor(mode: TransportMode): List<TransitLine> = when (mode) {
        TransportMode.METRO -> CairoTransit.METRO_ROUTES
        TransportMode.MONORAIL -> CairoTransit.MONO_ROUTES
        TransportMode.TRAIN -> CairoTransit.LRT_ROUTES
        else -> emptyList()
    }

    /**
     * Plans origin→destination on [mode]'s system. Null-coord stations
     * (monorail/LRT) can never be nearest-matched — those systems fall
     * back to line-level approximate plans (see below).
     */
    fun plan(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        mode: TransportMode,
    ): TransitItinerary {
        val routes = linesFor(mode).filter { it.operational }
        if (routes.isEmpty()) {
            return TransitItinerary("NONE", null, null, emptyList(), 0.0, 0.0, 0, true, "no_line")
        }
        val coordRoutes = routes.filter { r -> r.stations.any { it.lat != null } }
        if (coordRoutes.isEmpty()) {
            return lineLevelPlan(routes, originLat, originLng, destLat, destLng)
        }
        val board = nearest(originLat, originLng, coordRoutes)
        val alight = nearest(destLat, destLng, coordRoutes)
        if (board == null || alight == null) {
            return TransitItinerary("NONE", null, null, emptyList(), 0.0, 0.0, 0, true, "no_station")
        }
        // Same nearest station both ends → pure walk, no ride needed.
        if (board.second.id == alight.second.id) {
            val walkM = haversineM(originLat, originLng, destLat, destLng)
            return TransitItinerary(
                kind = "DIRECT",
                board = board.second,
                alight = alight.second,
                segments = emptyList(),
                walkOriginM = walkM,
                walkDestM = 0.0,
                totalMin = (walkM / WALK_M_PER_MIN).toInt().coerceAtLeast(2),
                approximate = false,
            )
        }
        val walkO = haversineM(originLat, originLng, board.second.lat ?: 0.0, board.second.lng ?: 0.0)
        val walkD = haversineM(destLat, destLng, alight.second.lat ?: 0.0, alight.second.lng ?: 0.0)
        if (walkO > MAX_WALK_M || walkD > MAX_WALK_M) {
            return TransitItinerary("NONE", board.second, alight.second, emptyList(), walkO, walkD, 0, true, "off_corridor")
        }
        val segs = routeSegments(board, alight, coordRoutes)
        if (segs.isEmpty()) {
            return TransitItinerary("NONE", board.second, alight.second, emptyList(), walkO, walkD, 0, true, "no_route")
        }
        val rideMin = segs.sumOf { it.minutes }
        val waitMin = segs.sumOf { seg -> seg.line.headwayMin ?: EST_WAIT_MIN }
        val transferMin = if (segs.size > 1) TRANSFER_MIN else 0.0
        val total = (walkO / WALK_M_PER_MIN + walkD / WALK_M_PER_MIN + waitMin + rideMin + transferMin)
        val approx = segs.any { !it.scheduled }
        return TransitItinerary(
            kind = if (segs.size > 1) "TRANSFER" else "DIRECT",
            board = board.second,
            alight = alight.second,
            segments = segs,
            walkOriginM = walkO,
            walkDestM = walkD,
            totalMin = total.toInt().coerceAtLeast(2),
            approximate = approx,
        )
    }

    /**
     * Line-level fallback (monorail/LRT — no station coords): picks the
     * line whose corridor (termini bounding area) best contains the trip,
     * reports direction + stop span. ALWAYS approximate. Used only to
     * inform the rider (which line, which direction, ~how long) — never
     * a map route.
     */
    private fun lineLevelPlan(
        routes: List<TransitLine>,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): TransitItinerary {
        // Only termini/interchange anchors have no coords either — the
        // honest output is the line + estimated end-to-end span. Pick the
        // first operational route of the system (single-line systems).
        val line = routes.firstOrNull()
            ?: return TransitItinerary("NONE", null, null, emptyList(), 0.0, 0.0, 0, true, "no_line")
        val n = line.stations.size
        if (n < 2) {
            return TransitItinerary("NONE", null, null, emptyList(), 0.0, 0.0, 0, true, "no_line")
        }
        // Full-line ride estimate (rider boards/alights wherever fits —
        // counted as the whole span, an upper bound, clearly approximate).
        val first = line.stations.first()
        val last = line.stations.last()
        val seg = RideSegment(line, first, last, line.stations.drop(1), (n - 1) * 3.0, false)
        val total = EST_WAIT_MIN + seg.minutes
        return TransitItinerary(
            kind = "DIRECT",
            board = first,
            alight = last,
            segments = listOf(seg),
            walkOriginM = 0.0,
            walkDestM = 0.0,
            totalMin = total.toInt().coerceAtLeast(2),
            approximate = true,
            reason = "line_level",
        )
    }

    /** Nearest (line, station) with real coords across [routes]. */
    private fun nearest(lat: Double, lng: Double, routes: List<TransitLine>): Pair<TransitLine, TransitStop>? {
        var best: Pair<TransitLine, TransitStop>? = null
        var bestM = Double.MAX_VALUE
        routes.forEach { line ->
            line.stations.forEach { st ->
                val sla = st.lat ?: return@forEach
                val slo = st.lng ?: return@forEach
                val m = haversineM(lat, lng, sla, slo)
                if (m < bestM) {
                    bestM = m
                    best = line to st
                }
            }
        }
        return best
    }

    /**
     * Direct when both stations share one line instance (branch lists
     * share trunk instances, so trunk↔branch is direct via Kit Kat /
     * Badr with no transfer penalty). Else one transfer through the
     * first common station name across the two lines.
     */
    private fun routeSegments(
        board: Pair<TransitLine, TransitStop>,
        alight: Pair<TransitLine, TransitStop>,
        routes: List<TransitLine>,
    ): List<RideSegment> {
        val (lineA, stA) = board
        val (lineB, stB) = alight
        if (lineA.id == lineB.id) {
            return listOf(segmentOn(lineA, stA, stB)).filter { it.stops.isNotEmpty() }
        }
        // Branches sharing trunk instances (L3 fork at Kit Kat, LRT fork
        // at Badr): the route list containing BOTH stations carries the
        // whole ride with no transfer penalty.
        sharedRoute(stA, stB, routes)?.let { line ->
            return listOf(segmentOn(line, stA, stB)).filter { it.stops.isNotEmpty() }
        }
        // Transfer search: every station F carried with stA on one route
        // and with stB on another is a candidate via — the fewest-stop
        // candidate wins. This covers real transfers (Sadat, Attaba, …)
        // AND fork changes (Kit Kat between the L3 branches, Badr between
        // the LRT branches) with no special cases.
        data class Via(val f: TransitStop, val ra: TransitLine, val rb: TransitLine, val cost: Int)
        var best: Via? = null
        routes.flatMap { it.stations }.distinct().forEach { f ->
            if (f.id == stA.id || f.id == stB.id) return@forEach
            val ra = routes.firstOrNull { it.stations.contains(stA) && it.stations.contains(f) }
                ?: return@forEach
            val rb = routes.firstOrNull { it.stations.contains(f) && it.stations.contains(stB) }
                ?: return@forEach
            val cost = stopCount(ra, stA, f) + stopCount(rb, f, stB)
            if (cost > 0 && (best == null || cost < best.cost)) {
                best = Via(f, ra, rb, cost)
            }
        }
        val v = best ?: return emptyList()
        val fOnB = v.rb.stations.firstOrNull { it.id == v.f.id } ?: v.f
        val first = segmentOn(v.ra, stA, v.f)
        val second = segmentOn(v.rb, fOnB, stB)
        if (first.stops.isEmpty() || second.stops.isEmpty()) return emptyList()
        return listOf(first, second)
    }

    private fun stopCount(line: TransitLine, a: TransitStop, b: TransitStop): Int {
        val i = line.stations.indexOf(a)
        val j = line.stations.indexOf(b)
        if (i < 0 || j < 0) return Int.MAX_VALUE
        return kotlin.math.abs(i - j)
    }

    /** Route list containing BOTH stations (branch trunk sharing). */
    private fun sharedRoute(
        stA: TransitStop,
        stB: TransitStop,
        routes: List<TransitLine>,
    ): TransitLine? = routes.firstOrNull { r ->
        r.stations.contains(stA) && r.stations.contains(stB)
    }

    private fun segmentOn(line: TransitLine, from: TransitStop, to: TransitStop): RideSegment {
        val idx = line.stations.indexOf(from)
        val jdx = line.stations.indexOf(to)
        if (idx < 0 || jdx < 0 || idx == jdx) {
            return RideSegment(line, from, to, emptyList(), 0.0, line.timing == TransitTiming.SCHEDULE)
        }
        val range = if (idx < jdx) (idx + 1..jdx) else ((jdx until idx).reversed())
        val stops = range.map { line.stations[it] }
        val minutes = if (line.timing == TransitTiming.SCHEDULE && line.segSec.size == line.stations.size - 1) {
            val lo = minOf(idx, jdx)
            val hi = maxOf(idx, jdx)
            line.segSec.subList(lo, hi).sum() / 60.0
        } else {
            estimateMinutes(line, from, stops)
        }
        return RideSegment(line, from, to, stops, minutes, line.timing == TransitTiming.SCHEDULE)
    }

    /** Distance/speed + 30 s dwell per stop (ESTIMATED legs only). */
    private fun estimateMinutes(line: TransitLine, from: TransitStop, stops: List<TransitStop>): Double {
        var meters = 0.0
        var prev = from
        stops.forEach { st ->
            val aLa = prev.lat
            val aLo = prev.lng
            val bLa = st.lat
            val bLo = st.lng
            if (aLa != null && aLo != null && bLa != null && bLo != null) {
                meters += haversineM(aLa, aLo, bLa, bLo)
            }
            prev = st
        }
        return meters / 1000.0 / line.effKmh * 60.0 + stops.size * 0.5
    }

    fun key(st: TransitStop): String =
        st.nameEn.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    fun haversineM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val s = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(s))
    }
}
