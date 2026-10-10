package com.app.wasalna.data.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Phase 10.10: phone-side Wear OS Data Layer sender (Google Play Services
 * Wearable MessageClient/CapabilityClient — small API, no Wear UI here).
 *
 * All calls are main-safe (switch to [Dispatchers.IO] internally) and
 * never throw — failures surface as `false` / [WatchStatus] so callers
 * (TrackingService, Settings) can fall back to the normal phone alarm.
 * Null-safety: every nullable resolves with `?:` / `?.let`, no assertions.
 */
object WatchAlertSender {

    private const val TAG = "WatchAlertSender"

    enum class WatchStatus {
        CHECKING,
        CONNECTED,
        APP_MISSING,
        NO_WATCH,
        UNKNOWN,
    }

    /**
     * Node discovery via CapabilityClient (FILTER_REACHABLE for the
     * companion capability), falling back to NodeClient when the
     * capability query itself fails.
     */
    suspend fun checkStatus(context: Context): WatchStatus = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        runCatching {
            val capability = Wearable.getCapabilityClient(appContext)
                .getCapability(
                    WatchAlertContract.CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE,
                ).await()
            val companionNodes = capability.nodes.filter { it.isNearby }
            if (companionNodes.isNotEmpty()) {
                Log.i(TAG, "checkStatus: companion reachable (${companionNodes.size})")
                return@withContext WatchStatus.CONNECTED
            }
            val connected = runCatching {
                Wearable.getNodeClient(appContext).connectedNodes.await()
            }.getOrDefault(emptyList())
            if (connected.isEmpty()) {
                WatchStatus.NO_WATCH
            } else {
                WatchStatus.APP_MISSING
            }
        }.getOrElse { error ->
            Log.w(TAG, "checkStatus failed: ${error.message}")
            WatchStatus.UNKNOWN
        }
    }

    suspend fun isCompanionReachable(context: Context): Boolean = withContext(Dispatchers.IO) {
        checkStatus(context) == WatchStatus.CONNECTED
    }

    private suspend fun reachableNodeIds(context: Context): List<String> =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext ?: context
            runCatching {
                Wearable.getCapabilityClient(appContext)
                    .getCapability(
                        WatchAlertContract.CAPABILITY,
                        CapabilityClient.FILTER_REACHABLE,
                    ).await()
                    .nodes.filter { it.isNearby }.map { it.id }
            }.getOrDefault(emptyList())
        }

    private suspend fun sendToReachable(
        context: Context,
        path: String,
        payload: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        val nodeIds = reachableNodeIds(appContext)
        if (nodeIds.isEmpty()) {
            Log.w(TAG, "send $path: no reachable companion node")
            return@withContext false
        }
        var delivered = false
        for (nodeId in nodeIds) {
            runCatching {
                Wearable.getMessageClient(appContext)
                    .sendMessage(nodeId, path, payload).await()
                delivered = true
                Log.i(TAG, "send $path → node delivered")
            }.onFailure { error ->
                Log.w(TAG, "send $path → node failed: ${error.message}")
            }
        }
        delivered
    }

    /** Destination alert → watch vibrates/rings. Returns true if ≥1 watch got it. */
    suspend fun sendAlert(context: Context, destinationName: String): Boolean {
        val bytes = runCatching {
            destinationName.toByteArray(Charsets.UTF_8)
                .take(WatchAlertContract.MAX_NAME_BYTES).toByteArray()
        }.getOrDefault(ByteArray(0))
        return sendToReachable(context, WatchAlertContract.PATH_ALERT, bytes)
    }

    /** Short test buzz from the Settings "test vibration" button. */
    suspend fun sendTestVibration(context: Context): Boolean =
        sendToReachable(context, WatchAlertContract.PATH_TEST, ByteArray(0))

    /**
     * Phone-side dismissal propagation: dismissing the phone alarm also
     * quiets the watch. Fire-and-forget — result ignored by design.
     */
    suspend fun sendDismiss(context: Context) {
        sendToReachable(context, WatchAlertContract.PATH_DISMISS, ByteArray(0))
    }
}
