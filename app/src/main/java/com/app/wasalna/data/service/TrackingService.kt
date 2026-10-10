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
import com.app.wasalna.data.simulation.TripSimulationController
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
import kotlinx.coroutines.Job
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
    @Volatile private var hasFiredAlarm: Boolean = false
    private val wakeupEstimator = DynamicWakeupEstimator()
    private var routeRemainingM: Float? = null
    private var routeRemainingAtMs: Long = 0L
    // Phase 10.4.3.4 + pre-arrival NUDGE: one-shot silent heads-up per
    // trip (reset on every START). Cross-thread (main fix callback vs
    // Dispatchers.Default alarm evaluation) → volatile.
    @Volatile private var hasFiredProactiveAlert: Boolean = false
    private var proactiveEnabled: Boolean = true
    // Pre-arrival NUDGE lead (minutes, 1..10, default 3) — cached at trip
    // START, re-read live on every fix so mid-trip slider moves apply.
    private var nudgeLeadMinutes: Int = DEFAULT_NUDGE_LEAD_MINUTES
    private var currentIntervalMs: Long = -1L
    private var isTracking = false
    // TRIP SIMULATION seam (وضع محاكاة للرحلة للتجربة): virtual-fix feed
    // jobs. When the controller is off these stay null and the fused GPS
    // path below runs exactly as before (zero behavior change).
    private var simFeedJob: Job? = null
    private var simWatchJob: Job? = null
    private var isStopping: Boolean = false

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
            // STOP from the notification (incl. during simulation):
            // onDestroy() ends the controller too, so virtual fixes halt
            // and real fused GPS is restored on the next START.
            ACTION_STOP -> {
                Log.i(TAG, "onStartCommand: STOP from notification")
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            // PAUSE SEMANTICS (simulation-aware): the service-level pause
            // drops every fix in onFix(), so ETA/alarm clocks derived from
            // fixes hold their last state — no false alarm while paused.
            // When a simulation drives the fixes, the controller is paused
            // in lockstep so the virtual trip ALSO freezes: resume then
            // continues from the frozen point instead of jumping ahead.
            ACTION_PAUSE_RESUME -> {
                isPaused = !isPaused
                Log.i(TAG, "onStartCommand: paused=$isPaused")
                if (TripSimulationController.isActive) {
                    if (isPaused) TripSimulationController.pause()
                    else TripSimulationController.resume()
                }
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
        isStopping = false
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
            nudgeLeadMinutes = runCatching {
                prefs.nudgeLeadMinutes.first()
            }.getOrDefault(DEFAULT_NUDGE_LEAD_MINUTES)
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

    /**
     * TRIP SIMULATION seam: when the simulation controller is active,
     * virtual GPS fixes drive the SAME onFix() production path
     * (estimator + failsafe + nudge + alarm). Sim off → fused GPS path
     * below, byte-for-byte the legacy behavior.
     */
    private fun startSimFeed() {
        simFeedJob?.cancel()
        simWatchJob?.cancel()
        isTracking = true
        currentIntervalMs = SIM_FIX_INTERVAL_MS
        Log.i(TAG, "startSimFeed: virtual GPS driving production onFix()")
        simFeedJob = serviceScope.launch {
            TripSimulationController.fixes.collect { fix -> onFix(fix) }
        }
        // Stopping the simulation restores real GPS instantly.
        simWatchJob = serviceScope.launch {
            TripSimulationController.isActiveFlow.first { !it }
            simFeedJob?.cancel()
            simFeedJob = null
            if (!hasFiredAlarm && !isStopping && !isPaused) {
                Log.i(TAG, "simulation stopped — restoring real GPS")
                startTracking(force = true)
            }
        }
    }

    private fun startTracking(force: Boolean = false) {
        if (isTracking && !force) return
        if (TripSimulationController.isActive) {
            startSimFeed()
            return
        }
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
            // VEHICLE (user order Oct 2026): mid-trip vehicle switches
            // (TripRoute selector) apply by the next fix — the following
            // OSRM fetch uses the new profile and the trigger below uses
            // the new base. Read failure keeps the current mode (never
            // falls back to CAR mid-trip).
            runCatching { modePrefs.transportMode.first() }.getOrNull()?.let {
                mode = TransportMode.fromName(it)
            }
            // Nudge prefs re-read live too — mid-trip enable/lead
            // changes apply by the next fix (never disturbs the alarm).
            proactiveEnabled = runCatching { modePrefs.isProactiveAlertEnabled.first() }
                .getOrDefault(proactiveEnabled)
            nudgeLeadMinutes = runCatching { modePrefs.nudgeLeadMinutes.first() }
                .getOrDefault(nudgeLeadMinutes)
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
        } else if (mode.isTransit && mode != TransportMode.TRANSIT) {
            // Static transit (METRO/MONORAIL/TRAIN): door-to-door ETA from
            // the local planner — ride legs are not road-routable so OSRM
            // is skipped entirely here (works offline too). Generic
            // TRANSIT keeps its corridor path below (unchanged).
            serviceScope.launch(Dispatchers.Default) {
                val plan = runCatching {
                    com.app.wasalna.domain.model.TransitPlanner.plan(
                        fix.latitude, fix.longitude,
                        targetLat, targetLng, mode,
                    )
                }.getOrNull()
                if (plan != null && plan.kind != "NONE") {
                    lastEtaMin = plan.totalMin
                    persistEta()
                }
                pushNotification(fix)
            }
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
        // Pre-arrival NUDGE: silent once-per-trip heads-up, strictly
        // BEFORE the main alarm (ordering validated inside).
        evaluateNudge(distanceM, triggerM)
        pushNotification(fix)

        // Adaptive interval: re-request only when the bucket changes.
        val wanted = intervalFor(distanceM)
        if (wanted != currentIntervalMs) {
            requestUpdates(wanted)
        }
    }

    /**
     * Phase 10.5.x Step 5 (upgraded Oct 2026): on-device route with
     * per-region auto-select — the INSTALLED graph covering fix→target
     * is loaded (auto-switch across regions; engine holds one graph).
     * On success the road ETA wins; on ANY failure the Phase 10.4.3.7
     * GPS straight-line distanceTo() fallback already applied by the
     * caller stands — the alarm never breaks — and the notification
     * carries the fallback notice so the user knows.
     * ── OFFLINE BOUNDARY: engine + files only, zero network. ──
     */
    private suspend fun runLocalRoute(fix: Location, distanceM: Float) {
        val routed = runCatching {
            com.app.wasalna.data.routing.routeOfflineWithAutoSelect(
                graphManager, ghEngine,
                fix.latitude, fix.longitude,
                targetLat, targetLng,
            )
        }.getOrNull()
        val local = routed?.second
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
            offlineFallbackNotice = getString(R.string.tracking_offline_fallback)
            Log.w(TAG, "offline fallback (dist=${distanceM.toInt()}m GPS line)")
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
                        getString(R.string.tracking_offline_fallback),
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
        val title = if (isPaused) getString(R.string.tracking_paused_title, targetName) else getString(R.string.tracking_heading_title, targetName)
        val body = if (isPaused) {
            getString(R.string.tracking_paused_body)
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
            getString(R.string.tracking_text)
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
     * Pre-arrival NUDGE — silent once-per-trip heads-up, strictly BEFORE
     * the main alarm. Reuses the SAME ETA ([lastEtaMin]) the alarm path
     * already computed (moving local estimate / OSRM / offline GPS+GH —
     * never duplicated here), so it works offline on GPS-only inputs too.
     *
     * ORDERING validation (nudge threshold > alarm threshold, all modes):
     * - TIME mode: effective lead = max(user lead, alarmMinutes + 1) — the
     *   nudge ETA threshold is strictly greater, so on a countdown it
     *   always trips on an earlier fix than the alarm. Same-fix race is
     *   safe: this check runs synchronously while the TIME alarm evaluates
     *   async — the nudge lands first, then the alarm.
     * - DISTANCE mode: the alarm already returned early above when
     *   distanceM <= triggerM, and the failsafe returned when <= 500 m —
     *   reaching here means still outside both radii (strictly before).
     * - ETA unavailable (offline/standstill): distance-equivalent band
     *   (alarmRadius, alarmRadius + margin] — strictly outside the alarm
     *   radius AND the 500 m failsafe, so it can never fire after/instead.
     * - [hasFiredAlarm] guard: even if the async TIME evaluation won the
     *   race, the nudge is suppressed after the alarm — never after it.
     *
     * Dismissing this notification only cancels its own id — the main
     * alarm path is untouched and still fires (fireAlarm dismisses the
     * nudge, never the reverse).
     */
    private fun evaluateNudge(distanceM: Float, triggerM: Float) {
        if (hasFiredProactiveAlert || !proactiveEnabled || hasFiredAlarm) return
        val etaNow = lastEtaMin
        if (etaNow != null) {
            if (alarmModeName == AlarmMode.TIME.name) {
                val effectiveLead = maxOf(
                    nudgeLeadMinutes,
                    alarmTimeMinutes + NUDGE_TIME_MARGIN_MIN,
                )
                if (etaNow <= effectiveLead) {
                    hasFiredProactiveAlert = true
                    fireProactiveAlert()
                }
            } else if (etaNow <= nudgeLeadMinutes &&
                distanceM > triggerM && distanceM > SAFETY_VALVE_METERS
            ) {
                hasFiredProactiveAlert = true
                fireProactiveAlert()
            }
            return
        }
        // ETA unavailable — distance equivalent of the lead, strictly
        // outside every alarm radius (same GPS-only fallback philosophy
        // as the alarm itself).
        val alarmRadiusM = maxOf(triggerM, SAFETY_VALVE_METERS)
        if (distanceM <= alarmRadiusM + NUDGE_DISTANCE_MARGIN_M &&
            distanceM > alarmRadiusM
        ) {
            hasFiredProactiveAlert = true
            fireProactiveAlert()
        }
    }

    /**
     * The silent nudge — dedicated IMPORTANCE_LOW channel ([NUDGE_CHANNEL_ID],
     * never the alarm channel): no sound, no vibration, no heads-up, no
     * full-screen intent — just a quiet shade entry, DND-friendly by design.
     * Auto-dismiss + explicit cancel on arrival; dismissing it never
     * cancels the main alarm (separate notification id, no shared state).
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
            val notification = NotificationCompat.Builder(this, NUDGE_CHANNEL_ID)
                .setContentTitle(getString(R.string.nudge_title))
                .setContentText(getString(R.string.nudge_body))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        getString(R.string.nudge_body) + "\n" +
                            getString(R.string.nudge_body_alt),
                    ),
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSilent(true)
                .setSound(null)
                .setVibrate(null)
                .setAutoCancel(true)
                .setTimeoutAfter(PROACTIVE_TIMEOUT_MS)
                .setContentIntent(contentIntent)
                .build()
            manager.notify(NUDGE_NOTIF_ID, notification)
            Log.i(TAG, "silent nudge fired (lead=${nudgeLeadMinutes}min)")
        }.onFailure {
            Log.w(TAG, "fireProactiveAlert failed: ${it.message}")
        }
    }

    private fun dismissProactiveAlert() {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            // Dismissing the nudge never touches the alarm — separate id.
            manager.cancel(NUDGE_NOTIF_ID)
            // Hygiene: legacy pre-silent channel id, if ever posted.
            manager.cancel(PROACTIVE_NOTIF_ID)
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
        isStopping = true
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
        // Pre-arrival NUDGE channel — IMPORTANCE_LOW, fully silent (no
        // sound, no vibration, no heads-up): a quiet shade entry that is
        // DND-friendly by design. A NEW channel id (the legacy
        // wasalna_proactive channel was IMPORTANCE_DEFAULT — importance is
        // immutable once created, so the id had to change); the legacy
        // channel is deleted below for hygiene.
        if (manager.getNotificationChannel(NUDGE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NUDGE_CHANNEL_ID,
                    "Pre-arrival nudge",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    vibrationPattern = null
                    setShowBadge(false)
                },
            )
        }
        runCatching { manager.deleteNotificationChannel(PROACTIVE_CHANNEL_ID) }
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
        // TRIP SIMULATION: end the virtual trip (idempotent; onDestroy
        // repeats it safely) so a re-run starts clean.
        isStopping = true
        TripSimulationController.notifyTripEnded()
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
     * BICYCLE 1km / CAR 2km / MOTORCYCLE 2.5km / TRANSIT 1km / METRO
     * 1.5km / MONORAIL 2km / TRAIN-LRT 2.5km), stretched
     * when actually moving fast so the
     * user always gets ~2 minutes of warning (capped at 8 km).
     * Adaptive off → legacy fixed 2 km.
     */
    private fun triggerRadiusMeters(fix: Location): Float {
        if (!adaptiveEnabled) return TRIGGER_RADIUS_METERS
        // Vehicle bases (user order Oct 2026): walk 500 m (= the 500 m
        // FAILSAFE floor — the guard and the trigger coincide, never
        // conflict); bicycle 1 km; car 2 km (legacy); motorcycle 2.5 km
        // (faster city pace, ~2 min warning); TRANSIT 1 km (station-stop
        // rhythm: stops land ~1 km apart, and the 500 m FAILSAFE backstops
        // every fix regardless — the stop is never overshot); METRO
        // 1.5 km; MONORAIL 2 km; TRAIN is now LRT Capital Train 2.5 km
        // (was 4 km as a hidden driving proxy).
        val base = when (mode) {
            TransportMode.WALK -> 500f
            TransportMode.BICYCLE -> 1000f
            TransportMode.CAR -> 2000f
            TransportMode.MOTORCYCLE -> 2500f
            TransportMode.TRANSIT -> 1000f
            TransportMode.METRO -> 1500f
            TransportMode.MONORAIL -> 2000f
            TransportMode.TRAIN -> 2500f
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
        isStopping = true
        simFeedJob?.cancel()
        simFeedJob = null
        simWatchJob?.cancel()
        simWatchJob = null
        TripSimulationController.notifyTripEnded()
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

        /**
         * Pre-arrival NUDGE: dedicated silent channel + notification id.
         * Never the alarm channel. PROACTIVE_* kept only to delete the
         * legacy IMPORTANCE_DEFAULT channel + cancel its id (hygiene).
         */
        const val NUDGE_CHANNEL_ID = "wasalna_nudge_silent"
        const val NUDGE_NOTIF_ID = 1002
        const val PROACTIVE_CHANNEL_ID = "wasalna_proactive"
        const val PROACTIVE_NOTIF_ID = 1002

        /** Default nudge lead (minutes-before-arrival); adjustable 1..10. */
        const val DEFAULT_NUDGE_LEAD_MINUTES = 3
        const val NUDGE_LEAD_MIN = 1
        const val NUDGE_LEAD_MAX = 10

        /**
         * ORDERING margin (TIME mode): effective nudge lead =
         * max(userLead, alarmMinutes + this) — strictly greater than the
         * TIME alarm threshold so the nudge always fires first.
         */
        private const val NUDGE_TIME_MARGIN_MIN = 1

        /**
         * ORDERING margin (distance fallback): nudge band =
         * (alarmRadius, alarmRadius + this] — strictly outside the alarm
         * radius and the 500 m failsafe.
         */
        private const val NUDGE_DISTANCE_MARGIN_M = 500f

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

        /** TRIP SIMULATION: virtual-fix cadence (matches the 10 s bucket). */
        private const val SIM_FIX_INTERVAL_MS = 10_000L

        /** Local ETA only counts when actually moving (>5 km/h). */
        private const val MIN_MOVING_KMH = 5.0

        /** OSRM ETA refresh floor while stationary (avoids API spam). */
        private const val OSRM_MIN_INTERVAL_MS = 120_000L

        /** Phase 10.4.3.7: minimum silence between offline probes. */
        private const val OSRM_RETRY_MS = 60_000L

        /** Phase 10.4.3.7: below this GPS speed the fallback ETA freezes. */
        private const val MIN_OFFLINE_SPEED_MPS = 0.5f

        /** Phase 10.5.x Step 5: exact user-facing offline fallback notice. */

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
