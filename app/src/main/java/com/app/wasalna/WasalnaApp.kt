package com.app.wasalna

import android.app.Application
import android.util.Log
import com.app.wasalna.utils.CrashReporter
import com.google.android.gms.ads.MobileAds
import org.maplibre.android.MapLibre
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WasalnaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Phase 10.8.0a: local-only crash log (no SDK) — first so every
        // later init is covered.
        runCatching { CrashReporter.install(this) }
        // Phase 10-1: MapLibre global init (required before any MapView).
        try {
            MapLibre.getInstance(this)
            Log.i(TAG, "MapLibre initialized")
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre init failed: ${e.message}")
        }
        // Phase 8: initialize AdMob once (test App ID in manifest).
        try {
            MobileAds.initialize(this) {}
            Log.i(TAG, "MobileAds initialized")
        } catch (e: Exception) {
            Log.e(TAG, "MobileAds init failed: ${e.message}")
        }
        // 100% MapLibre — no Google Places / Maps SDK in use.
    }

    companion object {
        private const val TAG = "WasalnaApp"
    }
}
