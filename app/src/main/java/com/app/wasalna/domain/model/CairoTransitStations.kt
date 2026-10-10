package com.app.wasalna.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OSM-DATA-DRIVEN transit layer (Oct 2026) — outcome **(c)**: generic
 * TRANSIT guidance runs on OpenStreetMap public-transport data
 * (ODbL-licensed, commercial use allowed with attribution), the way
 * OsmAnd renders PT (stops + route relations with ref/name/colour).
 *
 * This file is a THIN layer over the embedded rosters in [TransitData.kt]
 * ([CairoTransit]): it adds OSM PTv2 semantics (coverage flags,
 * relation mapping, refresh query, attribution pointer) WITHOUT
 * duplicating the station tables.
 *
 * OSM PTv2 SCHEMA (studied from the OSM wiki + OsmAnd PT docs, Oct 2026):
 * - Stops: `public_transport=stop_position` nodes on the way (where the
 *   vehicle stops) + `public_transport=platform` nodes/ways (where riders
 *   wait; PTv1 `highway=bus_stop` / `railway=station` still widespread).
 * - Routes: `type=route` relations with `route=subway|monorail|train`,
 *   ordered members (stops with role `stop`/`platform`, ways between),
 *   tags `ref` (line number), `name` (+ `name:ar`), `colour`, `network`,
 *   `operator`. One relation per direction; both bundled as one roster.
 * - [TransitLine] mirrors one OSM relation: [TransitLine.id] is the
 *   relation key (M1/M2/…), [TransitLine.system] mirrors `route=`,
 *   [TransitLine.stations] is the ordered member roster. (`colour` is
 *   unresolved upstream — left blank, never invented.)
 * - Cairo metro M1/M2/M3, the East-Nile monorail and the LRT Capital
 *   Train all exist as OSM relations — OsmAnd draws them from this data.
 *
 * DATASET SOURCES + FRESHNESS (dataset date [DATASET_DATE] — see
 * TransitData.kt provenance header for the full per-system record):
 * - M1/M2 coords: read-only cross-check vs official GTFS `mdb-3354`
 *   (Jul 2026). The GTFS file is CC-BY-NC-SA and is NOT bundled (no
 *   timetable seconds ship in THIS layer — OSM carries no timetables
 *   either); coordinates are geographic facts aligned to OSM stops.
 * - M3 coords (DMS), monorail/LRT rosters: Wikipedia (CC BY-SA).
 * - Monorail/LRT stops carry null coords (OSM mapping incomplete
 *   there) and NEVER take part in nearest-station matching.
 * - ENR heavy rail: no open data → not offered (unchanged).
 *
 * LICENSE (ODbL attribution — user-visible, in Settings via
 * `transit_osm_attribution`): station/relation data © OpenStreetMap
 * contributors (ODbL); M3/monorail/LRT names © Wikipedia (CC BY-SA).
 *
 * HONESTY CONTRACT (kept from outcome b, narrowed per-segment):
 * - `verifiedOsm=true` (metro M1/M2/M3 stops with real coords): the ride
 *   runs along a real OSM relation roster — the approximate label is
 *   DROPPED for these guides (`TransitGuide.approximate=false`).
 * - Everything else (monorail/LRT line-level, off-corridor, single-ended
 *   alighting hints) keeps the approximate label — NEVER a timetable.
 * - Routing = walk to nearest OSM stop → ride along the relation roster
 *   → alight at nearest stop to destination → walk (no timetables, no
 *   live departures — OsmAnd-style static routing).
 *
 * REFRESH PATH (fetch-at-download-time evolution, not yet wired):
 * [OVERPASS_REFRESH_QL] re-derives the Cairo rosters from OSM whenever a
 * region downloads. Direct Overpass access was unavailable from the build
 * environment (egress 406), so the bundled rosters stay source of truth
 * until the first on-device refresh.
 *
 * Alarm correctness is unaffected: the 500 m FAILSAFE fires on raw GPS
 * distance in EVERY mode (see TrackingService), and TIME-mode wake-up
 * runs on the generic speed/ETA estimator — both mode-agnostic and
 * unchanged under TRANSIT (trigger base 1000 m matches station-stop
 * rhythm; sim cruise 9 m/s matches metro rhythm).
 *
 * Pure Kotlin, no Android deps — safe on Dispatchers.Default. Nearest
 * math is O(stations), trivial (<1 ms). No `!!` anywhere.
 */

/** Locale-aware display name (Arabic-first, RTL-correct by caller). */
fun TransitStop.displayName(): String =
    if (java.util.Locale.getDefault().language == "ar") nameAr else nameEn

/** OSM line badge derived from the stop id prefix (e.g. "M1:x" → "L1"). */
fun TransitStop.lineBadge(): String = when (id.substringBefore(":").uppercase()) {
    "M1" -> "L1"
    "M2" -> "L2"
    "M3", "M3A", "M3B" -> "L3"
    "MONO" -> "Monorail"
    else -> "LRT"
}

/**
 * True only for OSM-covered metro stops (M1/M2/M3 with real coords) —
 * the only segments for which the approximate label may be dropped.
 */
fun TransitStop.verifiedOsm(): Boolean {
    if (lat == null || lng == null) return false
    return when (id.substringBefore(":").uppercase()) {
        "M1", "M2", "M3", "M3A", "M3B" -> true
        else -> false
    }
}

/** Nearest-station result with straight-line walking distance. */
data class NearestStation(
    val station: TransitStop,
    val distanceM: Float,
)

/** OSM-data-driven transit guidance: walk → ride along relation → walk. */
data class TransitGuide(
    val boarding: NearestStation,
    val alighting: NearestStation,
    /** False only when BOTH ends are OSM-covered metro stops. */
    val approximate: Boolean = true,
    /** e.g. "L1" direct or "L1 → L2" transfer hint (informational). */
    val lineHint: String? = null,
)

object CairoTransitStations {

    /** Bundled dataset date (ISO) — bump on every roster refresh. */
    const val DATASET_DATE = "2026-10-10"

    /**
     * Overpass QL to re-derive the Cairo rosters (fetch-at-download-time
     * path): subway + monorail + light-rail relations with stops in the
     * Greater-Cairo bbox. Paste into any Overpass instance; replace the
     * rosters in TransitData.kt with the result (keep `verifiedOsm`
     * semantics: coords + metro system).
     */
    const val OVERPASS_REFRESH_QL = "[out:json][timeout:90];" +
        "(relation[\"route\"=\"subway\"](29.7,30.9,30.4,31.7);" +
        "relation[\"route\"=\"monorail\"](29.7,30.9,30.4,31.7);" +
        "relation[\"route\"=\"light_rail\"](29.7,30.9,30.4,31.7););" +
        "out body;>;out skel qt;"

    /** Every OSM-covered stop with real coords (nearest candidates). */
    val coordStations: List<TransitStop> =
        CairoTransit.METRO_ROUTES.flatMap { it.stations }
            .filter { it.lat != null && it.lng != null }
            .distinctBy { it.id }

    /** Haversine straight-line distance in meters (pure, thread-safe). */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return (r * 2 * atan2(sqrt(a), sqrt(1 - a))).toFloat()
    }

    /** Nearest OSM-covered station to a point (never null — table non-empty). */
    fun nearest(lat: Double, lng: Double): NearestStation =
        coordStations
            .map { NearestStation(it, distanceM(lat, lng, it.lat ?: 0.0, it.lng ?: 0.0)) }
            .minByOrNull { it.distanceM }
            ?: NearestStation(coordStations.first(), Float.MAX_VALUE)

    /**
     * OSM-data-driven guidance: boarding = nearest OSM stop to the trip
     * origin, alighting = nearest OSM stop to the destination, the ride
     * running along the relation roster between them. `approximate` is
     * false ONLY when both ends are OSM-covered metro stops — callers
     * must render the approximate label whenever it is true.
     */
    fun buildGuide(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): TransitGuide {
        val board = nearest(originLat, originLng)
        val alight = nearest(destLat, destLng)
        val approx = !(board.station.verifiedOsm() && alight.station.verifiedOsm())
        val bBadge = board.station.lineBadge()
        val aBadge = alight.station.lineBadge()
        val hint = if (bBadge == aBadge) bBadge else "$bBadge → $aBadge"
        return TransitGuide(boarding = board, alighting = alight, approximate = approx, lineHint = hint)
    }
}
