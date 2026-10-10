package com.app.wasalna.data.wear

import android.content.Intent
import android.util.Log
import com.app.wasalna.ui.screens.alarm.AlarmActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phase 10.10: receives watch-side dismissal ([WatchAlertContract.PATH_DISMISSED])
 * and forwards it as a package-scoped [AlarmActivity.ACTION_DISMISS_ALARM]
 * broadcast so dismissing from the watch stops the phone alarm too.
 * (Dismiss from either side stops everything; the reverse direction —
 * phone → watch — is [WatchAlertSender.sendDismiss].)
 */
class PhoneWatchDismissService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchAlertContract.PATH_DISMISSED) return
        Log.i(TAG, "watch dismissed the alarm — stopping phone alarm")
        scope.launch {
            runCatching {
                sendBroadcast(
                    Intent(AlarmActivity.ACTION_DISMISS_ALARM).setPackage(packageName),
                )
            }.onFailure { error ->
                Log.w(TAG, "dismiss broadcast failed: ${error.message}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneWatchDismiss"
    }
}
