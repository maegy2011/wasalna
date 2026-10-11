package com.app.wasalna.utils

import java.io.IOException
import java.net.UnknownHostException

/**
 * Phase 10.4.3.2: offline-mode guards. Vector tiles render from downloaded
 * regions without internet, but Photon/OSRM need the network — these helpers
 * distinguish "no connection" from other failures so the UI can say so.
 */
object NetworkGuards {

    const val OFFLINE_MESSAGE =
        "Offline Mode: Map is available, but Search/Routing require internet."

    fun isOfflineCause(t: Throwable): Boolean =
        t is UnknownHostException || t is IOException

    /** True when any validated network is up (Oct 2026, gazetteer use). */
    fun isOnline(context: android.content.Context): Boolean = runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}
