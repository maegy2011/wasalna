package com.app.wasalna.ui.screens.alarm

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 6: full-screen alarm fired when [com.app.wasalna.data.service.TrackingService]
 * reaches the destination radius.
 *
 * - Shows over the lock screen, turns the screen on, max brightness.
 * - Maxes STREAM_ALARM volume (restored on stop), loops the alarm ringtone.
 * - Dismiss via long-press anywhere; lifecycle-safe cleanup in onDestroy.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var savedAlarmVolume: Int = -1
    private var stopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: alarm firing")

        // Show over lock screen + turn screen on.
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
        // Dismiss keyguard where allowed.
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km?.requestDismissKeyguard(this, null)
        }
        // Full brightness.
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        maxAlarmVolume()
        startAlarmSound()

        setContent {
            AlarmScreen(onLongPressStop = { stopAlarm() })
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
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (alarmUri == null) {
            Log.e(TAG, "startAlarmSound: no ringtone URI available")
            return
        }
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "startAlarmSound: looping $alarmUri")
        } catch (e: Exception) {
            Log.e(TAG, "startAlarmSound failed: ${e.message}")
            releasePlayer()
        }
    }

    private fun stopAlarm() {
        if (stopped) return
        stopped = true
        Log.i(TAG, "stopAlarm: user dismissed")
        releasePlayer()
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
        if (savedAlarmVolume >= 0) {
            try {
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    savedAlarmVolume,
                    0,
                )
                Log.i(TAG, "restoreVolume: restored to $savedAlarmVolume")
            } catch (e: SecurityException) {
                Log.w(TAG, "restoreVolume denied: ${e.message}")
            }
        }
        savedAlarmVolume = -1
    }

    override fun onDestroy() {
        releasePlayer()
        restoreVolume()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmActivity"
    }
}

@Composable
private fun AlarmScreen(onLongPressStop: () -> Unit) {
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
                    imageVector = Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Wake Up!",
                color = Color(0xFFFF5252),
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Destination Approaching",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Long-press anywhere to stop",
                color = Color(0xFFB0B0B0),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
