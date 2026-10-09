package com.app.wasalna.data.routing

/**
 * Phase 10.5.x — Offline GraphHopper rewrite Step 1: per-region download state.
 * One StateFlow per region id; UI collects and renders directly.
 */
sealed interface OfflineGraphState {
    data object NotDownloaded : OfflineGraphState
    data class Downloading(val progressPct: Int) : OfflineGraphState
    data object Extracting : OfflineGraphState
    data object Ready : OfflineGraphState
    data class Error(val message: String) : OfflineGraphState
}
