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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.app.wasalna.R
import com.app.wasalna.data.local.datastore.SettingsDataStore
import com.app.wasalna.data.repository.OSRMRepository
import com.app.wasalna.di.TripRepositoryEntryPoint
import com.app.wasalna.domain.model.AlarmMode
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.repository.TripRepository
import dagger.hilt.android.EntryPointAccessors
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
    // Phase 10.4.3.9: on-device routing fallback (plain Service — manual
    // construction like osrm above; Hilt singletons serve the UI layer).
    private val graphManager by lazy {
        com.app.wasalna.data.routing.RoutingGraphManager(applicationContext)
    }
    private val ghEngine by lazy {
        com.app.wasalna.data.routing.GraphHopperRouteEngine(applicationContext)
    }

    // Phase 10.4.2.1: trip-history logging via Hilt entry point
    // (plain Service cannot use @Inject).
    private val tripRepo: TripRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            TripRepositoryEntryPoint::class.java,
        ).tripRepository()
    }
    private var activeTripId: Long? = null

    // Phase 10.4.2.1: Partial WakeLock keeps the CPU awake while tracking
    // so fixes keep flowing with the screen off. Acquired once per trip
    // (12h safety timeout), released in onDestroy — never leaked.
    private var wakeLock: PowerManager.WakeLock? = null

    private var targetLat: Double = Double.NaN
    private var targetLng: Double = Double.NaN
    private var targetName: String = "Destination"
    private var mode: TransportMode = TransportMode.CAR
    private var isPaused: Boolean = false
    // Phase 10.4.3.3: adaptive trigger (loaded once per trip via .first()).
    private var adaptiveEnabled: Boolean = true
    // DYNAMIC WAKE-UP (التنبيه المرتبط بالزحام): DISTANCE (legacy default)
    // vs TIME (minutes-before-arrival). Cached at trip START, re-read live
    // on every fix inside evaluateTimeModeAlarm so mid-trip switches apply.
    private var alarmModeName: String = AlarmMode.DISTANCE.name
    private var alarmTimeMinutes: Int = DEFAULT_ALARM_TIME_MINUTES
    private var hasFiredAlarm: Boolean = false
    private val wakeupEstimator = DynamicWakeupEstimator()
    private var routeRemainingM: Float? = null
    private var routeRemainingAtMs: Long = 0L
    // Phase 10.4.3.4: one-shot heads-up per trip (reset on every START).
    private var hasFiredProactiveAlert: Boolean = false
    private var proactiveEnabled: Boolean = true
    private var currentIntervalMs: Long = -1L
    private var isTracking = false

    // Live stats for the notification body.
    private var lastSpeedKmh: Double = 0.0
    private var lastEtaMin: Int? = null
    private var lastOsrmFetchMs: Long = 0L
    // Phase 10.4.3.7: bulletproof offline — probe_net at most once a minute
    // while down; every tick in between runs on pure GPS distance.
    private var isOfflineMode: Boolean = false
    private var lastOsrmFailureAt: Long = 0L
    private var isSilent: Boolean = false
    // True offline mode (GPS-chip only): user-facing flag persisted in
    // SettingsDataStore (same key the header cloud button writes). Read at
    // trip START and re-read live on every stationary fix so mid-trip
    // toggles apply immediately (see OFFLINE BOUNDARY in onFix).
    private var forceOffline: Boolean = false
    private var offlineFallbackNotice: String? = null

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
        acquireWakeLock()
        // Phase 10.4.3.3/10.4.3.4: one-shot reads (never collect{} in a service).
        hasFiredProactiveAlert = false
        hasFiredAlarm = false
        wakeupEstimator.reset()
        routeRemainingM = null
        serviceScope.launch {
            val prefs = SettingsDataStore(applicationContext)
            alarmModeName = runCatching {
                prefs.alarmMode.first()
            }.getOrDefault(AlarmMode.DISTANCE.name)
            alarmTimeMinutes = runCatching {
                prefs.alarmTimeMinutes.first()
            }.getOrDefault(DEFAULT_ALARM_TIME_MINUTES)
            adaptiveEnabled = runCatching {
                prefs.isAdaptiveAlarmEnabled.first()
            }.getOrDefault(true)
            proactiveEnabled = runCatching {
                prefs.isProactiveAlertEnabled.first()
            }.getOrDefault(true)
            forceOffline = runCatching {
                prefs.isForceOffline.first()
            }.getOrDefault(false)
            if (forceOffline) {
                isOfflineMode = true
                Log.i(TAG, "Offline mode ON — OSRM bypassed, GPS + local engine only")
            }
        }
        // Phase 10.4.2.1: open the trip row (closes any stale ACTIVE one).
        serviceScope.launch {
            activeTripId = runCatching {
                tripRepo.startTrip(targetName, targetLat, targetLng, mode)
            }.onFailure {
                Log.w(TAG, "startTrip log failed: ${it.message}")
            }.getOrNull()
        }

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
        // Phase 10.4.3.7 (Part 2): breadcrumb per valid fix — junk fixes
        // (>100 m accuracy) never touch the DB.
        val tripId = activeTripId
        if (tripId != null && !(fix.hasAccuracy() && fix.accuracy > 100f)) {
            serviceScope.launch {
                runCatching {
                    tripRepo.recordRoutePoint(tripId, fix.latitude, fix.longitude)
                }.onFailure {
                    Log.w(TAG, "breadcrumb failed: ${it.message}")
                }
            }
        }
        val target = Location("wasalna-target").apply {
            latitude = targetLat
            longitude = targetLng
        }
        val distanceM = fix.distanceTo(target)
        lastSpeedKmh = if (fix.hasSpeed() && fix.speed >= 0f) fix.speed * 3.6 else 0.0
        val triggerM = triggerRadiusMeters(fix)
        Log.i(TAG, "onFix: distance=${distanceM.toInt()}m trigger=${triggerM.toInt()}m " +
            "speed=${"%.1f".format(lastSpeedKmh)}km/h mode=$mode")

        // FAILSAFE 🛡️ (بأمر محمد — صمام الأمان): independent guard checked
        // with EVERY fix BEFORE any other logic (top priority). Regardless
        // of alarm mode (DISTANCE/TIME) and regardless of vehicle speed —
        // at <=500 m actual remaining distance (walking distance) the alarm
        // fires IMMEDIATELY without waiting. The stop is never overshot.
        if (!hasFiredAlarm && distanceM <= SAFETY_VALVE_METERS) {
            fireAlarm(alarmModeName)
            return
        }
        // Mid-trip switch support: refresh the cached mode on IO (never Main)
        // on every fix, so DISTANCE→TIME also takes effect by the next fix
        // (TIME→DISTANCE re-reads live inside evaluateTimeModeAlarm).
        serviceScope.launch {
            val modePrefs = SettingsDataStore(applicationContext)
            alarmModeName = runCatching { modePrefs.alarmMode.first() }
                .getOrDefault(alarmModeName)
            alarmTimeMinutes = runCatching { modePrefs.alarmTimeMinutes.first() }
                .getOrDefault(alarmTimeMinutes)
        }
        // DYNAMIC WAKE-UP: DISTANCE mode fires on the radius here; TIME mode
        // never fires on distance alone — evaluated below on Default.
        if (alarmModeName != AlarmMode.TIME.name && distanceM <= triggerM) {
            fireAlarm(AlarmMode.DISTANCE.name)
            return
        }
        if (alarmModeName == AlarmMode.TIME.name) {
            evaluateTimeModeAlarm(fix, distanceM, triggerM)
        }

        // ETA: live local estimate while moving; OSRM refresh at most every
        // 2 minutes when standing still (avoids spamming the API).
        if (lastSpeedKmh > MIN_MOVING_KMH) {
            val mins = (distanceM / 1000.0) / lastSpeedKmh * 60.0
            lastEtaMin = mins.toInt().coerceAtLeast(1)
            persistEta()
        } else {
            val now = System.currentTimeMillis()
            // ── OFFLINE BOUNDARY: user offline-mode flag re-read live
            // (DataStore, IO scope) so a mid-trip toggle applies at once —
            // when ON, osrm.fetchRoute below is never reached: GPS fixes +
            // local GraphHopper engine + straight-line fallback only.
            forceOffline = runCatching {
                SettingsDataStore(applicationContext).isForceOffline.first()
            }.getOrDefault(forceOffline)
            // Phase 10.4.3.7: while offline, silence the radio — one probe
            // per minute max, pure GPS distance on every tick meanwhile.
            val mayProbe = !forceOffline && (!isOfflineMode ||
                now - lastOsrmFailureAt >= OSRM_RETRY_MS)
            if (forceOffline) {
                // Step 4/5: OSRM bypassed — straight to the local engine.
                serviceScope.launch {
                    runLocalRoute(fix, distanceM)
                    pushNotification(fix)
                }
            } else if (mayProbe && now - lastOsrmFetchMs >= OSRM_MIN_INTERVAL_MS) {
                lastOsrmFetchMs = now
                serviceScope.launch {
                    val route = runCatching {
                        osrm.fetchRoute(
                            fix.latitude, fix.longitude,
                            targetLat, targetLng,
                            mode.osrmProfile,
                        )
                    }.getOrNull()
                    val secs = route?.durationSec?.takeIf { it >= 0 }
                    if (secs != null) {
                        // Network alive — leave offline mode behind.
                        isOfflineMode = false
                        lastEtaMin = (secs / 60.0).toInt().coerceAtLeast(1)
                        persistEta()
                    } else {
                        // OSRM dead — GPS fallback + local ETA, no spam.
                        isOfflineMode = true
                        lastOsrmFailureAt = System.currentTimeMillis()
                        Log.w(TAG, "OSRM unreachable — offline GPS mode")
                        offlineEtaFallback(fix, distanceM)
                        runLocalRoute(fix, distanceM)
                    }
                    pushNotification(fix)
                }
            } else if (isOfflineMode) {
                // Between probes: keep the ETA breathing on GPS alone.
                offlineEtaFallback(fix, distanceM)
            }
        }
        // Phase 10.4.3.4: fire once when ETA drops to ~3 min while still
        // outside the main trigger radius (never after it — see guard).
        val etaNow = lastEtaMin
        if (!hasFiredProactiveAlert && proactiveEnabled &&
            etaNow != null && etaNow <= PROACTIVE_ETA_MIN &&
            distanceM > triggerM
        ) {
            hasFiredProactiveAlert = true
            fireProactiveAlert()
        }
        pushNotification(fix)

        // Adaptive interval: re-request only when the bucket changes.
        val wanted = intervalFor(distanceM)
        if (wanted != currentIntervalMs) {
            requestUpdates(wanted)
        }
    }

    /**
     * Phase 10.5.x Step 5: on-device route attempt (Dispatchers.IO inside
     * the engine). On success the road ETA wins; on ANY failure the
     * Phase 10.4.3.7 GPS straight-line distanceTo() fallback already
     * applied by the caller stands — the alarm never breaks — and the
     * notification carries "Offline Routing failed, using GPS
     * straight-line" so the user knows.
     */
    private suspend fun runLocalRoute(fix: Location, distanceM: Float) {
        val local = runCatching {
            val dir = graphManager.installedRegion()
                ?: return@runCatching null
            if (!ghEngine.ensureLoaded(dir)) return@runCatching null
            ghEngine.calculateRoute(
                fix.latitude, fix.longitude,
                targetLat, targetLng,
            )
        }.getOrNull()
        if (local != null) {
            offlineFallbackNotice = null
            // DYNAMIC WAKE-UP: cache fresh route distance for TIME-mode ETA.
            routeRemainingM = local.distanceMeters.toFloat()
            routeRemainingAtMs = System.currentTimeMillis()
            lastEtaMin = local.etaMinutes
            persistEta()
            Log.i(
                TAG,
                "offline GH route: ${local.points.size} pts, ~${local.etaMinutes} min",
            )
        } else {
            offlineFallbackNotice = FALLBACK_NOTICE
            Log.w(TAG, "$FALLBACK_NOTICE (dist=${distanceM.toInt()}m GPS line)")
            showFallbackToast()
        }
    }

    /** Toast on the main thread — best-effort, never throws. */
    private fun showFallbackToast() {
        runCatching {
            android.os.Handler(Looper.getMainLooper()).post {
                runCatching {
                    android.widget.Toast.makeText(
                        applicationContext,
                        FALLBACK_NOTICE,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    /**
     * Phase 10.4.3.7: local ETA when the network is dead — distance /
     * speed, guarded against zero speed (then the last ETA stands).
     */
    private fun offlineEtaFallback(fix: Location, distanceM: Float) {
        val speedMps = if (fix.hasSpeed() && fix.speed > 0f) fix.speed else 0f
        if (speedMps > MIN_OFFLINE_SPEED_MPS) {
            lastEtaMin = (distanceM / speedMps / 60.0).toInt().coerceAtLeast(1)
            persistEta()
        }
        // speed ~ 0 → retain lastEtaMin; UI shows "--" when null.
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
            val eta = lastEtaMin?.let { "~$it mins ${mode.icon}" }
                ?: if (isOfflineMode) "--" else "calculating…"
            // Phase 10.4.3.7: reassure — GPS guard stays on without internet.
            // Step 5: exact fallback notice when GH failed.
            val offlineTag = if (isOfflineMode) " [Offline 🛰️]" else ""
            val fallbackTag = offlineFallbackNotice?.let { " — $it" } ?: ""
            "Dist: ${"%.1f".format(distKm)} km | " +
                "Speed: ${"%.0f".format(lastSpeedKmh)} km/h | ETA: $eta$offlineTag$fallbackTag"
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

        // Phase 10.4.3.3: actions double as Wear OS wrist actions below.
        val pauseAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            getString(if (isPaused) R.string.tracking_resume else R.string.tracking_pause),
            servicePending(ACTION_PAUSE_RESUME, REQ_PAUSE),
        ).build()
        val silentAction = NotificationCompat.Action.Builder(
            if (isSilent) android.R.drawable.ic_lock_silent_mode_off
            else android.R.drawable.ic_lock_silent_mode,
            getString(if (isSilent) R.string.tracking_ring else R.string.tracking_silent),
            servicePending(ACTION_TOGGLE_SILENT, REQ_SILENT),
        ).build()
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.tracking_stop),
            servicePending(ACTION_STOP, REQ_STOP),
        ).build()

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
            .addAction(pauseAction)
            .addAction(silentAction)
            .addAction(stopAction)
            // Phase 10.4.3.3: Wear OS mirrors Pause + Stop on the wrist.
            .extend(
                NotificationCompat.WearableExtender()
                    .addAction(pauseAction)
                    .addAction(stopAction),
            )
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TrackingService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Phase 10.4.3.4: the gentle heads-up — separate DEFAULT channel,
     * no sound, one soft buzz, auto-dismiss + explicit cancel on arrival.
     */
    private fun fireProactiveAlert() {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val contentIntent = PendingIntent.getActivity(
                this,
                REQ_PROACTIVE,
                Intent(this, com.app.wasalna.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, PROACTIVE_CHANNEL_ID)
                .setContentTitle("Approaching destination / اقتربنا من الوصول")
                .setContentText(
                    "We are close. Start gathering your belongings and preparing " +
                        "your trip fare. / اقتربنا، ابدأ في جمع أوراقك وتجهيز حساب الرحلة.",
                )
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "We are close. Start gathering your belongings and preparing " +
                            "your trip fare.\nاقتربنا، ابدأ في جمع أوراقك وتجهيز حساب الرحلة.",
                    ),
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSound(null)
                .setVibrate(longArrayOf(0, 300))
                .setAutoCancel(true)
                .setTimeoutAfter(PROACTIVE_TIMEOUT_MS)
                .setContentIntent(contentIntent)
                .build()
            manager.notify(PROACTIVE_NOTIF_ID, notification)
            Log.i(TAG, "proactive alert fired (ETA<=${PROACTIVE_ETA_MIN}min)")
        }.onFailure {
            Log.w(TAG, "fireProactiveAlert failed: ${it.message}")
        }
    }

    private fun dismissProactiveAlert() {
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(PROACTIVE_NOTIF_ID)
        }
    }

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
        // Phase 10.4.3.4: heads-up channel — DEFAULT (no loud sound),
        // one gentle buzz only.
        if (manager.getNotificationChannel(PROACTIVE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    PROACTIVE_CHANNEL_ID,
                    "Approaching alert",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300)
                },
            )
        }
    }

    /**
     * Phase 10.4.2.1: hold the CPU across screen-off deep sleep.
     * 12-hour timeout is a safety net; normal trips release far earlier
     * in onDestroy. Reference-counted default + isHeld guard = no leak.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Wasalna:TrackingWakeLock",
            ).apply {
                acquire(WAKELOCK_TIMEOUT_MS)
            }
            Log.i(TAG, "WakeLock acquired")
        }.onFailure {
            Log.w(TAG, "WakeLock acquire failed: ${it.message}")
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock released")
            }
        }.onFailure {
            Log.w(TAG, "WakeLock release failed: ${it.message}")
        }
        wakeLock = null
    }

    /**
     * Single funnel for firing the destination alarm (both modes). Guarded
     * so the alarm fires exactly once per trip; [firedMode] travels to
     * AlarmActivity so the notification states which mode fired
     * (DISTANCE radius vs TIME traffic-aware ETA).
     */
    private fun fireAlarm(firedMode: String) {
        synchronized(this) {
            if (hasFiredAlarm) return
            hasFiredAlarm = true
        }
        Log.i(TAG, "DESTINATION_REACHED lat=$targetLat lng=$targetLng mode=$firedMode")
        // Phase 10.4.3.4: the heads-up has served its purpose — dismiss.
        dismissProactiveAlert()
        hasFiredProactiveAlert = false
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
            val headphoneOnly = try {
                prefs.isHeadphoneOnlyEnabled.first()
            } catch (e: Exception) {
                Log.w(TAG, "headphone-only read failed: ${e.message}")
                false
            }
            // Phase 10.10: watch-only alert (PREMIUM-gated). When ON
            // and a paired watch with the companion app is reachable,
            // the watch alerts and the phone stays silent; when the
            // watch is unreachable we FALL BACK to the normal phone
            // gradual alarm so the user never misses the stop.
            val watchOnly = try {
                prefs.isWatchOnlyAlert.first()
            } catch (e: Exception) {
                Log.w(TAG, "watch-only read failed: ${e.message}")
                false
            }
            var watchDelivered = false
            var watchFallback = false
            if (watchOnly && premium) {
                watchDelivered = runCatching {
                    com.app.wasalna.data.wear.WatchAlertSender.sendAlert(
                        this@TrackingService,
                        targetName,
                    )
                }.getOrDefault(false)
                watchFallback = !watchDelivered
                Log.i(
                    TAG,
                    "watch-only: delivered=$watchDelivered fallback=$watchFallback",
                )
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
                putExtra(
                    com.app.wasalna.ui.screens.alarm.AlarmActivity.EXTRA_HEADPHONE_ONLY,
                    headphoneOnly,
                )
                putExtra(
                    com.app.wasalna.ui.screens.alarm.AlarmActivity.EXTRA_WATCH_ONLY,
                    watchOnly && premium && watchDelivered,
                )
                putExtra(
                    com.app.wasalna.ui.screens.alarm.AlarmActivity.EXTRA_WATCH_FALLBACK,
                    watchFallback,
                )
                putExtra(
                    com.app.wasalna.ui.screens.alarm.AlarmActivity.EXTRA_DESTINATION_NAME,
                    targetName,
                )
                putExtra(
                    com.app.wasalna.ui.screens.alarm.AlarmActivity.EXTRA_ALARM_MODE,
                    firedMode,
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
    }

    /**
     * DYNAMIC WAKE-UP — TIME-mode check. Feeds the fix into the EMA, then
     * re-reads the persisted mode on Dispatchers.Default (never Main) on
     * EVERY fix so a mid-trip DISTANCE/TIME switch applies immediately.
     * Funnels into [fireAlarm]; on ANY computation failure degrades to the
     * DISTANCE trigger so the stop is never missed.
     */
    private fun evaluateTimeModeAlarm(fix: Location, distanceM: Float, triggerM: Float) {
        if (hasFiredAlarm) return
        val nowMs = System.currentTimeMillis()
        wakeupEstimator.onFix(
            hasSpeed = fix.hasSpeed(),
            speedMps = fix.speed,
            hasAccuracy = fix.hasAccuracy(),
            accuracyM = fix.accuracy,
            fixTimeMs = fix.time,
            nowMs = nowMs,
        )
        serviceScope.launch(Dispatchers.Default) {
            val prefs = SettingsDataStore(applicationContext)
            val liveMode = runCatching { prefs.alarmMode.first() }
                .getOrDefault(alarmModeName)
            val liveMinutes = runCatching { prefs.alarmTimeMinutes.first() }
                .getOrDefault(alarmTimeMinutes)
            alarmModeName = liveMode
            alarmTimeMinutes = liveMinutes
            if (liveMode != AlarmMode.TIME.name) {
                // Switched to DISTANCE mid-trip — same guarantee as legacy.
                if (distanceM <= triggerRadiusMeters(fix)) {
                    fireAlarm(AlarmMode.DISTANCE.name)
                }
                return@launch
            }
            // Prefer fresh on-device route distance; else straight-line GPS
            // (same fallback philosophy as the offline engine).
            val freshRoute = routeRemainingM?.takeIf {
                nowMs - routeRemainingAtMs <= ROUTE_REMAINING_FRESH_MS
            }
            val remainingM = freshRoute ?: distanceM
            val etaMin = wakeupEstimator.estimateEtaMinutes(remainingM, nowMs)
            if (etaMin == null) {
                // Speed data invalid (no usable fix for >60s): fall back to
                // the distance threshold equivalent so the stop is never missed.
                if (distanceM <= triggerM) {
                    fireAlarm(AlarmMode.DISTANCE.name)
                }
                return@launch
            }
            lastEtaMin = etaMin
            persistEta()
            Log.i(
                TAG,
                "time-wakeup: eta=${etaMin}min threshold=${liveMinutes}min " +
                    "remaining=${remainingM.toInt()}m",
            )
            if (etaMin <= liveMinutes) {
                fireAlarm(AlarmMode.TIME.name)
            }
        }
    }

    /**
     * Phase 10.4.3.3: adaptive trigger distance — mode base (WALK 500m /
     * CAR 2km / TRAIN 4km), stretched when actually moving fast so the
     * user always gets ~2 minutes of warning (capped at 8 km).
     * Adaptive off → legacy fixed 2 km.
     */
    private fun triggerRadiusMeters(fix: Location): Float {
        if (!adaptiveEnabled) return TRIGGER_RADIUS_METERS
        val base = when (mode) {
            TransportMode.WALK -> 500f
            TransportMode.CAR -> 2000f
            TransportMode.TRAIN -> 4000f
        }
        // Bonus only when unusually fast: guarantees ~2 min of warning
        // (speed × 120 s) but never shrinks the mode base.
        val speedStretch = if (fix.hasSpeed() && fix.speed > 0f) fix.speed * 120f else 0f
        return maxOf(base, speedStretch).coerceAtMost(8000f)
    }

    /** Phase 10.4.2.1: keep the open trip row's ETA fresh for the widget. */
    private fun persistEta() {
        val id = activeTripId ?: return
        val eta = lastEtaMin ?: return
        serviceScope.launch {
            runCatching { tripRepo.updateEta(id, eta) }
        }
    }

    override fun onDestroy() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy removeLocationUpdates: ${e.message}")
        }
        // Phase 10.4.2.1: every stop path ends here — finish the trip row.
        val id = activeTripId
        activeTripId = null
        if (id != null) {
            serviceScope.launch {
                runCatching { tripRepo.finishTrip(id) }
            }
        }
        // Phase 10.4.3.4: safety reset — a fresh trip always starts clean.
        hasFiredProactiveAlert = false
        // Phase 10.4.2.1: every stop path ends here — battery never leaks.
        releaseWakeLock()
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

        /** Phase 10.4.3.4: gentle heads-up channel + notification id. */
        const val PROACTIVE_CHANNEL_ID = "wasalna_proactive"
        const val PROACTIVE_NOTIF_ID = 1002

        /** Fire the heads-up when ETA drops to this many minutes. */
        private const val PROACTIVE_ETA_MIN = 3

        /** Backstop: heads-up auto-dismisses after 10 minutes regardless. */
        private const val PROACTIVE_TIMEOUT_MS = 10L * 60L * 1000L

        private const val REQ_PROACTIVE = 14

        /** Alarm trigger radius: 2 km before the destination. */
        const val TRIGGER_RADIUS_METERS = 2000f

        /** DYNAMIC WAKE-UP: default minutes-before-arrival for TIME mode. */
        const val DEFAULT_ALARM_TIME_MINUTES = 5

        /** DYNAMIC WAKE-UP: cached route distance freshness window. */
        private const val ROUTE_REMAINING_FRESH_MS = 120_000L

        /**
         * FAILSAFE 🛡️ (صمام الأمان): walking-distance backstop — the alarm
         * fires here no matter the mode or speed state (never miss the stop).
         */
        private const val SAFETY_VALVE_METERS = 500f

        /** Phase 10.4.2.1: WakeLock safety ceiling — 12 hours. */
        private const val WAKELOCK_TIMEOUT_MS = 12L * 60L * 60L * 1000L

        private const val INITIAL_INTERVAL_MS = 10_000L

        /** Local ETA only counts when actually moving (>5 km/h). */
        private const val MIN_MOVING_KMH = 5.0

        /** OSRM ETA refresh floor while stationary (avoids API spam). */
        private const val OSRM_MIN_INTERVAL_MS = 120_000L

        /** Phase 10.4.3.7: minimum silence between offline probes. */
        private const val OSRM_RETRY_MS = 60_000L

        /** Phase 10.4.3.7: below this GPS speed the fallback ETA freezes. */
        private const val MIN_OFFLINE_SPEED_MPS = 0.5f

        /** Phase 10.5.x Step 5: exact user-facing offline fallback notice. */
        const val FALLBACK_NOTICE = "Offline Routing failed, using GPS straight-line"

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
