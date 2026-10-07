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
}
