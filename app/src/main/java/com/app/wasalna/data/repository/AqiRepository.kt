package com.app.wasalna.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WAQI station feed (Oct 2026, AQI layer upgrade): numeric AQI at a
 * point via the user's own token (BYOK — same key as the raster tiles).
 * `GET https://api.waqicn.org/feed/geo:{lat};{lng}/?token={key}` →
 * `data.aqi` (int, or "-" when the nearest station is silent → null).
 * Dispatchers.IO, never throws — null on any failure.
 */
@Singleton
class AqiRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAqi(lat: Double, lng: Double, token: String): Int? =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext null
            runCatching {
                val url = "https://api.waqicn.org/feed/geo:$lat;$lng/?token=$token"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", NominatimRepository.USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val data = JSONObject(body).optJSONObject("data")
                        ?: return@runCatching null
                    when (val aqi = data.opt("aqi")) {
                        is Int -> aqi.takeIf { it >= 0 }
                        is Number -> aqi.toInt().takeIf { it >= 0 }
                        is String -> aqi.toIntOrNull()?.takeIf { it >= 0 }
                        else -> null
                    }
                }
            }.onFailure {
                Log.e(TAG, "WAQI feed failed: ${it.message}")
            }.getOrNull()
        }

    companion object {
        private const val TAG = "AqiRepository"
    }
}
