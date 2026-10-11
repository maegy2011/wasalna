package com.app.wasalna.domain.model

/**
 * Vehicle type for trip routing (user orders Oct 2026: pre-trip vehicle
 * selection + Cairo public transit: generic TRANSIT first, then
 * system-specific METRO / MONORAIL / TRAIN).
 *
 * ROAD profile mapping (verified live 10 Oct 2026 against
 * router.project-osrm.org — driving/foot/bike ALL return code=Ok):
 *
 * | Vehicle    | OSRM profile | GraphHopper offline      |
 * |------------|--------------|--------------------------|
 * | CAR        | driving      | car encoder (CH, native) |
 * | MOTORCYCLE | driving¹     | car encoder (proxy)¹     |
 * | BICYCLE    | bike         | — fallback²              |
 * | WALK       | foot         | — fallback²              |
 *
 * ¹ Motorcycle has no dedicated OSRM/GH profile — rides the driving/car
 *   road network as a proxy. ² The on-device graph is baked car-only.
 *
 * TRANSIT (Oct 2026, two layers — see CairoTransitStations,
 * TransitPlanner, TransitRoutesRepository):
 * - TRANSIT⁴ (generic): OSRM `driving` REFERENCE CORRIDOR + nearest
 *   boarding/alighting from the hand-authored 12-station Cairo table,
 *   always «إرشاد تقريبي». With the user's own Routes API v2 key
 *   (Settings → BYOK) Map shows a LIVE plan instead; without it,
 *   approximate. Offline NEVER calls the API.
 * - METRO⁵ (system-specific): REAL station coords + order + interchange
 *   topology (L1/L2 official GTFS mdb-3354 Jul 2026 + L3 Wikipedia DMS)
 *   planned by [TransitPlanner]; OSRM `foot` serves the walk legs only.
 *   L1/L2 ride times are REAL GTFS schedule segment seconds + real peak
 *   headways; L3 is distance-estimated (flagged approximate).
 * - MONORAIL⁵ / TRAIN⁵ (= LRT Capital Train): published station ORDER
 *   is real, but per-station coords are unavailable (no GTFS; OSM
 *   mapping incomplete — verified Oct 2026) → line-level guidance only,
 *   ALWAYS «إرشاد تقريبي — تحقق من المواعيد», never a faked polyline.
 *   West Nile monorail (under construction, no date) is excluded;
 *   heavy-rail (ENR) has no open data → TRAIN = LRT Capital Train.
 *
 * ⁴ NEVER a timetable route (OSRM has no transit profile, GH is
 *   car-only). ⁵ Ride legs NEVER go through OSRM/GraphHopper.
 *
 * GTFS-note (Oct 2026): only station FACTS are embedded (names, coords,
 * order, scheduled segment seconds) with source attribution in
 * TransitData.kt — no feed files redistributed. L1/L2 coords+times from
 * the official feed; L3 from Wikipedia (CC BY-SA, independently
 * cross-checked); monorail/LRT order from Wikipedia.
 *
 * [simSpeedMps] is the cruise speed honored by the trip-simulation
 * controller (METRO also passes station stops so the virtual GPS dwells
 * like a real metro rhythm). Default (unknown persisted name) is CAR —
 * existing behavior unchanged.
 */
enum class TransportMode(
    val osrmProfile: String,
    val simSpeedMps: Float,
) {
    CAR("driving", 12f),
    MOTORCYCLE("driving", 14f),
    BICYCLE("bike", 4.5f),
    WALK("foot", 1.4f),
    // Generic transit (BYOK live when keyed, else 12-station approximate
    // corridor): metro average incl. dwell (~32 km/h); the simulation
    // controller honors it automatically (mode.simSpeedMps, unchanged).
    TRANSIT("driving", 9f),
    // System-specific static-data modes (TransitPlanner): cruise speeds;
    // METRO additionally dwells at real stations in simulation.
    METRO("foot", 15f),
    MONORAIL("foot", 20f),
    // LRT Capital Train (was a hidden driving-proxy alias; now the
    // visible Cairo-train choice — stored "TRAIN" values keep working).
    TRAIN("foot", 22f);

    /** Emoji shown next to the ETA in UI + notification. */
    val icon: String
        get() = when (this) {
            WALK -> "🚶"
            CAR -> "🚗"
            MOTORCYCLE -> "🏍️"
            BICYCLE -> "🚲"
            TRANSIT -> "🚇"
            METRO -> "🚇"
            MONORAIL -> "🚝"
            TRAIN -> "🚆"
        }

    /** True for station-to-station transit modes (guide, not OSRM). */
    val isTransit: Boolean
        get() = when (this) {
            TRANSIT, METRO, MONORAIL, TRAIN -> true
            else -> false
        }

    /**
     * True when the on-device GraphHopper graph can route this vehicle
     * natively (car-only bake) — else the GPS straight-line fallback
     * applies offline. Transit ride legs never touch the engine.
     */
    val offlineRoutable: Boolean
        get() = when (this) {
            CAR, MOTORCYCLE -> true
            BICYCLE, WALK, TRANSIT, METRO, MONORAIL, TRAIN -> false
        }

    companion object {
        /**
         * The ONLY four choices (user order Oct 2026): car, motorcycle,
         * public transit, walk. Retired entries (BICYCLE/METRO/MONORAIL/
         * TRAIN) stay in the enum so stored values + history keep
         * resolving — they are just never offered anymore.
         */
        val pickerEntries: List<TransportMode> =
            listOf(CAR, MOTORCYCLE, TRANSIT, WALK)

        /** The four primary choices (Map segmented row, user order). */
        val primaryEntries: List<TransportMode> =
            listOf(CAR, MOTORCYCLE, TRANSIT, WALK)

        /** Road subset (kept for compat; UI uses [primaryEntries]). */
        val roadEntries: List<TransportMode> =
            listOf(CAR, MOTORCYCLE, WALK)

        /** Transit subset (kept for compat; UI uses [primaryEntries]). */
        val transitEntries: List<TransportMode> =
            listOf(TRANSIT)

        /** Static-data systems (TransitPlanner; TRANSIT uses its own path). */
        val staticTransitEntries: List<TransportMode> =
            listOf(METRO, MONORAIL, TRAIN)

        fun fromName(name: String?): TransportMode =
            runCatching { valueOf(name ?: "") }.getOrDefault(CAR)
    }
}
