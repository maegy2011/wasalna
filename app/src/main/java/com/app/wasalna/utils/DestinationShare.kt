package com.app.wasalna.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Phase 10.11: dynamic destination share — "مشاركة الوجهة".
 *
 * Builds the share-target list at RUNTIME from what is ACTUALLY installed
 * (PackageManager), never assuming presence. Re-queried on every sheet
 * open, so installs/uninstalls are reflected immediately.
 *
 * Targets:
 * - Default/system maps: geo: Intent (resolves to the user's default maps
 *   app) + Google Maps / OsmAnd / MAPS.ME entries IF installed.
 * - Ride-hailing: Uber, DiDi, inDrive — package candidates verified at
 *   runtime; deep-link where a public scheme exists, else the app's main
 *   activity with coords in extras + an Arabic note.
 *
 * Rules: no `!!`, no crash when nothing is installed, Dispatchers for I/O
 * at the call site (query does binder calls — call from Dispatchers.IO).
 */
object DestinationShare {

    /** One share row in the bottom sheet. */
    data class Target(
        val id: String,
        val arabicLabel: String,
        val packageName: String?,
        val hint: String,
    )

    // ── Package candidates (verified at runtime, first installed wins) ──

    private val GoogleMapsPkgs = listOf("com.google.android.apps.maps")
    private val OsmAndPkgs = listOf("net.osmand.plus", "net.osmand")
    private val MapsMePkgs = listOf("com.mapswithme.maps")
    private val UberPkgs = listOf("com.ubercab")
    private val DidiPkgs = listOf(
        "com.didiglobal.passenger",
        "com.didi.global",
        "com.didapassenger",
    )
    private val InDrivePkgs = listOf(
        "com.indriver.passenger",
        "sinet.startup.inDriver",
        "com.inDriver",
    )

    private const val TAG = "DestinationShare"

    /** True if the package is installed (any version). Never throws. */
    fun isInstalled(pm: PackageManager, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

    /** First installed candidate, or null. Never throws. */
    private fun firstInstalled(pm: PackageManager, candidates: List<String>): String? =
        candidates.firstOrNull { isInstalled(pm, it) }

    /** True if SOME app resolves the geo: view intent. Never throws. */
    fun isGeoAvailable(pm: PackageManager, lat: Double, lng: Double, label: String): Boolean =
        runCatching {
            geoIntent(lat, lng, label).resolveActivity(pm) != null
        }.getOrDefault(false)

    /**
     * Dynamic detection matrix — call on Dispatchers.IO, re-run on EVERY
     * sheet open. Returns only installed targets. May be empty (caller
     * shows the Arabic empty state).
     */
    fun queryTargets(
        context: Context,
        lat: Double,
        lng: Double,
        label: String,
    ): List<Target> {
        val pm = context.packageManager
        val out = mutableListOf<Target>()
        if (isGeoAvailable(pm, lat, lng, label)) {
            out += Target(
                id = "geo_default",
                arabicLabel = "🗺️ خرائط النظام (الافتراضية)",
                packageName = null,
                hint = "يفتح تطبيق الخرائط الافتراضي",
            )
        }
        firstInstalled(pm, GoogleMapsPkgs)?.let { pkg ->
            out += Target("gmaps", "🗺️ خرائط جوجل", pkg, "ملاحة مباشرة إلى الوجهة")
        }
        firstInstalled(pm, OsmAndPkgs)?.let { pkg ->
            out += Target("osmand", "🧭 OsmAnd", pkg, "ملاحة أوفلاين إلى الوجهة")
        }
        firstInstalled(pm, MapsMePkgs)?.let { pkg ->
            out += Target("mapsme", "🧭 MAPS.ME", pkg, "ملاحة أوفلاين إلى الوجهة")
        }
        firstInstalled(pm, UberPkgs)?.let { pkg ->
            out += Target("uber", "🚕 أوبر (Uber)", pkg, "طلب مشوار إلى الوجهة")
        }
        firstInstalled(pm, DidiPkgs)?.let { pkg ->
            out += Target("didi", "🚕 دي دي (DiDi)", pkg, "طلب مشوار إلى الوجهة")
        }
        firstInstalled(pm, InDrivePkgs)?.let { pkg ->
            out += Target("indrive", "🚕 إن درايف (inDrive)", pkg, "طلب مشوار إلى الوجهة")
        }
        return out
    }

    /**
     * Launches [target] with the destination. Returns true on success,
     * false when the app vanished / has no entry point (caller shows an
     * Arabic snackbar). Never throws, no `!!`.
     */
    fun launch(context: Context, target: Target, lat: Double, lng: Double, label: String): Boolean =
        runCatching {
            val intent = when (target.id) {
                "geo_default" -> geoIntent(lat, lng, label)
                "gmaps" -> runCatching {
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("google.navigation:q=$lat,$lng"),
                    ).apply { target.packageName?.let { setPackage(it) } }
                        .takeIf { it.resolveActivity(context.packageManager) != null }
                }.getOrNull() ?: geoIntent(lat, lng, label).apply {
                    target.packageName?.let { setPackage(it) }
                }
                "osmand", "mapsme" -> geoIntent(lat, lng, label).apply {
                    target.packageName?.let { setPackage(it) }
                }
                "uber" -> uberIntent(target.packageName, lat, lng, label)
                    ?: mainActivityFallback(context, target, lat, lng, label)
                    ?: return false
                "didi", "indrive" ->
                    mainActivityFallback(context, target, lat, lng, label)
                        ?: return false
                else -> return false
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) return false
            context.startActivity(intent)
            true
        }.onFailure {
            Log.w(TAG, "launch ${target.id} failed: ${it.message}")
        }.getOrDefault(false)

    // ── Intent builders ──

    private fun geoIntent(lat: Double, lng: Double, label: String): Intent {
        val encoded = Uri.encode(label.ifBlank { "$lat,$lng" })
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat,$lng?q=$lat,$lng($encoded)"),
        )
    }

    /**
     * Uber: public m.uber.com deep link (handled by the Uber app via App
     * Links when installed). Package-pinned when verified installed.
     */
    private fun uberIntent(pkg: String?, lat: Double, lng: Double, label: String): Intent? {
        val encoded = Uri.encode(label.ifBlank { "Wasalna destination" })
        return runCatching {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://m.uber.com/go/home" +
                        "?dropoff[latitude]=$lat&dropoff[longitude]=$lng" +
                        "&dropoff[nickname]=$encoded",
                ),
            ).apply { pkg?.let { setPackage(it) } }
        }.getOrNull()
    }

    /**
     * DiDi / inDrive: no stable public ride-request scheme — open the
     * app's main (launcher) activity with the destination in extras plus
     * an Arabic note, so the user picks the drop-off in one tap.
     * Null when the app has no launchable entry point.
     */
    private fun mainActivityFallback(
        context: Context,
        target: Target,
        lat: Double,
        lng: Double,
        label: String,
    ): Intent? {
        val pkg = target.packageName ?: return null
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                putExtra("dest_lat", lat)
                putExtra("dest_lng", lng)
                putExtra("dest_label", label.ifBlank { "وجهة وصلنا" })
                putExtra(
                    "note",
                    "الوجهة من تطبيق وصلنا: $label ($lat، $lng) — الصقها كنقطة وصول",
                )
            }
        }.getOrNull()
    }
}
