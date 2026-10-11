package com.app.wasalna.data.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.app.wasalna.R
import com.app.wasalna.data.simulation.TripSimulationController
import com.app.wasalna.domain.model.TransportMode

/**
 * Single funnel for starting trips (Oct 2026: real-trip upgrade + sim
 * separation).
 *
 * REAL trips ([startRealTrip]) run a pre-flight gate BEFORE the service
 * starts — the old call sites fired ACTION_START blindly, so a trip with
 * GPS off / no permission / battery-optimized died silently seconds
 * later. Every blocker shows an Arabic Toast AND opens the exact system
 * screen that fixes it (location, background-location, battery).
 *
 * SEPARATION: a real trip refuses while a simulation runs and a
 * simulation refuses while a real trip runs — the two modes can never
 * mix, share a notification, or corrupt each other's state. Simulated
 * trips are also excluded from persistence/watchdog/auto-graph inside
 * TrackingService, so a dead sim can never resurrect itself.
 *
 * Never throws — false + Toast on any refusal. No `!!`.
 */
object TripStarter {

    private const val TAG = "TripStarter"

    /** Pre-flight for a REAL trip: null = clear to start. */
    fun checkRealReady(context: Context): RealBlocker? {
        if (TripSimulationController.isActive) return RealBlocker.SimRunning
        if (TrackingService.isServiceRunning) return RealBlocker.AlreadyRunning
        if (!hasFineLocation(context)) return RealBlocker.NoLocation
        if (!isGpsOn(context)) return RealBlocker.GpsOff
        if (!hasBackgroundLocation(context)) return RealBlocker.NoBackground
        if (!isBatteryIgnored(context)) return RealBlocker.BatteryOptimized
        return null
    }

    /**
     * Starts a real trip after the gate. Fixable blockers open their
     * system screen; hard conflicts (sim/running) only Toast.
     */
    fun startRealTrip(
        context: Context,
        lat: Double,
        lng: Double,
        name: String,
        mode: TransportMode,
    ): Boolean {
        return when (val block = checkRealReady(context)) {
            null -> {
                fireStart(context, lat, lng, name, mode)
                Toast.makeText(
                    context,
                    context.getString(R.string.trip_started, name),
                    Toast.LENGTH_SHORT,
                ).show()
                true
            }
            RealBlocker.SimRunning -> {
                refuse(context, R.string.trip_blocked_by_sim)
                false
            }
            RealBlocker.AlreadyRunning -> {
                refuse(context, R.string.trip_already_running)
                false
            }
            RealBlocker.NoLocation -> {
                refuse(context, R.string.trip_need_location)
                openAppSettings(context)
                false
            }
            RealBlocker.GpsOff -> {
                refuse(context, R.string.trip_need_gps)
                openLocationSettings(context)
                false
            }
            RealBlocker.NoBackground -> {
                refuse(context, R.string.trip_need_bg)
                openAppSettings(context)
                false
            }
            RealBlocker.BatteryOptimized -> {
                refuse(context, R.string.trip_need_battery)
                openBatterySettings(context)
                false
            }
        }
    }

    /**
     * Simulation entry guard: refuses while a REAL trip runs (the sim
     * panel calls this before [TripSimulationController.start]).
     */
    fun checkSimReady(context: Context): Boolean {
        if (TrackingService.isServiceRunning && !TripSimulationController.isActive) {
            refuse(context, R.string.sim_blocked_by_trip)
            return false
        }
        if (!hasFineLocation(context) || !isGpsOn(context)) {
            // Sim needs an origin fix; without GPS it has no start point.
            refuse(context, R.string.sim_no_fix)
            return false
        }
        return true
    }

    private fun fireStart(
        context: Context,
        lat: Double,
        lng: Double,
        name: String,
        mode: TransportMode,
    ) {
        runCatching {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
                putExtra(TrackingService.EXTRA_LAT, lat)
                putExtra(TrackingService.EXTRA_LNG, lng)
                putExtra(TrackingService.EXTRA_NAME, name)
                putExtra(TrackingService.EXTRA_MODE, mode.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { Log.w(TAG, "fireStart failed: ${it.message}") }
    }

    private fun refuse(context: Context, msgRes: Int) {
        runCatching {
            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openLocationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { openAppSettings(context) }
    }

    private fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isGpsOn(context: Context): Boolean = runCatching {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    private fun isBatteryIgnored(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(true)

    enum class RealBlocker {
        SimRunning,
        AlreadyRunning,
        NoLocation,
        GpsOff,
        NoBackground,
        BatteryOptimized,
    }
}
