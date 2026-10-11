package com.app.wasalna.data.search

import android.content.Context
import android.util.Log
import com.app.wasalna.data.routing.RoutingGraphManager
import com.app.wasalna.data.routing.RoutingRegion
import com.app.wasalna.utils.AppLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline street/place search (Oct 2026).
 *
 * Photon/Nominatim need network — when the device is offline (or the API
 * fails) search used to die with "map_offline_search". This gazetteer
 * searches compact per-region name indexes instead:
 * `downloads/gazetteer/<region>.json.gz` — streets, districts, stations,
 * mosques, hospitals, schools, fuel, malls, parks and other named OSM
 * features, baked server-side from the Egypt extract.
 *
 * - Files are tiny vs graphs (~1–3 MB gz) so they fetch on ANY network,
 *   lazily on first offline search (or alongside their graph region).
 * - Arabic-aware matching: diacritics/tatweel stripped, alef variants
 *   unified, taa-marbuta/haa unified — "شارع" matches "شَارِع".
 * - Ranking: prefix > word-prefix > substring; POI type boosts stations
 *   and hospitals (trip-critical) over shops.
 * - Threading: load + search on Dispatchers.Default; in-memory index,
 *   ~100–200k entries ≈ a few MB. Never throws — empty list on failure.
 */
@Singleton
class OfflineGazetteer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private val index = mutableListOf<GazEntry>()
    private val loadedRegions = mutableSetOf<String>()

    data class GazEntry(
        val name: String,
        val nameNorm: String,
        val lat: Double,
        val lng: Double,
        /** 0 street · 1 district/place · 2 station · 3 service(med Beach) · 4 poi */
        val kind: Int,
    )

    data class GazHit(
        val name: String,
        val lat: Double,
        val lng: Double,
    )

    /** Normalized Arabic for search: strips tashkeel/tatweel, unifies alef/hamza + taa/haa. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = when (ch) {
                '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650',
                '\u0651', '\u0652', '\u0653', '\u0654', '\u0655', '\u0656',
                '\u0657', '\u0658', '\u0659', '\u065A', '\u0640' -> continue
                '\u0623', '\u0625', '\u0622' -> '\u0627' // alef variants → ا
                '\u0624' -> '\u0648' // ؤ → و
                '\u0626' -> '\u064A' // ئ → ي
                '\u0629' -> '\u0647' // ة → ه
                '\u0649' -> '\u064A' // ى → ي
                else -> ch.lowercaseChar()
            }
            sb.append(c)
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private fun gazDir(): File =
        File(context.getExternalFilesDir(null), "offline_gazetteer").apply { mkdirs() }

    private fun gazFile(regionId: String): File = File(gazDir(), "$regionId.json")

    fun isLoaded(regionId: String): Boolean = loadedRegions.contains(regionId)

    fun anyLoaded(): Boolean = loadedRegions.isNotEmpty()

    /**
     * Loads a region's index into memory (cached; second call is free).
     * Downloads first when missing — small file, any network allowed.
     */
    suspend fun ensureRegion(region: RoutingRegion): Boolean = mutex.withLock {
        if (loadedRegions.contains(region.id)) return true
        val file = gazFile(region.id)
        if (!file.exists() || file.length() < 1024) {
            if (!fetchRegion(region)) return false
        }
        return loadFile(region.id, file)
    }

    /** Preloads every INSTALLED graph region that already has a file (no network). */
    suspend fun preloadInstalled(manager: RoutingGraphManager) = mutex.withLock {
        for (id in manager.installedRegionIds()) {
            if (loadedRegions.contains(id)) continue
            val f = gazFile(id)
            if (f.exists() && f.length() >= 1024) {
                loadFile(id, f)
            }
        }
    }

    /**
     * Best-effort fetch for the region containing [lat]/[lng] — used by
     * search when nothing is loaded yet (even on metered links: files
     * are ~100× smaller than graphs).
     */
    suspend fun ensureForLocation(
        manager: RoutingGraphManager,
        lat: Double,
        lng: Double,
    ): Boolean {
        val region = RoutingGraphManager.REGIONS.firstOrNull {
            it.contains(lat, lng)
        } ?: return anyLoaded()
        return ensureRegion(region)
    }

    /**
     * Prefix-first ranked search over the loaded index. [limit] caps UI
     * rows; matching is substring on the normalized form so "تحرير"
     * finds "ميدان التحرير" and "tahrir" finds "Tahrir Square".
     */
    suspend fun search(query: String, limit: Int = 8): List<GazHit> =
        withContext(Dispatchers.Default) {
            val q = normalize(query)
            if (q.length < 2) return@withContext emptyList()
            val snap = mutex.withLock { index.toList() }
            val scored = ArrayList<Pair<Int, GazEntry>>(32)
            var scanned = 0
            for (e in snap) {
                if (scored.size >= 400 || scanned >= 60_000) break
                scanned++
                val score = matchScore(e.nameNorm, q, e.kind) ?: continue
                scored.add(score to e)
            }
            scored.sortBy { it.first }
            scored.take(limit).map { GazHit(it.second.name, it.second.lat, it.second.lng) }
        }

    /**
     * Score (lower = better): 0 exact · 1 name-prefix · 2 word-prefix ·
     * 3 substring. Kind boost: stations/services sort before plain POIs
     * within the same tier (arrival-critical first).
     */
    private fun matchScore(norm: String, q: String, kind: Int): Int? {
        val base = when {
            norm == q -> 0
            norm.startsWith(q) -> 1
            norm.split(' ').any { it.startsWith(q) } -> 2
            norm.contains(q) -> 3
            else -> return null
        }
        val boost = when (kind) {
            2, 3 -> 0
            1 -> 1
            else -> 2
        }
        return base * 10 + boost
    }

    private suspend fun fetchRegion(region: RoutingRegion): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${AppLinks.GAZETTEER_BASE_URL}/${region.id}.json.gz"
                client.newCall(Request.Builder().url(url).build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.i(TAG, "gazetteer ${region.id}: HTTP ${resp.code}")
                            return@withContext false
                        }
                        val body = resp.body?.byteStream() ?: return@withContext false
                        val tmp = File(gazDir(), "${region.id}.json.tmp")
                        body.use { ins ->
                            GZIPInputStream(ins).use { gz ->
                                tmp.outputStream().use { out -> gz.copyTo(out) }
                            }
                        }
                        check(tmp.length() > 1024) { "implausibly small" }
                        tmp.renameTo(gazFile(region.id))
                        Log.i(TAG, "gazetteer ${region.id} fetched")
                        true
                    }
            }.onFailure {
                Log.w(TAG, "gazetteer ${region.id} fetch failed: ${it.message}")
            }.getOrDefault(false)
        }

    /** Caller must hold [mutex]. */
    private fun loadFile(regionId: String, file: File): Boolean {
        return runCatching {
            val arr = JSONArray(file.readText())
            var added = 0
            // No continue/break inside runCatching (inline lambda) —
            // filter with plain ifs so the loop stays a simple loop.
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("n")
                val lat = o.optDouble("la", Double.NaN)
                val lng = o.optDouble("ln", Double.NaN)
                if (name.isBlank() || lat.isNaN() || lng.isNaN()) {
                    // skip bad row
                } else {
                    index.add(
                        GazEntry(
                            name = name,
                            nameNorm = normalize(name),
                            lat = lat,
                            lng = lng,
                            kind = o.optInt("t", 4),
                        ),
                    )
                    added++
                }
            }
            loadedRegions.add(regionId)
            Log.i(TAG, "gazetteer $regionId: $added entries (total ${index.size})")
            added > 0
        }.onFailure {
            Log.w(TAG, "gazetteer $regionId parse failed: ${it.message}")
            runCatching { file.delete() }
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "OfflineGazetteer"
    }
}
