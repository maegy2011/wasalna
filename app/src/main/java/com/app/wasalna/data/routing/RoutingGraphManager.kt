package com.app.wasalna.data.routing

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10.4.3.9: routing-graph hub — on-demand download (never bundled).
 *
 * The user picks a region in [OfflineMapsScreen]; its `.ghz` (zipped,
 * CH-prepared graph) streams here with progress, unpacks into
 * `filesDir/graphs/<id>/`, and is header-validated with [ByteBufferCompat]
 * bulk reads before it is trusted. Nothing ships inside the APK.
 */
@Singleton
class RoutingGraphManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    /** Null = idle; 0..100 while a graph downloads. */
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    private val _downloadingId = MutableStateFlow<String?>(null)
    val downloadingId: StateFlow<String?> = _downloadingId.asStateFlow()

    fun graphsRoot(): File = File(context.filesDir, "graphs").apply { mkdirs() }

    fun graphDir(regionId: String): File = File(graphsRoot(), regionId)

    /** Installed = prepared graph (properties file present). */
    fun isInstalled(regionId: String): Boolean =
        File(graphDir(regionId), "properties").exists()

    /** First installed region, if any (single-region phase). */
    fun installedRegion(): File? =
        graphsRoot().listFiles()
            ?.firstOrNull { it.isDirectory && File(it, "properties").exists() }

    /**
     * Downloads [region]'s `.ghz`, unzips, validates, installs.
     * Returns true on success. Never throws.
     */
    suspend fun download(
        region: RoutingRegion,
        onProgress: (Int) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        _downloadingId.value = region.id
        _downloadProgress.value = 0
        val dest = graphDir(region.id)
        val tmpZip = File(graphsRoot(), "${region.id}.ghz.tmp")
        val tmpDir = File(graphsRoot(), "${region.id}.tmp")
        try {
            tmpZip.delete()
            deleteRecursively(tmpDir)
            client.newCall(Request.Builder().url(region.url).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "graph HTTP ${resp.code} for ${region.id}")
                        return@withContext false
                    }
                    val total = resp.body?.contentLength() ?: -1L
                    resp.body?.byteStream()?.use { input ->
                        tmpZip.outputStream().use { out ->
                            val buf = ByteArray(1 shl 16)
                            var read = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val pct = (100 * read / total).toInt()
                                        .coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        _downloadProgress.value = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                    }
                }
            check(tmpZip.length() > 1024) { "graph download implausibly small" }
            unzip(tmpZip, tmpDir)
            // Validate BEFORE trusting: properties + bulk header read.
            check(File(tmpDir, "properties").exists()) { "no prepared graph inside" }
            validateGraphHeaders(tmpDir)
            dest.deleteRecursively()
            check(tmpDir.renameTo(dest)) { "install rename failed" }
            Log.i(TAG, "graph ${region.id} installed")
            true
        } catch (e: Exception) {
            Log.w(TAG, "graph download failed: ${e.message}")
            false
        } finally {
            tmpZip.delete()
            deleteRecursively(tmpDir)
            _downloadProgress.value = null
            _downloadingId.value = null
        }
    }

    suspend fun delete(regionId: String) = withContext(Dispatchers.IO) {
        deleteRecursively(graphDir(regionId))
    }

    private fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                // Zip-slip guard.
                check(out.canonicalPath.startsWith(dest.canonicalPath)) { "bad zip entry" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    /**
     * Bulk header validation with ART-safe reads (fix #1 in action): every
     * graph data file must open and yield its first 4KB through
     * [ByteBufferCompat]. A truncated download fails here, never in the router.
     */
    private fun validateGraphHeaders(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.length() > 4096 } ?: emptyArray()
        check(files.isNotEmpty()) { "graph folder holds no data files" }
        val probe = ByteArray(4096)
        for (f in files) {
            RandomAccessFile(f, "r").use { raf ->
                val ch: FileChannel = raf.channel
                val buf: ByteBuffer = ch.map(FileChannel.MapMode.READ_ONLY, 0, 4096)
                ByteBufferCompat.getBulk(buf, 0, probe)
            }
        }
    }

    private fun deleteRecursively(f: File) {
        runCatching { if (f.exists()) f.deleteRecursively() }
    }

    companion object {
        private const val TAG = "GHGraphs"

        /**
         * Cairo + Giza metro graph (clipped bbox 30.85,29.6–31.8,30.3).
         * Baked server-side (custom fastest model + CH) with the same
         * declaration the engine queries with — fingerprint-compatible.
         */
        val TEST_REGION = RoutingRegion(
            id = "cairo-giza",
            name = "Cairo + Giza (Egypt)",
            url = "https://mohamedadel.cfd/downloads/graphs/cairo-giza.ghz",
            sizeMb = 42,
        )
    }
}

/** Phase 10.4.3.9: one downloadable routing graph. */
data class RoutingRegion(
    val id: String,
    val name: String,
    val url: String,
    val sizeMb: Int,
)
