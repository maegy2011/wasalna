package com.app.wasalna.ui.screens.alarm

import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.wasalna.R
import com.app.wasalna.data.local.datastore.SettingsDataStore
import com.app.wasalna.data.wear.WatchAlertSender
import com.app.wasalna.domain.audio.HeadphoneMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase 6: full-screen alarm fired when TrackingService reaches the radius.
 * Phase 9 (redone): silent alarm (premium) — headset → STREAM_MUSIC/USAGE_MEDIA
 * to earphones only; no headset → vibrate-only pattern, no loud speaker.
 *
 * Extras: [EXTRA_SILENT] + [EXTRA_PREMIUM] (sent by TrackingService).
 * Silent applies only when silent && premium (premium gate).
 *
 * Phase 10.9.x: smart gradual alarm (enabled in Settings → المنبه
 * التدريجي, OFF by default). When on (and NOT silent), a
 * GradualAlarmController runs vibration-only → quiet ramp → full alarm
 * instead of the legacy instant full-volume path.
 *
 * DND (documented, unchanged): the alarm uses STREAM_ALARM which obeys
 * the system's Do-Not-Disturb alarm policy. This screen never requests
 * ACCESS_NOTIFICATION_POLICY and never touches the interruption filter —
 * alarms ring when DND allows alarms, stay muted otherwise. The "Force
 * Alarm DND Override" toggle in Settings is visual-only (no DataStore
 * field, no behaviour change).
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var savedAlarmVolume: Int = -1
    private var savedMusicVolume: Int = -1
    private var vibrator: Vibrator? = null
    private var stopped = false
    private var silentMode = false
    // Phase 10.4.3.1: gates the custom ringtone in resolveAlarmUri().
    private var premiumMode = false
    // Headphone-only alerts (premium): alarm plays ONLY through connected
    // headphones; the speaker stays silent. [headphoneFallback] = true when
    // no headphones were connected at alarm time → normal phone alarm so
    // the stop is never missed (noted in the alarm notification).
    private var headphoneOnlyMode = false
    private var headphoneActive = false
    private var headphoneFallback = false
    // Phase 10.9.x: owns player + vibrator + volume while the gradual
    // escalation runs; null = legacy instant path. stop() halts ALL
    // sound + vibration synchronously (no leaks, no lingering coroutine).
    private var gradualController: GradualAlarmController? = null
    // Phase 10.10: watch-only alert (PREMIUM-gated) — the watch already
    // alerted, so the phone MUST stay silent (no sound, no vibration).
    // [watchFallback] = true when the watch was unreachable at alarm
    // time → normal phone alarm so the stop is never missed (noted in
    // the alarm notification).
    private var watchOnlyMode = false
    private var watchFallback = false
    // DYNAMIC WAKE-UP: which mode fired (DISTANCE radius vs TIME ETA) —
    // stated in the alarm notification so the user knows why it rang.
    private var alarmModeName = "DISTANCE"
    private var destinationName = ""
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dismissReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 10.4.3.3: cold-start with a wrist action is meaningless
        // (no sound owns this instance) — dismiss quietly. Live-instance
        // actions arrive via onNewIntent below.
        if (intent.action == ACTION_DISMISS_ALARM ||
            intent.action == ACTION_SILENCE_ALARM
        ) {
            stopAlarm()
            return
        }
        val wantSilent = intent.getBooleanExtra(EXTRA_SILENT, false)
        val isPremium = intent.getBooleanExtra(EXTRA_PREMIUM, false)
        val wantHeadphoneOnly = intent.getBooleanExtra(EXTRA_HEADPHONE_ONLY, false)
        premiumMode = isPremium
        silentMode = wantSilent && isPremium
        headphoneOnlyMode = wantHeadphoneOnly && isPremium
        // Phase 10.10: watch-only applies only when premium gated AND the
        // watch actually received the alert (TrackingService guarantee).
        watchOnlyMode = intent.getBooleanExtra(EXTRA_WATCH_ONLY, false) && isPremium
        watchFallback = intent.getBooleanExtra(EXTRA_WATCH_FALLBACK, false)
        alarmModeName = intent.getStringExtra(EXTRA_ALARM_MODE)?.takeIf { it == "TIME" } ?: "DISTANCE"
        destinationName = intent.getStringExtra(EXTRA_DESTINATION_NAME).orEmpty()
        Log.i(TAG, "onCreate: alarm firing silent=$silentMode headphoneOnly=$headphoneOnlyMode watchOnly=$watchOnlyMode fallback=$watchFallback")
        // Voice announcement (Oct 2026): speak the arrival aloud via the
        // system TTS engine — skipped for silent mode (vibrate only) and
        // watch-only (the watch owns the alert).
        if (!silentMode && !watchOnlyMode && destinationName.isNotBlank()) {
            lifecycleScope.launch {
                val speak = runCatching {
                    com.app.wasalna.data.local.datastore.SettingsDataStore(this@AlarmActivity)
                        .isVoiceAnnounceEnabled.first()
                }.getOrDefault(false)
                if (speak) {
                    com.app.wasalna.data.speech.SpeechAnnouncer.speak(
                        getString(R.string.alarm_arrived_voice, destinationName),
                    )
                }
            }
        }
        // Phase 10.10: watch-side dismissal lands here as a package-scoped
        // broadcast (PhoneWatchDismissService) — dismissing from either
        // side stops everything.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_DISMISS_ALARM) stopAlarm()
            }
        }
        dismissReceiver = receiver
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(ACTION_DISMISS_ALARM),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "dismiss receiver: ${it.message}") }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km?.requestDismissKeyguard(this, null)
        }
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (watchOnlyMode) {
            // Phone-silent guarantee: the paired watch is already alerting
            // — no sound, no vibration on the phone. The screen still shows
            // so the destination is visible if the user picks up the phone.
            Log.i(TAG, "watch-only: phone stays silent, watch is alerting")
        } else {
            lifecycleScope.launch {
                if (headphoneOnlyMode) {
                    val device = HeadphoneMonitor(this@AlarmActivity).findHeadphoneDevice()
                    if (device != null) {
                        headphoneActive = true
                        Log.i(TAG, "headphone-only: pinned to ${device.productName} type=${device.type}")
                        val gradual = readGradualConfig()
                        if (gradual != null) {
                            Log.i(TAG, "onCreate: headphone-only gradual alarm $gradual")
                            val uri = resolveGradualUri()
                            if (uri != null) {
                                val controller = GradualAlarmController(this@AlarmActivity)
                                gradualController = controller
                                controller.start(uri, gradual, device, headphoneOnly = true)
                            } else {
                                startHeadphoneOnlySound(device)
                            }
                        } else {
                            startHeadphoneOnlySound(device)
                        }
                    } else {
                        // Fallback guarantee: headphones gone → normal loud phone
                        // alarm. Missing the stop is worse than waking others.
                        headphoneFallback = true
                        Log.w(TAG, "headphone-only: none connected → loud fallback")
                        maxAlarmVolume()
                        startAlarmSound()
                    }
                } else if (silentMode) {
                    if (isHeadsetConnected()) {
                        Log.i(TAG, "silent: headset on → earphones route")
                        startSilentEarphoneSound()
                    } else {
                        Log.i(TAG, "silent: no headset → vibrate-only")
                        startVibrateOnly()
                    }
                } else {
                    // Phase 10.9.x: gradual escalation when enabled (OFF by
                    // default → legacy instant full-volume path unchanged).
                    val gradual = readGradualConfig()
                    if (gradual != null) {
                        Log.i(TAG, "onCreate: gradual alarm $gradual")
                        startGradualAlarm(gradual)
                    } else {
                        maxAlarmVolume()
                        startAlarmSound()
                    }
                }
            }
        }

        // Phase 10.4.3.3: wrist-reachable alarm (Wear OS mirrors actions).
        postAlarmNotification()

        // REDESIGN 10 Oct 2026: calm wake screen — phase indicator +
        // huge dismiss + snooze. All audio/mode flags above untouched.
        val phaseFlow = gradualController?.phase
        setContent {
            AlarmScreen(
                silentMode = silentMode || headphoneActive,
                watchOnly = watchOnlyMode,
                watchFallback = watchFallback,
                alarmModeName = alarmModeName,
                gradualActive = phaseFlow != null,
                phaseFlow = phaseFlow,
                onLongPressStop = { stopAlarm() },
                onDismiss = { stopAlarm() },
                onSnooze = { snoozeAlarm() },
            )
        }
    }

    /**
     * Phase 10.4.3.3: wrist taps land here (SINGLE_TOP) on the instance
     * that actually owns the player + vibrator.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            ACTION_DISMISS_ALARM -> stopAlarm()
            ACTION_SILENCE_ALARM -> silenceAlarm()
        }
    }

    private fun isHeadsetConnected(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        try {
            @Suppress("DEPRECATION")
            if (am.isWiredHeadsetOn) return true
        } catch (e: Exception) {
            Log.w(TAG, "wired check failed: ${e.message}")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    ) return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "device check failed: ${e.message}")
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            if (adapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
                BluetoothProfile.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.HEADSET) ==
                BluetoothProfile.STATE_CONNECTED
            ) return true
        } catch (e: SecurityException) {
            Log.w(TAG, "BT check denied: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "BT check failed: ${e.message}")
        }
        return false
    }

    /**
     * User order Oct 2026: custom ringtone is FREE — every failure
     * (no URI, unreadable file) falls back to the system alarm tone.
     */
    private suspend fun resolveAlarmUri(isPremium: Boolean): android.net.Uri? {
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return runCatching {
            val custom = withContext(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    SettingsDataStore(this@AlarmActivity).customRingtoneUri.first()
                }
            }.orEmpty()
            if (custom.isBlank()) return fallback
            val uri = android.net.Uri.parse(custom)
            contentResolver.openInputStream(uri)?.close()
            uri
        }.onFailure {
            Log.w(TAG, "custom ringtone unreadable, using default: ${it.message}")
        }.getOrNull() ?: fallback
    }

    private suspend fun startSilentEarphoneSound() {
        val uri = resolveAlarmUri(premiumMode)
        if (uri == null) {
            Log.e(TAG, "silent: no ringtone, vibrate fallback")
            startVibrateOnly()
            return
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am
        try {
            if (am != null) {
                savedMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val low = (am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 3)
                    .coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, low, 0)
            }
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "silent: looping to earphones")
        } catch (e: Exception) {
            Log.e(TAG, "silent sound failed: ${e.message}")
            releasePlayer()
            startVibrateOnly()
        }
        startVibration(AGGRESSIVE_PATTERN, 0)
    }

    /**
     * Phase 10.9.x: reads the gradual-alarm settings (Dispatchers.IO,
     * 2 s cap). Null = feature disabled or settings unreadable → the
     * caller falls back to the legacy instant path. Never throws.
     */
    private suspend fun readGradualConfig(): GradualAlarmConfig? {
        return runCatching {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    val store = SettingsDataStore(this@AlarmActivity)
                    if (!store.isGradualAlarmEnabled.first()) {
                        return@withTimeoutOrNull null
                    }
                    GradualAlarmConfig(
                        vibrationOnlySec = store.gradualVibrationSec.first(),
                        rampSec = store.gradualRampSec.first(),
                        vibrationStrength = store.gradualVibrationStrength.first(),
                    )
                }
            }
        }.getOrNull()
    }

    /**
     * Phase 10.9.x: gradual tone wins when set + readable; otherwise the
     * existing chain applies (premium custom tone → system default).
     */
    private suspend fun resolveGradualUri(): Uri? {
        runCatching {
            val custom = withContext(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    SettingsDataStore(this@AlarmActivity).gradualRingtoneUri.first()
                }
            }.orEmpty()
            if (custom.isNotBlank()) {
                val uri = Uri.parse(custom)
                contentResolver.openInputStream(uri)?.close()
                return uri
            }
        }.onFailure {
            Log.w(TAG, "gradual tone unreadable, falling back: ${it.message}")
        }
        return resolveAlarmUri(premiumMode)
    }

    private suspend fun startGradualAlarm(config: GradualAlarmConfig) {
        val uri = resolveGradualUri()
        if (uri == null) {
            Log.e(TAG, "startGradualAlarm: no ringtone, legacy fallback")
            maxAlarmVolume()
            startAlarmSound()
            return
        }
        val controller = GradualAlarmController(this)
        gradualController = controller
        controller.start(uri, config)
    }

    /**
     * Headphone-only instant path (no gradual escalation): the ringtone
     * loops with USAGE_MEDIA on STREAM_MUSIC, pinned to [device] via
     * setPreferredDevice (API 28+) so the phone speaker stays silent.
     * API 26–27 has no setPreferredDevice — the system already routes
     * STREAM_MUSIC to connected headphones, so exclusivity holds there
     * by platform routing. Vibration stays on (haptic, not a speaker).
     */
    private suspend fun startHeadphoneOnlySound(device: AudioDeviceInfo) {
        val uri = resolveAlarmUri(premiumMode)
        if (uri == null) {
            Log.e(TAG, "headphone-only: no ringtone, vibrate fallback")
            startVibrateOnly()
            return
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am
        try {
            if (am != null) {
                savedMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val mid = (am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2)
                    .coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, mid, 0)
            }
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { setPreferredDevice(device) }
                        .onFailure { Log.w(TAG, "preferred device failed: ${it.message}") }
                }
                prepare()
                start()
            }
            Log.i(TAG, "headphone-only: looping to headphones, speaker silent")
        } catch (e: Exception) {
            Log.e(TAG, "headphone-only sound failed: ${e.message}")
            releasePlayer()
            startVibrateOnly()
        }
        startVibration(AGGRESSIVE_PATTERN, 0)
    }

    private fun startVibrateOnly() {
        startVibration(AGGRESSIVE_PATTERN, 0)
    }

    private fun startVibration(pattern: LongArray, repeat: Int) {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator = vib
            if (vib == null || !vib.hasVibrator()) {
                Log.w(TAG, "vibrate: no vibrator")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, repeat)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "vibrate denied: ${e.message}")
        }
    }

    private fun maxAlarmVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        try {
            savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            Log.i(TAG, "maxAlarmVolume: saved=$savedAlarmVolume max=$max")
        } catch (e: SecurityException) {
            Log.w(TAG, "maxAlarmVolume denied: ${e.message}")
        }
    }

    private suspend fun startAlarmSound() {
        val uri = resolveAlarmUri(premiumMode)
        if (uri == null) {
            Log.e(TAG, "startAlarmSound: no ringtone URI")
            return
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "startAlarmSound: looping $uri")
        } catch (e: Exception) {
            Log.e(TAG, "startAlarmSound failed: ${e.message}")
            releasePlayer()
        }
    }

    /**
     * Phase 10.4.3.3: high-priority alarm notification so a paired watch
     * buzzes hard. Stop/Silence ride the WearableExtender (wrist control);
     * tapping the body reopens this screen. Aggressive repeating vibration
     * is driven by startVibration (waveform, repeat from 0).
     */
    private fun postAlarmNotification() {
        runCatching {
            val manager = getSystemService(
                android.app.NotificationManager::class.java,
            ) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        android.app.NotificationChannel(
                            ALARM_CHANNEL_ID,
                            "Alarm firing",
                            android.app.NotificationManager.IMPORTANCE_HIGH,
                        ).apply {
                            enableVibration(true)
                            vibrationPattern = AGGRESSIVE_PATTERN
                        },
                    )
                }
            }
            val stopPending = android.app.PendingIntent.getActivity(
                this,
                REQ_WEAR_STOP,
                android.content.Intent(this, AlarmActivity::class.java).apply {
                    action = ACTION_DISMISS_ALARM
                    addFlags(
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val silencePending = android.app.PendingIntent.getActivity(
                this,
                REQ_WEAR_SILENCE,
                android.content.Intent(this, AlarmActivity::class.java).apply {
                    action = ACTION_SILENCE_ALARM
                    addFlags(
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val stopAction = androidx.core.app.NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPending,
            ).build()
            val silenceAction = androidx.core.app.NotificationCompat.Action.Builder(
                android.R.drawable.ic_lock_silent_mode,
                "Silent",
                silencePending,
            ).build()
            val contentPending = android.app.PendingIntent.getActivity(
                this,
                REQ_WEAR_CONTENT,
                android.content.Intent(this, AlarmActivity::class.java).apply {
                    addFlags(
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            // DYNAMIC WAKE-UP: notification states which mode fired.
            val baseText = when {
                watchOnlyMode -> getString(R.string.alarm_notif_watch_only)
                watchFallback -> getString(R.string.alarm_notif_watch_fallback)
                headphoneFallback -> getString(R.string.alarm_notif_headphone_fallback)
                else -> getString(R.string.alarm_notif_open_hint)
            }
            val modeText = if (alarmModeName == "TIME") {
                getString(R.string.alarm_notif_mode_time)
            } else {
                getString(R.string.alarm_notif_mode_distance)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle(getString(R.string.alarm_notif_title))
                .setContentText("$baseText • $modeText")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(contentPending)
                .addAction(stopAction)
                .addAction(silenceAction)
                .extend(
                    androidx.core.app.NotificationCompat.WearableExtender()
                        .addAction(stopAction)
                        .addAction(silenceAction),
                )
                .build()
            manager.notify(ALARM_NOTIF_ID, notification)
        }.onFailure {
            Log.w(TAG, "postAlarmNotification failed: ${it.message}")
        }
    }

    private fun cancelAlarmNotification() {
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(ALARM_NOTIF_ID)
        }
    }

    /**
     * Phase 10.4.3.3: wrist "Silent" — kills the sound but keeps the
     * violent vibration + screen so a sleeping user still wakes up.
     */
    /** Phase 10.10: phone dismissal also quiets the watch (fire-and-forget). */
    private fun notifyWatchDismissed() {
        ioScope.launch {
            runCatching { WatchAlertSender.sendDismiss(this@AlarmActivity) }
        }
    }

    private fun silenceAlarm() {
        if (stopped) return
        Log.i(TAG, "silenceAlarm: sound off, vibration continues")
        // Phase 10.9.x: gradual path keeps vibrating, kills its own player.
        gradualController?.silenceSound()
        releasePlayer()
        cancelAlarmNotification()
        notifyWatchDismissed()
    }

    /**
     * REDESIGN 10 Oct 2026: snooze — quiets everything now (same full
     * stop as dismiss: sound + vibration + notification + watch), then
     * re-fires this same screen in 5 minutes via AlarmManager with the
     * IDENTICAL mode extras (silent/premium/headphone/watch/mode copied
     * read-only from the firing intent). No flag is reinterpreted.
     */
    private fun snoozeAlarm() {
        if (stopped) return
        stopped = true
        Log.i(TAG, "snoozeAlarm: quiet now, refire in 5 min")
        cancelAlarmNotification()
        notifyWatchDismissed()
        gradualController?.release()
        gradualController = null
        releasePlayer()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "snooze vibrate cancel: ${e.message}")
        } finally {
            vibrator = null
        }
        restoreVolume()
        runCatching {
            val am = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            val refire = android.content.Intent(this, AlarmActivity::class.java).apply {
                action = null
                intent?.extras?.let { putExtras(it) }
            }
            val pending = android.app.PendingIntent.getActivity(
                this,
                REQ_SNOOZE,
                refire,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            am?.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + SNOOZE_MS,
                pending,
            )
        }.onFailure {
            Log.w(TAG, "snooze schedule failed: ${it.message}")
        }
        finish()
    }

    private fun stopAlarm() {
        if (stopped) return
        stopped = true
        Log.i(TAG, "stopAlarm: dismissed silent=$silentMode")
        cancelAlarmNotification()
        // Phase 10.10: dismissing from either side stops everything.
        notifyWatchDismissed()
        // Phase 10.9.x: stops ALL gradual sound + vibration immediately
        // and releases the escalation scope (no leaks).
        gradualController?.release()
        gradualController = null
        releasePlayer()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "vibrate cancel: ${e.message}")
        } finally {
            vibrator = null
        }
        restoreVolume()
        finish()
    }

    private fun releasePlayer() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "releasePlayer: ${e.message}")
        } finally {
            player = null
        }
    }

    private fun restoreVolume() {
        val am = audioManager ?: return
        try {
            if (savedAlarmVolume >= 0) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            }
            if (savedMusicVolume >= 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "restoreVolume denied: ${e.message}")
        }
        savedAlarmVolume = -1
        savedMusicVolume = -1
    }

    override fun onDestroy() {
        runCatching { dismissReceiver?.let { unregisterReceiver(it) } }
        dismissReceiver = null
        ioScope.cancel()
        // Phase 10.9.x: safety net — same immediate full stop as dismiss.
        gradualController?.release()
        gradualController = null
        releasePlayer()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy vibrate cancel: ${e.message}")
        } finally {
            vibrator = null
        }
        restoreVolume()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmActivity"
        const val EXTRA_SILENT = "extra_silent"
        const val EXTRA_PREMIUM = "extra_premium"
        const val EXTRA_HEADPHONE_ONLY = "extra_headphone_only"
        const val EXTRA_WATCH_ONLY = "extra_watch_only"
        const val EXTRA_WATCH_FALLBACK = "extra_watch_fallback"
        const val EXTRA_DESTINATION_NAME = "extra_destination_name"
        /** DYNAMIC WAKE-UP: "DISTANCE" or "TIME" — which mode fired. */
        const val EXTRA_ALARM_MODE = "extra_alarm_mode"

        // Phase 10.4.3.3: wrist actions (WearableExtender targets).
        const val ACTION_DISMISS_ALARM = "com.app.wasalna.action.DISMISS_ALARM"
        const val ACTION_SILENCE_ALARM = "com.app.wasalna.action.SILENCE_ALARM"
        const val ALARM_CHANNEL_ID = "wasalna_alarm"
        const val ALARM_NOTIF_ID = 2001
        private const val REQ_WEAR_STOP = 21
        private const val REQ_WEAR_SILENCE = 22
        private const val REQ_WEAR_CONTENT = 23
        private const val REQ_SNOOZE = 24
        private const val SNOOZE_MS = 5 * 60 * 1000L

        /**
         * Phase 10.4.3.3: violent repeating waveform — 1.2 s buzz /
         * 0.4 s gap, looping from index 0 until explicitly cancelled.
         */
        val AGGRESSIVE_PATTERN = longArrayOf(0, 1200, 400, 1200, 400)
    }
}

/**
 * REDESIGN 10 Oct 2026: calm wake screen matching the gradual alarm —
 * soft dawn palette, phase indicator (vibration → ramp → full),
 * HUGE dismiss (72dp) + snooze (56dp), one primary action.
 *
 * Kept: long-press anywhere stops, silent/watch subtitles, alarm-mode
 * line, DND/headphone/watch-only flags respected READ-ONLY (badges
 * display only — no behaviour change, no new permissions).
 */
@Composable
private fun AlarmScreen(
    silentMode: Boolean,
    watchOnly: Boolean = false,
    watchFallback: Boolean = false,
    alarmModeName: String = "DISTANCE",
    gradualActive: Boolean = false,
    phaseFlow: kotlinx.coroutines.flow.StateFlow<GradualPhase>? = null,
    onLongPressStop: () -> Unit,
    onDismiss: () -> Unit = onLongPressStop,
    onSnooze: () -> Unit = {},
) {
    val currentPhase by (phaseFlow?.collectAsState() ?: remember {
        mutableStateOf(GradualPhase.FULL)
    })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101828))
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPressStop() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            // Phase indicator: vibration → ramp → full (gradual path) or
            // a single full-alarm state (legacy instant path).
            if (gradualActive) {
                PhaseIndicator(currentPhase = currentPhase)
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFD54F).copy(alpha = 0.16f),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.alarm_phase_instant),
                        color = Color(0xFFFFD54F),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color(0xFFF2A007), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (silentMode) Icons.Filled.Headset else Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.alarm_title),
                color = Color(0xFFFFD54F),
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    watchOnly -> stringResource(R.string.alarm_subtitle_watch)
                    watchFallback -> stringResource(R.string.alarm_subtitle_watch_fallback)
                    silentMode -> stringResource(R.string.alarm_subtitle_silent)
                    else -> stringResource(R.string.alarm_subtitle)
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (alarmModeName == "TIME") {
                    stringResource(R.string.alarm_mode_time_short)
                } else {
                    stringResource(R.string.alarm_mode_distance_short)
                },
                color = Color(0xFFFFD54F),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Transit design system (Oct 2026): swipe-to-stop slider —
            // deliberate 80% drag prevents accidental dismissals.
            com.app.wasalna.ui.components.SwipeToStopSlider(
                onStop = onDismiss,
                label = stringResource(R.string.alarm_swipe_to_stop),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.alarm_snooze),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.alarm_dismiss_hint),
                color = Color(0xFFB0B0B0),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Three-dot gradual phase indicator — filled = reached, calm colors. */
@Composable
private fun PhaseIndicator(currentPhase: GradualPhase) {
    val steps = listOf(
        GradualPhase.VIBRATION_ONLY to stringResource(R.string.alarm_phase_vibration),
        GradualPhase.RAMPING to stringResource(R.string.alarm_phase_ramp),
        GradualPhase.FULL to stringResource(R.string.alarm_phase_full),
    )
    val order = listOf(
        GradualPhase.VIBRATION_ONLY,
        GradualPhase.RAMPING,
        GradualPhase.FULL,
    )
    val currentIndex = order.indexOf(currentPhase).coerceAtLeast(0)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                steps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentIndex) 16.dp else 12.dp)
                            .background(
                                color = if (index <= currentIndex) {
                                    Color(0xFFFFD54F)
                                } else {
                                    Color.White.copy(alpha = 0.30f)
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
            Text(
                text = steps[currentIndex.coerceIn(steps.indices)].second,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 24.dp),
            )
        }
    }
}
