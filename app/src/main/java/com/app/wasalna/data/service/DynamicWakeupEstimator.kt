package com.app.wasalna.data.service

/**
 * DYNAMIC WAKE-UP (التنبيه المرتبط بالزحام) — TIME-mode ETA engine.
 *
 * ETA formula:
 *   etaMinutes = (remainingMeters / smoothedSpeedMps) / 60
 *
 * - [remainingMeters] comes from the caller: cached on-device route
 *   distance when fresh, else GPS straight-line distanceTo() — the same
 *   fallback philosophy as the offline engine (never miss the stop).
 * - Speed is an exponential moving average (EMA, factor [emaAlpha]) over
 *   GPS fixes so one jumpy fix cannot swing the ETA.
 * - Fixes are ignored (previous smoothed value kept) when:
 *   stale by device clock (> [maxFixAgeMs]), low accuracy
 *   (> [maxAccuracyM]), missing/negative speed, or standstill (0 m/s —
 *   holding the last speed avoids dragging the EMA to zero in a jam;
 *   standstill still counts down via the distance fallback in
 *   TrackingService, guarded by [minSpeedMps] floor here).
 * - [estimateEtaMinutes] returns null when speed data is invalid (never
 *   smoothed, or no usable fix for > [staleTimeoutMs]): the caller MUST
 *   degrade to the DISTANCE trigger (see TrackingService).
 *
 * Pure Kotlin, no Android deps — safe to call from any dispatcher, but
 * TrackingService always calls it from Dispatchers.Default (never Main).
 * Thread-safe via [lock].
 */
class DynamicWakeupEstimator(
    private val emaAlpha: Float = 0.3f,
    private val minSpeedMps: Float = MIN_SPEED_MPS,
    private val staleTimeoutMs: Long = STALE_TIMEOUT_MS,
    private val maxFixAgeMs: Long = MAX_FIX_AGE_MS,
    private val maxAccuracyM: Float = MAX_ACCURACY_M,
) {
    private val lock = Any()
    private var smoothedMps: Float? = null
    private var lastUsableFixMs: Long = -1L

    /**
     * Feeds one GPS fix into the EMA. Returns the current smoothed speed
     * (m/s), or null when no usable fix has ever been seen.
     */
    fun onFix(
        hasSpeed: Boolean,
        speedMps: Float,
        hasAccuracy: Boolean,
        accuracyM: Float,
        fixTimeMs: Long,
        nowMs: Long,
    ): Float? {
        if (fixTimeMs < nowMs - maxFixAgeMs) return current()
        if (hasAccuracy && accuracyM > maxAccuracyM) return current()
        if (!hasSpeed || speedMps.isNaN() || speedMps < 0f) return current()
        if (speedMps <= 0f) return current()
        synchronized(lock) {
            val prev = smoothedMps
            smoothedMps = if (prev == null) speedMps else emaAlpha * speedMps + (1f - emaAlpha) * prev
            lastUsableFixMs = nowMs
        }
        return current()
    }

    /**
     * Live ETA in whole minutes (≥ 1), or null when speed data is invalid
     * → caller degrades to the DISTANCE trigger so the stop is never missed.
     */
    fun estimateEtaMinutes(remainingMeters: Float, nowMs: Long): Int? {
        val speed: Float?
        val lastFix: Long
        synchronized(lock) {
            speed = smoothedMps
            lastFix = lastUsableFixMs
        }
        if (speed == null || speed <= 0f) return null
        if (lastFix < 0L || nowMs - lastFix > staleTimeoutMs) return null
        if (remainingMeters <= 0f) return 1
        val effective = maxOf(speed, minSpeedMps)
        return ((remainingMeters / effective) / 60f).toInt().coerceAtLeast(1)
    }

    fun reset() {
        synchronized(lock) {
            smoothedMps = null
            lastUsableFixMs = -1L
        }
    }

    private fun current(): Float? = synchronized(lock) { smoothedMps }

    companion object {
        /** EMA weight for each new fix (0.3 = smooth but responsive). */
        const val DEFAULT_ALPHA = 0.3f

        /**
         * Speed floor: below ~5.4 km/h the vehicle is crawling in a jam —
         * ETA still counts down instead of exploding to infinity.
         */
        const val MIN_SPEED_MPS = 1.5f

        /**
         * No usable fix for longer than this → speed data invalid →
         * TrackingService falls back to the distance threshold equivalent.
         */
        const val STALE_TIMEOUT_MS = 60_000L

        /** Fixes older than this (device clock) are ignored as stale. */
        const val MAX_FIX_AGE_MS = 30_000L

        /** Fixes worse than ±50 m accuracy are ignored as noisy. */
        const val MAX_ACCURACY_M = 50f
    }
}
