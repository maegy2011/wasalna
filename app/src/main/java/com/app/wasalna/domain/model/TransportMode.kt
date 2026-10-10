package com.app.wasalna.domain.model

/**
 * Vehicle type for trip routing (user order Oct 2026: vehicle selection
 * before trip start, routing computed accordingly).
 *
 * Profile mapping (verified live 10 Oct 2026 against
 * router.project-osrm.org — driving/foot/bike ALL return code=Ok):
 *
 * | Vehicle    | OSRM profile | GraphHopper offline      |
 * |------------|--------------|--------------------------|
 * | CAR        | driving      | car encoder (CH, native) |
 * | MOTORCYCLE | driving¹     | car encoder (proxy)¹     |
 * | BICYCLE    | bike         | — fallback²              |
 * | WALK       | foot         | — fallback²              |
 * | TRANSIT⁴   | driving⁴     | — fallback² (approx)     |
 * | TRAIN³     | driving      | car encoder (proxy)      |
 *
 * ¹ Motorcycle has no dedicated OSRM/GH profile on this stack — it rides
 *   the driving/car road network as a proxy (same precedent as TRAIN).
 * ² The on-device graph is baked car-only (`car_access`,
 *   `car_average_speed`, CH `car` profile — see GraphHopperRouteEngine):
 *   offline bicycle/walk/transit routes fall back to the GPS
 *   straight-line path (existing OfflineRouteSelector + TrackingService
 *   fallback, unchanged).
 * ³ TRAIN is a legacy alias (kept for stored trips/settings compat) and
 *   is HIDDEN from the picker ([pickerEntries]).
 * ⁴ TRANSIT (Oct 2026, outcome b APPROXIMATE — see CairoTransitStations):
 *   OSRM has no transit profile and GH is car-only, so the `driving`
 *   geometry is a REFERENCE CORRIDOR only, always shown with the
 *   «إرشاد تقريبي — تحقق من المواعيد» label plus nearest
 *   boarding/alighting stations from the hand-authored Cairo table
 *   (walk → station → ride → station → walk). NEVER a timetable route.
 *
 * [simSpeedMps] is the typical cruise speed per vehicle, honored by the
 * trip-simulation controller so virtual GPS advances realistically.
 * Default (unknown persisted name) is CAR — existing behavior unchanged.
 */
enum class TransportMode(
    val osrmProfile: String,
    val simSpeedMps: Float,
) {
    CAR("driving", 12f),
    MOTORCYCLE("driving", 14f),
    BICYCLE("bike", 4.5f),
    WALK("foot", 1.4f),
    // Metro average incl. station dwell time (~32 km/h); the simulation
    // controller honors it automatically (mode.simSpeedMps, unchanged).
    TRANSIT("driving", 9f),
    TRAIN("driving", 11f);

    /** Emoji shown next to the ETA in UI + notification. */
    val icon: String
        get() = when (this) {
            WALK -> "🚶"
            CAR -> "🚗"
            MOTORCYCLE -> "🏍️"
            BICYCLE -> "🚲"
            TRANSIT -> "🚇"
            TRAIN -> "🚆"
        }

    /**
     * True when the on-device GraphHopper graph can route this vehicle
     * natively (car-only bake) — else the GPS straight-line fallback
     * applies offline.
     */
    val offlineRoutable: Boolean
        get() = when (this) {
            CAR, MOTORCYCLE, TRAIN -> true
            BICYCLE, WALK, TRANSIT -> false
        }

    companion object {
        /** Vehicles offered in the pre-trip picker (TRAIN hidden). */
        val pickerEntries: List<TransportMode> =
            listOf(CAR, MOTORCYCLE, BICYCLE, WALK, TRANSIT)

        fun fromName(name: String?): TransportMode =
            runCatching { valueOf(name ?: "") }.getOrDefault(CAR)
    }
}
