package com.app.wasalna

import android.app.Application
import android.util.Log
import com.app.wasalna.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WasalnaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Phase 8: initialize AdMob once (test App ID in manifest).
        try {
            MobileAds.initialize(this) {}
            Log.i(TAG, "MobileAds initialized")
        } catch (e: Exception) {
            Log.e(TAG, "MobileAds init failed: ${e.message}")
        }
        // Phase 4: initialize Places SDK once. Empty/placeholder key is tolerated —
        // map + autocomplete simply stay idle until a real key is supplied.
        try {
            val key = BuildConfig.MAPS_API_KEY.trim()
            if (key.isNotEmpty() && key != "YOUR_API_KEY_HERE" && !Places.isInitialized()) {
                Places.initialize(applicationContext, key)
                Log.i(TAG, "Places initialized")
            } else {
                Log.w(TAG, "Places init skipped: no real MAPS_API_KEY (placeholder/empty)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Places init failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WasalnaApp"
    }
}
