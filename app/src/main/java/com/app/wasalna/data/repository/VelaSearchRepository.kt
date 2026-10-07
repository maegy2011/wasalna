package com.app.wasalna.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10-2: Vela-style keyless autocomplete.
 * Uses Google's public browser suggest endpoint — NO API key, NO billing.
 * Fast string suggestions; coordinates are resolved on click via
 * NominatimRepository (two-step flow in MapViewModel).
 */
@Singleton
class VelaSearchRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Returns raw suggestion strings. Response is a JSON array whose
     * index 1 holds the suggestion list. Empty list on any failure —
     * never throws (runs on Dispatchers.IO).
     */
    suspend fun suggest(query: String): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = SUGGEST_URL + "?client=chrome&q=${encode(query)}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", NominatimRepository.USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "suggest HTTP ${response.code}")
                        return@runCatching emptyList<String>()
                    }
                    val body = response.body?.string() ?: return@runCatching emptyList<String>()
                    val root = JSONArray(body)
                    if (root.length() < 2) return@runCatching emptyList<String>()
                    val arr = root.getJSONArray(1)
                    List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
                }
            }.onFailure {
                Log.e(TAG, "suggest failed: ${it.message}")
            }.getOrDefault(emptyList())
        }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val TAG = "VelaSearch"
        const val SUGGEST_URL = "https://suggestqueries.google.com/complete/search"
    }
}
