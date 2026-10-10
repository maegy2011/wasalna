package com.app.wasalna.ui.screens.alarm

import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    // Phase 10.9.x: owns player + vibrator + volume while the gradual
    // escalation runs; null = legacy instant path. stop() halts ALL
    // sound + vibration synchronously (no leaks, no lingering coroutine).
    private var gradualController: GradualAlarmController? = null

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
        premiumMode = isPremium
        silentMode = wantSilent && isPremium
        Log.i(TAG, "onCreate: alarm firing silent=$silentMode")

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

        if (silentMode) {
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

        // Phase 10.4.3.3: wrist-reachable alarm (Wear OS mirrors actions).
        postAlarmNotification()

        setContent {
            AlarmScreen(
                silentMode = silentMode,
                onLongPressStop = { stopAlarm() },
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
     * Phase 10.4.3.1: premium custom ringtone wins; every failure (no URI,
     * unreadable file, non-premium) falls back to the system alarm tone.
     */
    private fun resolveAlarmUri(isPremium: Boolean): android.net.Uri? {
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (!isPremium) return fallback
        return runCatching {
            val custom = runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    SettingsDataStore(this@AlarmActivity).customRingtoneUri.first()
                }
            }.orEmpty()
            if (custom.isBlank()) return fallback
            val uri = android.net.Uri.parse(custom)
            // Verify the file is actually readable before trusting it.
            contentResolver.openInputStream(uri)?.close()
            uri
        }.onFailure {
            Log.w(TAG, "custom ringtone unreadable, using default: ${it.message}")
        }.getOrNull() ?: fallback
    }

    private fun startSilentEarphoneSound() {
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
    private fun readGradualConfig(): GradualAlarmConfig? {
        return runCatching {
            runBlocking(Dispatchers.IO) {
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
    private fun resolveGradualUri(): Uri? {
        runCatching {
            val custom = runBlocking(Dispatchers.IO) {
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

    private fun startGradualAlarm(config: GradualAlarmConfig) {
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

    private fun startAlarmSound() {
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
            val notification = androidx.core.app.NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle("⏰ Wasalna — destination reached!")
                .setContentText("Tap to open, or stop from your watch")
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
    private fun silenceAlarm() {
        if (stopped) return
        Log.i(TAG, "silenceAlarm: sound off, vibration continues")
        // Phase 10.9.x: gradual path keeps vibrating, kills its own player.
        gradualController?.silenceSound()
        releasePlayer()
        cancelAlarmNotification()
    }

    private fun stopAlarm() {
        if (stopped) return
        stopped = true
        Log.i(TAG, "stopAlarm: dismissed silent=$silentMode")
        cancelAlarmNotification()
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

        // Phase 10.4.3.3: wrist actions (WearableExtender targets).
        const val ACTION_DISMISS_ALARM = "com.app.wasalna.action.DISMISS_ALARM"
        const val ACTION_SILENCE_ALARM = "com.app.wasalna.action.SILENCE_ALARM"
        const val ALARM_CHANNEL_ID = "wasalna_alarm"
        const val ALARM_NOTIF_ID = 2001
        private const val REQ_WEAR_STOP = 21
        private const val REQ_WEAR_SILENCE = 22
        private const val REQ_WEAR_CONTENT = 23

        /**
         * Phase 10.4.3.3: violent repeating waveform — 1.2 s buzz /
         * 0.4 s gap, looping from index 0 until explicitly cancelled.
         */
        val AGGRESSIVE_PATTERN = longArrayOf(0, 1200, 400, 1200, 400)
    }
}

@Composable
private fun AlarmScreen(
    silentMode: Boolean,
    onLongPressStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0505))
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPressStop() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color(0xFFD32F2F), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (silentMode) Icons.Filled.Headset else Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.alarm_title),
                color = Color(0xFFFF5252),
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (silentMode) {
                    stringResource(R.string.alarm_subtitle_silent)
                } else {
                    stringResource(R.string.alarm_subtitle)
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = stringResource(R.string.alarm_dismiss_hint),
                color = Color(0xFFB0B0B0),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
