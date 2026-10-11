package com.app.wasalna.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Background-survival watchdog (Oct 2026 fix).
 *
 * - [ACTION_WATCHDOG]: fires every ~15 min while a trip is saved. If the
 *   tracking process died (OEM kill, crash, swipe-away), the saved trip
 *   restarts via the normal ACTION_START flow; if the service is alive it
 *   simply re-chains the next check. A finished/cancelled trip clears the
 *   save, so this becomes a no-op.
 * - BOOT_COMPLETED: a reboot mid-trip resumes tracking instead of silently
 *   dropping it (stale saves >12h are rejected in resumePersistedTrip).
 */
class TripWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_WATCHDOG && action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        if (startedAt == 0L) return
        if (System.currentTimeMillis() - startedAt > TRIP_MAX_AGE_MS) {
            runCatching { prefs.edit().clear().apply() }
            return
        }
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull()
        val lng = prefs.getString(KEY_LNG, null)?.toDoubleOrNull()
        if (lat == null || lng == null) return
        Log.i(TAG, "watchdog ($action): restarting saved trip")
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .setAction(TrackingService.ACTION_START)
                    .putExtra(TrackingService.EXTRA_LAT, lat)
                    .putExtra(TrackingService.EXTRA_LNG, lng)
                    .putExtra(TrackingService.EXTRA_NAME, prefs.getString(KEY_NAME, null))
                    .putExtra(TrackingService.EXTRA_MODE, prefs.getString(KEY_MODE, null)),
            )
        }.onFailure { Log.w(TAG, "watchdog restart failed: ${it.message}") }
    }

    companion object {
        const val ACTION_WATCHDOG = "com.app.wasalna.action.TRIP_WATCHDOG"

        private const val TAG = "TripWatchdog"
        private const val PREFS_NAME = "wasalna_active_trip"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_NAME = "name"
        private const val KEY_MODE = "mode"
        private const val TRIP_MAX_AGE_MS = 12L * 60L * 60L * 1000L
    }
}
