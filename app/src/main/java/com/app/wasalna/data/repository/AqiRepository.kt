package com.app.wasalna.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WAQI station feed (Oct 2026, AQI layer upgrade): numeric AQI at a
 * point via the user's own token (BYOK — same key as the raster tiles).
 * `GET https://api.waqicn.org/feed/geo:{lat};{lng}/?token={key}` →
 * `data.aqi` (int, or "-" when the nearest station is silent → null).
 * Dispatchers.IO, never throws — null on any failure.
 *
 * A1 offline cache: every successful fetch is journaled to a tiny JSON
 * file keyed by ~5 km cell; [cachedAqi] serves values younger than
 * [CACHE_TTL_MS] (6 h) with zero network so the destination chip keeps
 * working in offline mode (stale-flagged in the UI).
 */
@Singleton
class AqiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

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
            }.getOrNull()?.also { value ->
                runCatching { writeCache(lat, lng, value) }
            }
        }

    /** Cached AQI for the cell containing [lat]/[lng], or null when absent/stale. */
    fun cachedAqi(lat: Double, lng: Double): Int? = runCatching {
        val file = cacheFile() ?: return null
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val cell = root.optJSONObject(cellKey(lat, lng)) ?: return null
        val at = cell.optLong("at", 0L)
        if (System.currentTimeMillis() - at > CACHE_TTL_MS) return null
        cell.optInt("aqi", -1).takeIf { it >= 0 }
    }.getOrNull()

    /** True when a (possibly stale) entry exists — lets the UI flag staleness. */
    fun hasCachedAqi(lat: Double, lng: Double): Boolean = runCatching {
        val file = cacheFile() ?: return false
        if (!file.exists()) return false
        JSONObject(file.readText()).has(cellKey(lat, lng))
    }.getOrDefault(false)

    private fun cellKey(lat: Double, lng: Double): String {
        // ~5 km cells (0.05°): station-level AQI barely varies inside one.
        val la = (lat * 20).toInt()
        val ln = (lng * 20).toInt()
        return "$la:$ln"
    }

    private fun cacheFile(): File? = runCatching {
        File(context.cacheDir, "aqi_cache.json")
    }.getOrNull()

    private fun writeCache(lat: Double, lng: Double, aqi: Int) {
        val file = cacheFile() ?: return
        val root = runCatching {
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        }.getOrDefault(JSONObject())
        root.put(
            cellKey(lat, lng),
            JSONObject().put("aqi", aqi).put("at", System.currentTimeMillis()),
        )
        // Cap growth: keep the 200 most recent cells (one file, tiny).
        if (root.length() > MAX_CELLS) {
            val keys = root.keys().asSequence().toList()
            keys.drop(MAX_CELLS).forEach { root.remove(it) }
        }
        file.writeText(root.toString())
    }

    companion object {
        private const val TAG = "AqiRepository"
        /** 6 h TTL (user-confirmed): WAQI updates hourly; 6 h stays useful. */
        const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
        private const val MAX_CELLS = 200
    }
}
