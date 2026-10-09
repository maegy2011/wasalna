package com.app.wasalna.data.routing

import android.content.Context
import android.util.Log
import com.app.wasalna.utils.AppLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10.5.x Step 1 — COMPLETE REWRITE: robust offline graph download &
 * extract manager.
 *
 * - Store: `context.getExternalFilesDir(null)/offline_graphs/<region>/`
 *   (external app-private dir — survives large graphs, no storage perm).
 * - Download: OkHttp stream-to-file, 8KB buffer, progress callbacks.
 * - Extract: ZipInputStream with 8KB buffer + canonical-path traversal
 *   guard. Progress state EXTRACTING while unzipping.
 * - Threading: EVERYTHING in [download]/[delete] runs on Dispatchers.IO —
 *   zero UI-thread file/network work. StateFlow emissions are
 *   thread-safe (MutableStateFlow is conflated + atomic).
 * - State: per-region [StateFlow] of [OfflineGraphState]:
 *   NOT_DOWNLOADED → DOWNLOADING(pct) → EXTRACTING → READY / ERROR(msg).
 *
 * Legacy compat (TrackingService / old UI still call these): [graphsRoot]
 * now points at the new store; [graphDir], [isInstalled],
 * [installedRegion], [downloadProgress], [downloadingId], [download] and
 * [delete] keep their signatures and delegate to the new implementation.
 * The old `filesDir/graphs` location is checked as a fallback in
 * [installedRegion] so previously downloaded graphs keep working.
 */
@Singleton
class RoutingGraphManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ---- New per-region state (Step 1 contract) ----

    private val stateFlows = mutableMapOf<String, MutableStateFlow<OfflineGraphState>>()

    /** StateFlow for one region — creates NOT_DOWNLOADED / READY lazily. */
    fun stateFor(regionId: String): StateFlow<OfflineGraphState> =
        synchronized(stateFlows) {
            stateFlows.getOrPut(regionId) {
                MutableStateFlow(
                    if (isInstalled(regionId)) OfflineGraphState.Ready
                    else OfflineGraphState.NotDownloaded,
                )
            }
        }

    private fun emit(regionId: String, state: OfflineGraphState) {
        synchronized(stateFlows) {
            stateFlows.getOrPut(regionId) { MutableStateFlow(state) }.value = state
        }
        // Legacy mirrors for single-download UI.
        when (state) {
            is OfflineGraphState.Downloading -> {
                _downloadingId.value = regionId
                _downloadProgress.value = state.progressPct
            }
            is OfflineGraphState.Extracting -> {
                _downloadingId.value = regionId
                _downloadProgress.value = 100
            }
            else -> {
                if (_downloadingId.value == regionId &&
                    (state is OfflineGraphState.Ready ||
                        state is OfflineGraphState.Error ||
                        state is OfflineGraphState.NotDownloaded)
                ) {
                    _downloadingId.value = null
                    _downloadProgress.value = null
                }
            }
        }
    }

    // ---- Store paths ----

    /** New store root (Step 1 contract). */
    fun graphsRoot(): File =
        File(context.getExternalFilesDir(null), "offline_graphs").apply { mkdirs() }

    private fun legacyRoot(): File = File(context.filesDir, "graphs")

    fun graphDir(regionId: String): File = File(graphsRoot(), regionId)

    /** Installed = prepared graph (properties file present), new or legacy. */
    fun isInstalled(regionId: String): Boolean =
        File(graphDir(regionId), "properties").exists() ||
            File(File(legacyRoot(), regionId), "properties").exists()

    /** First installed region (new store preferred, legacy fallback). */
    fun installedRegion(): File? {
        val fresh = graphsRoot().listFiles()
            ?.firstOrNull { it.isDirectory && File(it, "properties").exists() }
        if (fresh != null) return fresh
        return legacyRoot().listFiles()
            ?.firstOrNull { it.isDirectory && File(it, "properties").exists() }
    }

    // ---- Legacy global progress mirrors ----

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    /** Null = idle; 0..100 while a graph downloads. */
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    private val _downloadingId = MutableStateFlow<String?>(null)
    val downloadingId: StateFlow<String?> = _downloadingId.asStateFlow()

    // ---- Download + extract (all Dispatchers.IO) ----

    /**
     * Downloads [region]'s `.ghz`/ZIP, extracts, validates, installs.
     * Returns true on success. Never throws. Never touches the UI thread.
     */
    suspend fun download(
        region: RoutingRegion,
        onProgress: (Int) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        if (stateFor(region.id).value is OfflineGraphState.Downloading ||
            stateFor(region.id).value is OfflineGraphState.Extracting
        ) {
            return@withContext false
        }
        emit(region.id, OfflineGraphState.Downloading(0))
        val dest = graphDir(region.id)
        val tmpZip = File(graphsRoot(), "${region.id}.ghz.tmp")
        val tmpDir = File(graphsRoot(), "${region.id}.tmp")
        try {
            tmpZip.delete()
            deleteRecursively(tmpDir)
            client.newCall(Request.Builder().url(region.url).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "HTTP ${resp.code} for ${region.id}"
                        Log.w(TAG, "graph $msg")
                        emit(region.id, OfflineGraphState.Error(msg))
                        return@withContext false
                    }
                    val total = resp.body?.contentLength() ?: -1L
                    val input = resp.body?.byteStream()
                    if (input == null) {
                        val msg = "empty response body for ${region.id}"
                        emit(region.id, OfflineGraphState.Error(msg))
                        return@withContext false
                    }
                    // Stream-to-file, 8KB buffer — constant memory, IO thread.
                    input.use { ins ->
                        tmpZip.outputStream().use { out ->
                            val buf = ByteArray(BUFFER_8K)
                            var read = 0L
                            var lastPct = -1
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val pct = (100 * read / total).toInt()
                                        .coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        emit(region.id, OfflineGraphState.Downloading(pct))
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                    }
                }
            check(tmpZip.length() > 1024) { "graph download implausibly small" }
            emit(region.id, OfflineGraphState.Extracting)
            unzip(tmpZip, tmpDir)
            check(File(tmpDir, "properties").exists()) { "no prepared graph inside ZIP" }
            dest.deleteRecursively()
            // renameTo may fail across volumes — fallback to copy.
            val installed = runCatching { tmpDir.renameTo(dest) }.getOrDefault(false) ||
                runCatching {
                    tmpDir.copyRecursively(dest, overwrite = true)
                    true
                }.getOrDefault(false)
            check(installed) { "install move failed" }
            Log.i(TAG, "graph ${region.id} installed at ${dest.absolutePath}")
            emit(region.id, OfflineGraphState.Ready)
            true
        } catch (e: Exception) {
            val msg = e.message?.ifBlank { null } ?: e.javaClass.simpleName
            Log.w(TAG, "graph download failed: $msg")
            emit(region.id, OfflineGraphState.Error(msg))
            false
        } finally {
            tmpZip.delete()
            deleteRecursively(tmpDir)
            if (stateFor(region.id).value is OfflineGraphState.Extracting) {
                emit(region.id, OfflineGraphState.Error("extraction interrupted"))
            }
        }
    }

    /** Wipes a region folder (READY or ERROR). Back to NOT_DOWNLOADED. */
    suspend fun delete(regionId: String) = withContext(Dispatchers.IO) {
        deleteRecursively(graphDir(regionId))
        deleteRecursively(File(legacyRoot(), regionId))
        emit(regionId, OfflineGraphState.NotDownloaded)
    }

    /**
     * ZipInputStream extraction — 8KB buffered, canonical-path traversal
     * guard on every entry. Runs on the caller's thread (always IO here).
     */
    private fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        val destCanonical = dest.canonicalPath
        ZipInputStream(zip.inputStream().buffered(BUFFER_8K)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                // Entry traversal guard: no ../ escapes, no absolute paths.
                check(out.canonicalPath.startsWith(destCanonical + File.separator)) {
                    "bad zip entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { fileOut ->
                        val buf = ByteArray(BUFFER_8K)
                        while (true) {
                            val n = zin.read(buf)
                            if (n < 0) break
                            fileOut.write(buf, 0, n)
                        }
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    private fun deleteRecursively(f: File) {
        runCatching { if (f.exists()) f.deleteRecursively() }
    }

    companion object {
        private const val TAG = "GHGraphs"

        /** 8KB stream buffer for download + extraction (constant memory). */
        private const val BUFFER_8K = 8 * 1024

        /**
         * Cairo + Giza metro graph (clipped bbox 30.85,29.6–31.8,30.3).
         * Baked server-side (custom fastest model + CH) with the same
         * declaration the engine queries with — fingerprint-compatible.
         */
        val TEST_REGION = RoutingRegion(
            id = "cairo-giza",
            name = "Cairo + Giza (Egypt)",
            url = "${AppLinks.GRAPH_BASE_URL}/cairo-giza.ghz",
            sizeMb = 42,
        )

        /**
         * Step 3 contract: hardcoded region list, list-designed.
         * "Monaco Test" is the small smoke-test graph; Cairo+Giza is the
         * production Egypt graph. Add more RoutingRegion entries here.
         */
        val REGIONS = listOf(
            RoutingRegion(
                id = "monaco-test",
                name = "Monaco Test",
                url = "${AppLinks.GRAPH_BASE_URL}/monaco-test.ghz",
                sizeMb = 2,
            ),
            TEST_REGION,
        )
    }
}

/** One downloadable routing graph (ZIP/GHZ of a CH-prepared graph). */
data class RoutingRegion(
    val id: String,
    val name: String,
    val url: String,
    val sizeMb: Int,
)
