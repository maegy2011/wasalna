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
import com.app.wasalna.R
import com.app.wasalna.data.local.datastore.SettingsDataStore
import com.app.wasalna.data.repository.OSRMRepository
import com.app.wasalna.domain.model.TransportMode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Phase 5: background location engine (foreground service, type `location`).
 * Phase 10.4.2: rich live notification + action buttons.
 *
 * - Started with [ACTION_START] + [EXTRA_LAT]/[EXTRA_LNG] (+ optional
 *   [EXTRA_NAME]/[EXTRA_MODE]); shows a brand-colored ongoing notification.
 * - Every GPS fix refreshes: "Heading to: X" + "Dist | Speed | ETA + icon".
 * - Actions (no UI needed): Stop, Pause/Resume, Silent/Ring toggle.
 * - Content tap opens the app (change destination from My Places).
 * - Pause ignores GPS; silent toggle flips the DataStore preference.
 * - Polls FusedLocationProviderClient with adaptive intervals:
 *   >10_000 m → 3 min, >5_000 m → 1 min, else 10 s (re-requested dynamically).
 * - When distance <= [TRIGGER_RADIUS_METERS]: DESTINATION_REACHED broadcast
 *   + alarm activity, then stops itself.
 *
 * POST_NOTIFICATIONS note (API 33+): foreground-service notifications are posted
 * by the system even if the user denied POST_NOTIFICATIONS, so startForeground
 * still works; the channel is IMPORTANCE_LOW to stay quiet.
 * Android 14+: manifest declares FOREGROUND_SERVICE_LOCATION + matching
 * foregroundServiceType, and startForeground runs synchronously in
 * onStartCommand — no crash path.
 */
class TrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val osrm = OSRMRepository()

    private var targetLat: Double = Double.NaN
    private var targetLng: Double = Double.NaN
    private var targetName: String = "Destination"
    private var mode: TransportMode = TransportMode.CAR
    private var isPaused: Boolean = false
    private var currentIntervalMs: Long = -1L
    private var isTracking = false

    // Live stats for the notification body.
    private var lastSpeedKmh: Double = 0.0
    private var lastEtaMin: Int? = null
    private var lastOsrmFetchMs: Long = 0L
    private var isSilent: Boolean = false

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
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "onStartCommand: STOP from notification")
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_RESUME -> {
                isPaused = !isPaused
                Log.i(TAG, "onStartCommand: paused=$isPaused")
                if (isPaused) {
                    runCatching { fusedClient.removeLocationUpdates(locationCallback) }
                    isTracking = false
                } else {
                    startTracking(force = true)
                }
                pushNotification(null)
                return START_STICKY
            }
            ACTION_TOGGLE_SILENT -> {
                serviceScope.launch {
                    val prefs = SettingsDataStore(applicationContext)
                    val current = runCatching { prefs.isSilentAlarmEnabled.first() }
                        .getOrDefault(false)
                    runCatching { prefs.setSilentAlarmEnabled(!current) }
                    isSilent = !current
                    pushNotification(null)
                }
                return START_STICKY
            }
            ACTION_START -> { /* handled below */ }
            else -> {
                Log.w(TAG, "onStartCommand: unknown action, stopping")
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        targetLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        targetLng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        if (targetLat.isNaN() || targetLng.isNaN()) {
            Log.e(TAG, "onStartCommand: missing target extras, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        targetName = intent.getStringExtra(EXTRA_NAME)?.ifBlank { null } ?: "Destination"
        mode = TransportMode.fromName(intent.getStringExtra(EXTRA_MODE))
        isPaused = false

        // Read the current silent flag once so the action label is correct.
        serviceScope.launch {
            isSilent = runCatching {
                SettingsDataStore(applicationContext).isSilentAlarmEnabled.first()
            }.getOrDefault(false)
            pushNotification(null)
        }

        // API 29+ requires the FGS type at startForeground time.
        val notification = buildNotification(null).build()
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
        if (isPaused) return
        val target = Location("wasalna-target").apply {
            latitude = targetLat
            longitude = targetLng
        }
        val distanceM = fix.distanceTo(target)
        lastSpeedKmh = if (fix.hasSpeed() && fix.speed >= 0f) fix.speed * 3.6 else 0.0
        Log.i(TAG, "onFix: distance=${distanceM.toInt()}m speed=${"%.1f".format(lastSpeedKmh)}km/h")

        if (distanceM <= TRIGGER_RADIUS_METERS) {
            Log.i(TAG, "DESTINATION_REACHED lat=$targetLat lng=$targetLng")
            val prefs = SettingsDataStore(applicationContext)
            serviceScope.launch {
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

        // ETA: live local estimate while moving; OSRM refresh at most every
        // 2 minutes when standing still (avoids spamming the API).
        if (lastSpeedKmh > MIN_MOVING_KMH) {
            val mins = (distanceM / 1000.0) / lastSpeedKmh * 60.0
            lastEtaMin = mins.toInt().coerceAtLeast(1)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastOsrmFetchMs >= OSRM_MIN_INTERVAL_MS) {
                lastOsrmFetchMs = now
                serviceScope.launch {
                    val route = runCatching {
                        osrm.fetchRoute(
                            fix.latitude, fix.longitude,
                            targetLat, targetLng,
                            mode.osrmProfile,
                        )
                    }.getOrNull()
                    route?.durationSec?.takeIf { it >= 0 }?.let {
                        lastEtaMin = (it / 60.0).toInt().coerceAtLeast(1)
                    }
                    pushNotification(fix)
                }
            }
        }
        pushNotification(fix)

        // Adaptive interval: re-request only when the bucket changes.
        val wanted = intervalFor(distanceM)
        if (wanted != currentIntervalMs) {
            requestUpdates(wanted)
        }
    }

    /** Builds the rich notification; null fix = static (start/pause/silent). */
    private fun buildNotification(fix: Location?): NotificationCompat.Builder {
        val title = if (isPaused) "Paused — $targetName" else "Heading to: $targetName"
        val body = if (isPaused) {
            "Trip paused — tap Resume to continue"
        } else if (fix != null) {
            val distKm = fix.distanceTo(
                Location("wasalna-target").apply {
                    latitude = targetLat
                    longitude = targetLng
                },
            ) / 1000.0
            val eta = lastEtaMin?.let { "~$it mins ${mode.icon}" } ?: "calculating…"
            "Dist: ${"%.1f".format(distKm)} km | " +
                "Speed: ${"%.0f".format(lastSpeedKmh)} km/h | ETA: $eta"
        } else {
            "Alarm is active... Sleep tight!"
        }

        // Content tap opens the app (change destination from My Places).
        val contentIntent = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            Intent(this, com.app.wasalna.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setColor(ContextCompat.getColor(this, R.color.brand_primary))
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(if (isPaused) R.string.tracking_resume else R.string.tracking_pause),
                servicePending(ACTION_PAUSE_RESUME, REQ_PAUSE),
            )
            .addAction(
                if (isSilent) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode,
                getString(if (isSilent) R.string.tracking_ring else R.string.tracking_silent),
                servicePending(ACTION_TOGGLE_SILENT, REQ_SILENT),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.tracking_stop),
                servicePending(ACTION_STOP, REQ_STOP),
            )
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TrackingService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Silent in-place update — LOW channel + onlyAlertOnce: no buzz. */
    private fun pushNotification(fix: Location?) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIF_ID, buildNotification(fix).build())
        }.onFailure {
            Log.w(TAG, "pushNotification failed: ${it.message}")
        }
    }

    private fun stopForegroundAndSelf() {
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
                ).apply {
                    // Phase 10.4.2: tracking updates must never buzz.
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    override fun onDestroy() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy removeLocationUpdates: ${e.message}")
        }
        serviceScope.cancel()
        isTracking = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TrackingService"

        const val ACTION_START = "com.app.wasalna.action.START_TRACKING"
        const val ACTION_STOP = "com.app.wasalna.action.STOP_TRACKING"

        /** Phase 10.4.2: pause/resume + silent toggle (handled in-service). */
        const val ACTION_PAUSE_RESUME = "com.app.wasalna.action.PAUSE_RESUME"
        const val ACTION_TOGGLE_SILENT = "com.app.wasalna.action.TOGGLE_SILENT"
        const val EXTRA_LAT = "targetLat"
        const val EXTRA_LNG = "targetLng"

        /** Phase 10.4.2: destination name + mode name for the rich header. */
        const val EXTRA_NAME = "targetName"
        const val EXTRA_MODE = "transportMode"

        /** Package-scoped broadcast sent when the 2 km radius is entered. */
        const val ACTION_DESTINATION_REACHED =
            "com.app.wasalna.action.DESTINATION_REACHED"

        const val CHANNEL_ID = "wasalna_tracking"
        const val NOTIF_ID = 1001

        /** Alarm trigger radius: 2 km before the destination. */
        const val TRIGGER_RADIUS_METERS = 2000f

        private const val INITIAL_INTERVAL_MS = 10_000L

        /** Local ETA only counts when actually moving (>5 km/h). */
        private const val MIN_MOVING_KMH = 5.0

        /** OSRM ETA refresh floor while stationary (avoids API spam). */
        private const val OSRM_MIN_INTERVAL_MS = 120_000L

        private const val REQ_CONTENT = 10
        private const val REQ_PAUSE = 11
        private const val REQ_SILENT = 12
        private const val REQ_STOP = 13

        /** Adaptive buckets: >10 km → 3 min, >5 km → 1 min, else 10 s. */
        fun intervalFor(distanceM: Float): Long = when {
            distanceM > 10_000f -> 180_000L
            distanceM > 5_000f -> 60_000L
            else -> 10_000L
        }
    }
}
