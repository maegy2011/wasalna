package com.app.wasalna

import android.app.Application
import android.os.Build
import android.util.Log
import com.app.wasalna.data.local.datastore.SettingsDataStore
import com.app.wasalna.utils.AppLocaleHelper
import com.app.wasalna.utils.CrashReporter
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.maplibre.android.MapLibre
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WasalnaApp : Application() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate() {
        super.onCreate()
        // Phase 10.8.0a: local-only crash log (no SDK) — first so every
        // later init is covered.
        runCatching { CrashReporter.install(this) }
        // In-app language: on API <33 the legacy locale override lives only
        // in process memory, so re-apply the persisted choice before the
        // first composition. One tiny prefs read via runBlocking; on API 33+
        // LocaleManager persists automatically — nothing to do here.
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val code = runBlocking { settingsDataStore.appLanguageCode.first() }
                if (code.isNotBlank()) {
                    AppLocaleHelper.applyLegacy(this, code)
                }
            }
        }
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
        // Voice announcements (Oct 2026): system TTS engine, saved voice.
        runCatching {
            val savedVoice = runBlocking {
                settingsDataStore.voiceName.first()
            }
            com.app.wasalna.data.speech.SpeechAnnouncer.init(this, savedVoice)
            Log.i(TAG, "SpeechAnnouncer initialized")
        }.onFailure { Log.w(TAG, "SpeechAnnouncer init failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "WasalnaApp"
    }
}
