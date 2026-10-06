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
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 6: full-screen alarm fired when TrackingService reaches the radius.
 * Phase 9 (redone): silent alarm (premium) — headset → STREAM_MUSIC/USAGE_MEDIA
 * to earphones only; no headset → vibrate-only pattern, no loud speaker.
 *
 * Extras: [EXTRA_SILENT] + [EXTRA_PREMIUM] (sent by TrackingService).
 * Silent applies only when silent && premium (premium gate).
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wantSilent = intent.getBooleanExtra(EXTRA_SILENT, false)
        val isPremium = intent.getBooleanExtra(EXTRA_PREMIUM, false)
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
            maxAlarmVolume()
            startAlarmSound()
        }

        setContent {
            AlarmScreen(
                silentMode = silentMode,
                onLongPressStop = { stopAlarm() },
            )
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

    private fun startSilentEarphoneSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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
        startVibration(longArrayOf(0, 500, 500), 0)
    }

    private fun startVibrateOnly() {
        startVibration(longArrayOf(0, 1000, 500), 0)
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
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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

    private fun stopAlarm() {
        if (stopped) return
        stopped = true
        Log.i(TAG, "stopAlarm: dismissed silent=$silentMode")
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
