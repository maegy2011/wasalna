package com.app.wasalna.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.app.wasalna.wear.R
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Phase 10.10: watch alert screen — destination name + looping vibration +
 * a 48dp+ dismiss button. Dismissing here notifies the phone
 * ([WatchAlertContract.PATH_DISMISSED]) so the phone alarm stops too.
 * A phone-side dismissal ([WatchAlertContract.PATH_DISMISS] → local
 * broadcast) finishes this screen.
 */
class WatchAlarmActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null
    private var stopped = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dismissReceiver: BroadcastReceiver? = null
    private var messageListener: MessageClient.OnMessageReceivedListener? = null
    private var destinationName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destinationName = intent.getStringExtra(EXTRA_DESTINATION_NAME).orEmpty()
        Log.i(TAG, "watch alarm firing: $destinationName")

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        startLoopingVibration()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WatchAlertContract.ACTION_DISMISS_LOCAL) {
                    stopQuietly()
                }
            }
        }
        dismissReceiver = receiver
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(WatchAlertContract.ACTION_DISMISS_LOCAL),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        // Belt and braces: also finish on direct Data Layer dismissals
        // while this screen is up (paired add/remove — skill lifecycle rule).
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WatchAlertContract.PATH_DISMISS) {
                runOnUiThread { stopQuietly() }
            }
        }
        messageListener = listener
        runCatching {
            Wearable.getMessageClient(this).addListener(listener)
        }

        setContent {
            WatchAlarmScreen(
                destinationName = destinationName,
                onDismiss = { dismissFromWatch() },
            )
        }
    }

    private fun systemVibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private fun startLoopingVibration() {
        runCatching {
            val vib = systemVibrator()
            vibrator = vib
            if (vib == null) {
                Log.w(TAG, "no vibrator on this watch")
                return
            }
            if (!vib.hasVibrator()) {
                Log.w(TAG, "watch has no vibrator hardware")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(ALERT_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ALERT_PATTERN, 0)
            }
        }.onFailure { Log.w(TAG, "vibration failed: ${it.message}") }
    }

    /** User tapped dismiss on the watch → tell the phone, then finish. */
    private fun dismissFromWatch() {
        if (stopped) return
        stopped = true
        ioScope.launch {
            runCatching {
                val nodes = Wearable.getCapabilityClient(this@WatchAlarmActivity)
                    .getCapability(
                        WatchAlertContract.CAPABILITY,
                        CapabilityClient.FILTER_REACHABLE,
                    ).await()
                    .nodes.filter { it.isNearby }
                for (node in nodes) {
                    runCatching {
                        Wearable.getMessageClient(this@WatchAlarmActivity)
                            .sendMessage(
                                node.id,
                                WatchAlertContract.PATH_DISMISSED,
                                ByteArray(0),
                            ).await()
                    }
                }
            }.onFailure { Log.w(TAG, "dismiss notify failed: ${it.message}") }
            runOnUiThread { finish() }
        }
        runCatching { vibrator?.cancel() }
    }

    /** Phone dismissed → stop quietly without echoing back. */
    private fun stopQuietly() {
        if (stopped) return
        stopped = true
        runCatching { vibrator?.cancel() }
        vibrator = null
        finish()
    }

    override fun onDestroy() {
        runCatching {
            messageListener?.let {
                Wearable.getMessageClient(this).removeListener(it)
            }
        }
        messageListener = null
        runCatching { dismissReceiver?.let { unregisterReceiver(it) } }
        dismissReceiver = null
        ioScope.cancel()
        runCatching { vibrator?.cancel() }
        vibrator = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WatchAlarmActivity"
        const val EXTRA_DESTINATION_NAME = "extra_destination_name"

        /** Strong repeating waveform — 1 s buzz / 0.4 s gap until dismissed. */
        private val ALERT_PATTERN = longArrayOf(0, 1000, 400, 1000, 400)
    }
}

@Composable
private fun WatchAlarmScreen(
    destinationName: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1F14))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "⏰",
            fontSize = 40.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.watch_arrived),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (destinationName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = destinationName,
                color = Color(0xFF9CCC9C),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(text = stringResource(R.string.watch_dismiss))
        }
    }
}
