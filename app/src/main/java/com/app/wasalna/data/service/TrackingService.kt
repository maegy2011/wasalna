package com.app.wasalna.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Phase 5: background location engine (foreground service, type `location`).
 *
 * - Started with [ACTION_START] + [EXTRA_LAT]/[EXTRA_LNG] doubles (the alarm target).
 * - Shows a low-importance ongoing notification ("Alarm is active... Sleep tight!").
 * - Polls FusedLocationProviderClient with adaptive intervals:
 *   >10_000 m → 3 min, >5_000 m → 1 min, else 10 s (re-requested dynamically).
 * - When distance <= [TRIGGER_RADIUS_METERS]: logs DESTINATION_REACHED,
 *   sends package-scoped [ACTION_DESTINATION_REACHED] broadcast, stops itself.
 *
 * POST_NOTIFICATIONS note (API 33+): foreground-service notifications are posted
 * by the system even if the user denied POST_NOTIFICATIONS, so startForeground
 * still works; the channel is IMPORTANCE_LOW to stay quiet.
 */
class TrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var targetLat: Double = Double.NaN
    private var targetLng: Double = Double.NaN
    private var currentIntervalMs: Long = -1L
    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fix = result.lastLocation ?: return
            onFix(fix)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "onStartCommand: STOP from notification")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "stopForeground: ${e.message}")
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            Log.w(TAG, "onStartCommand: unknown action, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        targetLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        targetLng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        if (targetLat.isNaN() || targetLng.isNaN()) {
            Log.e(TAG, "onStartCommand: missing target extras, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val stopPending = PendingIntent.getService(
            this,
            0,
            Intent(this, TrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wasalna alarm active")
            .setContentText("Alarm is active... Sleep tight!")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(com.app.wasalna.R.string.tracking_stop),
                stopPending,
            )
            .build()

        // API 29+ requires the FGS type at startForeground time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        startTracking(force = true)
        return START_STICKY
    }

    private fun startTracking(force: Boolean = false) {
        if (isTracking && !force) return
        if (!hasLocationPermission()) {
            Log.e(TAG, "startTracking: location permission missing, stopping")
            stopSelf()
            return
        }
        isTracking = true
        requestUpdates(INITIAL_INTERVAL_MS)
    }

    private fun onFix(fix: Location) {
        val results = FloatArray(1)
        Location.distanceBetween(
            fix.latitude, fix.longitude,
            targetLat, targetLng,
            results,
        )
        val distanceM = results[0]
        Log.i(TAG, "onFix: distance=${distanceM.toInt()}m interval=${currentIntervalMs}ms")

        if (distanceM <= TRIGGER_RADIUS_METERS) {
            Log.i(TAG, "DESTINATION_REACHED lat=$targetLat lng=$targetLng")
            val prefs = com.app.wasalna.data.local.datastore.SettingsDataStore(
                applicationContext,
            )
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val silent = try {
                    prefs.isSilentAlarmEnabled.first()
                } catch (e: Exception) {
                    Log.w(TAG, "silent read failed: ${e.message}")
                    false
                }
                val premium = try {
                    prefs.isPremium.first()
                } catch (e: Exception) {
                    Log.w(TAG, "premium read failed: ${e.message}")
                    false
                }
                val alarmIntent = Intent(
                    this@TrackingService,
                    com.app.wasalna.ui.screens.alarm.AlarmActivity::class.java,
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(
                        com.app.wasalna.ui.screens.alarm.AlarmActivity.EXTRA_SILENT,
                        silent,
                    )
                    putExtra(
                        com.app.wasalna.ui.screens.alarm.AlarmActivity.EXTRA_PREMIUM,
                        premium,
                    )
                }
                try {
                    startActivity(alarmIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "AlarmActivity launch failed: ${e.message}")
                }
            }
            sendBroadcast(
                Intent(ACTION_DESTINATION_REACHED).setPackage(packageName),
            )
            stopSelf()
            return
        }

        // Adaptive interval: re-request only when the bucket changes.
        val wanted = intervalFor(distanceM)
        if (wanted != currentIntervalMs) {
            requestUpdates(wanted)
        }
    }

    private fun requestUpdates(intervalMs: Long) {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "removeLocationUpdates failed: ${e.message}")
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs,
        ).setMinUpdateIntervalMillis(intervalMs)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()
        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper(),
            )
            currentIntervalMs = intervalMs
            Log.i(TAG, "requestUpdates: interval=${intervalMs}ms")
        } catch (e: SecurityException) {
            Log.e(TAG, "requestLocationUpdates failed: ${e.message}")
            stopSelf()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Alarm tracking",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onDestroy() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy removeLocationUpdates: ${e.message}")
        }
        isTracking = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TrackingService"

        const val ACTION_START = "com.app.wasalna.action.START_TRACKING"
        const val ACTION_STOP = "com.app.wasalna.action.STOP_TRACKING"
        const val EXTRA_LAT = "targetLat"
        const val EXTRA_LNG = "targetLng"

        /** Package-scoped broadcast sent when the 2 km radius is entered. */
        const val ACTION_DESTINATION_REACHED =
            "com.app.wasalna.action.DESTINATION_REACHED"

        const val CHANNEL_ID = "wasalna_tracking"
        const val NOTIF_ID = 1001

        /** Alarm trigger radius: 2 km before the destination. */
        const val TRIGGER_RADIUS_METERS = 2000f

        private const val INITIAL_INTERVAL_MS = 10_000L

        /** Adaptive buckets: >10 km → 3 min, >5 km → 1 min, else 10 s. */
        fun intervalFor(distanceM: Float): Long = when {
            distanceM > 10_000f -> 180_000L
            distanceM > 5_000f -> 60_000L
            else -> 10_000L
        }
    }
}
