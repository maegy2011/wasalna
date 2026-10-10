package com.app.wasalna.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TRANSIT honesty pack — outcome **(b) APPROXIMATE** (Oct 2026).
 *
 * Why NOT real station-to-station routing (option a was rejected):
 * - OSRM public server exposes only road profiles (driving/foot/bike) —
 *   there is NO transit/timetable profile to query.
 * - The on-device GraphHopper graph is baked car-only (`car_access`,
 *   `car_average_speed`, CH `car` profile) — it cannot route transit.
 * - Web check (10 Oct 2026): the only usable Cairo dataset is
 *   Transport-for-Cairo `Metro-GTFS` (github.com/transportforcairo/
 *   Metro-GTFS): 3 metro lines BUT (1) licensed **CC BY-NC 4.0** —
 *   incompatible with bundling into an app with a premium/paywall
 *   tier; (2) 2016 snapshot whose README states *"all schedules and
 *   dates are estimations"*; (3) metro-only — no monorail, no ENR
 *   trains. Bundling it would fake freshness AND breach the license.
 *
 * What ships instead: a hand-authored table of major Cairo interchange /
 * terminus stations (station names + coordinates are public geographic
 * facts; coordinates cross-checked read-only against the TfC GTFS
 * `stops.txt` — the dataset itself is NOT bundled). Guidance built on
 * this table is nearest-station + walking-leg APPROXIMATION, always
 * shown with the «إرشاد تقريبي — تحقق من المواعيد» label — NEVER
 * presented as a real timetable route.
 *
 * Alarm correctness is unaffected: the 500 m FAILSAFE fires on raw GPS
 * distance to the destination in EVERY mode (see TrackingService), and
 * TIME-mode wake-up runs on the generic speed/ETA estimator — both are
 * mode-agnostic and work unchanged under TRANSIT (trigger base 1000 m
 * matches station-stop rhythm).
 */
data class TransitStation(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    /** e.g. "L1/L2" — interchange hint shown nowhere user-facing alone. */
    val lines: String,
    val lat: Double,
    val lng: Double,
) {
    /** Locale-aware display name (Arabic-first, RTL-correct by caller). */
    fun displayName(): String =
        if (java.util.Locale.getDefault().language == "ar") nameAr else nameEn
}

/** Nearest-station result with straight-line walking distance. */
data class NearestStation(
    val station: TransitStation,
    val distanceM: Float,
)

/** Approximate transit guidance: walk → station → ride → station → walk. */
data class TransitGuide(
    val boarding: NearestStation,
    val alighting: NearestStation,
)

object CairoTransitStations {

    /**
     * Major Cairo metro interchanges + line termini (12). Coordinates are
     * public facts cross-checked against TfC Metro-GTFS stops.txt
     * (read-only, Oct 2026). Interchanges listed first so ties prefer a
     * station with more connections.
     */
    val stations: List<TransitStation> = listOf(
        TransitStation("sadat", "Sadat (Tahrir)", "السادات (التحرير)", "L1/L2", 30.043924, 31.235660),
        TransitStation("shohadaa", "Al-Shohadaa (Ramses)", "الشهداء (رمسيس)", "L1/L2", 30.061640, 31.246287),
        TransitStation("attaba", "Attaba", "العتبة", "L2/L3", 30.052352, 31.246796),
        TransitStation("nasser", "Nasser", "ناصر", "L1/L3", 30.053457, 31.238648),
        TransitStation("cairo-uni", "Cairo University", "جامعة القاهرة", "L2", 30.026151, 31.200872),
        TransitStation("dokki", "Dokki", "الدقي", "L2", 30.038467, 31.212243),
        TransitStation("naguib", "Mohamed Naguib", "محمد نجيب", "L2", 30.045340, 31.244152),
        TransitStation("abbassiya", "Abbassiya", "العباسية", "L3", 30.069763, 31.280780),
        TransitStation("helwan", "Helwan", "حلوان", "L1", 29.848824, 31.334252),
        TransitStation("marg", "New El-Marg", "المرج الجديدة", "L1", 30.163551, 31.338335),
        TransitStation("mounib", "El-Mounib", "المنيب", "L2", 29.981033, 31.212308),
        TransitStation("shubra", "Shubra El-Kheima", "شبرا الخيمة", "L2", 30.122515, 31.244688),
    )

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

    /** Nearest table station to a point (never null — table non-empty). */
    fun nearest(lat: Double, lng: Double): NearestStation =
        stations
            .map { NearestStation(it, distanceM(lat, lng, it.lat, it.lng)) }
            .minByOrNull { it.distanceM }
            ?: NearestStation(stations.first(), Float.MAX_VALUE)

    /**
     * APPROXIMATE guidance for a trip: boarding = nearest station to the
     * trip origin, alighting = nearest station to the destination, with
     * straight-line walking legs. Callers must render the approximate
     * label alongside — this is NOT a timetable route.
     */
    fun buildGuide(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): TransitGuide = TransitGuide(
        boarding = nearest(originLat, originLng),
        alighting = nearest(destLat, destLng),
    )
}
