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
 * | TRAIN³     | driving      | car encoder (proxy)      |
 *
 * ¹ Motorcycle has no dedicated OSRM/GH profile on this stack — it rides
 *   the driving/car road network as a proxy (same precedent as TRAIN).
 * ² The on-device graph is baked car-only (`car_access`,
 *   `car_average_speed`, CH `car` profile — see GraphHopperRouteEngine):
 *   offline bicycle/walk routes fall back to the GPS straight-line path
 *   (existing OfflineRouteSelector + TrackingService fallback, unchanged).
 * ³ TRAIN is a legacy alias (kept for stored trips/settings compat) and
 *   is HIDDEN from the picker ([pickerEntries]).
 *
 * Bus/metro are deliberately NOT offered: OSRM has no transit timetable
 * profile and the offline graph is car-only — offering them would fake
 * routing the engines cannot compute.
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
    TRAIN("driving", 11f);

    /** Emoji shown next to the ETA in UI + notification. */
    val icon: String
        get() = when (this) {
            WALK -> "🚶"
            CAR -> "🚗"
            MOTORCYCLE -> "🏍️"
            BICYCLE -> "🚲"
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
            BICYCLE, WALK -> false
        }

    companion object {
        /** Vehicles offered in the pre-trip picker (TRAIN hidden). */
        val pickerEntries: List<TransportMode> =
            listOf(CAR, MOTORCYCLE, BICYCLE, WALK)

        fun fromName(name: String?): TransportMode =
            runCatching { valueOf(name ?: "") }.getOrDefault(CAR)
    }
}
