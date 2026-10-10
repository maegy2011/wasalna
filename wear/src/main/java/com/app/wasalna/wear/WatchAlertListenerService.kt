package com.app.wasalna.wear

import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phase 10.10: watch-side Data Layer listener.
 *
 * - [WatchAlertContract.PATH_ALERT] → launches [WatchAlarmActivity] with
 *   the destination name (the activity owns the looping vibration).
 * - [WatchAlertContract.PATH_TEST] → short confirmation buzz, no screen.
 * - [WatchAlertContract.PATH_DISMISS] → phone dismissed → finish the
 *   watch screen too (dismiss from either side stops everything).
 */
class WatchAlertListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WatchAlertContract.PATH_ALERT -> {
                val name = runCatching {
                    event.data.decodeToString().take(MAX_NAME_CHARS)
                }.getOrDefault("")
                Log.i(TAG, "alert received: $name")
                runCatching {
                    startActivity(
                        Intent(this, WatchAlarmActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(WatchAlarmActivity.EXTRA_DESTINATION_NAME, name)
                        },
                    )
                }.onFailure { Log.w(TAG, "launch failed: ${it.message}") }
            }
            WatchAlertContract.PATH_TEST -> {
                Log.i(TAG, "test buzz received")
                scope.launch { buzzOnce() }
            }
            WatchAlertContract.PATH_DISMISS -> {
                Log.i(TAG, "phone dismissed — quieting watch")
                scope.launch {
                    runCatching {
                        sendBroadcast(
                            Intent(WatchAlertContract.ACTION_DISMISS_LOCAL)
                                .setPackage(packageName),
                        )
                    }
                }
            }
        }
    }

    private fun buzzOnce() {
        runCatching {
            val vibrator = systemVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(TEST_PATTERN, -1),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(TEST_PATTERN, -1)
            }
        }.onFailure { Log.w(TAG, "buzz failed: ${it.message}") }
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WatchAlertListener"
        private const val MAX_NAME_CHARS = 120
        private val TEST_PATTERN = longArrayOf(0, 400, 200, 400, 200, 800)
    }
}
