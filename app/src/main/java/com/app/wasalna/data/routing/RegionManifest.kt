package com.app.wasalna.data.routing

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.app.wasalna.utils.AppLinks
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegionManifestRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _manifest = MutableLiveData<RegionManifest?>()
    val manifest: LiveData<RegionManifest?> = _manifest

    private val manifestFile: File =
        File(context.cacheDir, "region_manifest.json").also { it.parentFile?.mkdirs() }

    private val gson = Gson()
    private val manifestType = object : com.google.gson.reflect.TypeToken<RegionManifest>() {}.type

    init {
        loadCachedManifest()
    }

    private fun loadCachedManifest() {
        if (manifestFile.exists()) {
            try {
                val json = manifestFile.readText()
                val parsed = gson.fromJson<RegionManifest>(json, manifestType)
                _manifest.value = parsed
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load cached manifest: ${e.message}")
            }
        }
    }

    /** Fetches manifest from server; caches for 7 days. */
    suspend fun fetchManifest(): RegionManifest? = withContext(Dispatchers.IO) {
        val cacheAgeMs = System.currentTimeMillis() - manifestFile.lastModified()
        if (cacheAgeMs < 7 * 24 * 60 * 60 * 1000L && manifestFile.exists()) {
            _manifest.value?.let { return@withContext it }
        }

        return@withContext runCatching {
            val url = "${AppLinks.GRAPH_BASE_URL}/manifest.json"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Manifest fetch HTTP ${response.code}")
                    return@runCatching null
                }
                val body = response.body?.string() ?: return@runCatching null
                val parsed = gson.fromJson<RegionManifest>(body, manifestType)
                manifestFile.writeText(body)
                _manifest.value = parsed
                parsed
            }
        }.onFailure {
            Log.w(TAG, "Manifest fetch failed: ${it.message}")
        }.getOrNull()
    }

    fun getManifest(): RegionManifest? = _manifest.value

    companion object {
        private const val TAG = "RegionManifestRepo"
    }
}

data class RegionManifest(
    val version: Int,
    val regions: List<RoutingRegion>,
    val gazetteerBaseUrl: String,
) {
    fun regionById(id: String): RoutingRegion? = regions.firstOrNull { it.id == id }
}

data class RoutingRegion(
    val id: String,
    val name: String,
    val url: String,
    val sizeMb: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    /** R5: expected SHA-256 of the .ghz (null/absent = skip check, old manifests). */
    val sha256: String? = null,
    /** R6: server-side graph build number (null/absent = unknown, no badge). */
    val version: Int? = null,
) {
    fun contains(lat: Double, lng: Double): Boolean =
        lat in minLat..maxLat && lng in minLng..maxLng

    fun coversRoute(sLat: Double, sLng: Double, dLat: Double, dLng: Double): Boolean =
        contains(sLat, sLng) && contains(dLat, dLng)

    fun intersects(south: Double, west: Double, north: Double, east: Double): Boolean =
        south <= maxLat && north >= minLat && west <= maxLng && east >= minLng
}