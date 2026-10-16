package com.app.wasalna.ui.screens.offline

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.app.wasalna.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import javax.inject.Inject

/** Phase 10.4.3.2: one downloaded region for the list. */
data class OfflineRegionInfo(
    val id: Long,
    val name: String,
)

/**
 * Phase 10.4.3.2: Vela-style offline tiles via MapLibre OfflineManager.
 * Downloads run in the SDK (survive screen rotation); the observer only
 * posts progress — detached in onCleared / on download end (no leaks).
 *
 * Offline-upgrade (Oct 2026, Vela-inspired — ideas only, no Vela code):
 * - Zoom pack 10–14: OpenFreeMap serves vector tiles whose last detail
 *   zoom is 14 (Vela FULL_MAP_ZOOM); zooms 15–16 pulled nothing useful
 *   and only bloated packs. Smaller, faster downloads, same streets.
 * - Explicit 50,000-tile cap (Vela TILE_LIMIT) + pre-download estimate
 *   (tile count × 110 KB, Vela AREA_TILE_KB) so oversized areas are
 *   refused BEFORE downloading with a "zoom in" message.
 * - Exactly-once completion guard (Vela `done` flag): MapLibre keeps
 *   firing onStatusChanged around completion — onDone path runs once.
 * - Post-download integrity check: the new region must appear in the
 *   listing with matching metadata, else a verify-failed error.
 * - Cancel discards the partial pack (no orphan tiles in the DB).
 * - packDatabase (VACUUM) after every delete (Vela lesson: MapLibre
 *   keeps saved areas + browsing cache in ONE SQLite file and deleting
 *   rows does not shrink it — a phone showed 5 GB with nothing left).
 * - Best-effort on-device tile DB size for the Storage row.
 *
 * Attribution: approach inspired by Vela (GPL-3.0,
 * https://github.com/PimpinPumpkin/Vela) — reimplemented from the
 * documented behavior; no Vela source is bundled here.
 */
@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val graphManager: com.app.wasalna.data.routing.RoutingGraphManager,
    private val routeEngine: com.app.wasalna.data.routing.GraphHopperRouteEngine,
    private val manifestRepo: com.app.wasalna.data.routing.RegionManifestRepository,
    private val gazetteer: com.app.wasalna.data.search.OfflineGazetteer,
) : ViewModel() {

    private val offlineManager: OfflineManager by lazy {
        OfflineManager.getInstance(appContext)
    }

    private val _regions = MutableStateFlow<List<OfflineRegionInfo>>(emptyList())
    val regions: StateFlow<List<OfflineRegionInfo>> = _regions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Null = idle; 0..1 while a download runs. */
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _downloadingName = MutableStateFlow("")
    val downloadingName: StateFlow<String> = _downloadingName.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** One-tap retry of the last failed/oversized request (name + bounds). */
    private var lastRequest: Pair<String, LatLngBounds>? = null
    private val _canRetry = MutableStateFlow(false)
    val canRetry: StateFlow<Boolean> = _canRetry.asStateFlow()

    /** Best-effort MapLibre offline DB bytes (null = unknown). IO thread. */
    private val _offlineDbBytes = MutableStateFlow<Long?>(null)
    val offlineDbBytes: StateFlow<Long?> = _offlineDbBytes.asStateFlow()

    private var downloadingRegion: OfflineRegion? = null

    /**
     * M3: ids of tile regions downloaded > 30 days ago — the screen
     * banners them (OpenFreeMap re-renders; old packs go stale).
     */
    private val _staleRegionIds = MutableStateFlow<Set<Long>>(emptySet())
    val staleRegionIds: StateFlow<Set<Long>> = _staleRegionIds.asStateFlow()

    private val _graphInstalled = MutableStateFlow(false)
    val graphInstalled: StateFlow<Boolean> = _graphInstalled.asStateFlow()

    private val _graphBusy = MutableStateFlow(false)
    val graphBusy: StateFlow<Boolean> = _graphBusy.asStateFlow()

    /**
     * Upgrade (Oct 2026): EVERY stored tile region gets a routable
     * graph — installed graph ids across ALL regions (not just Cairo).
     * Refreshed alongside [refreshGraphState]; tile downloads trigger
     * [ensureGraphForBounds] so the graph arrives with the tiles.
     */
    private val _installedGraphIds = MutableStateFlow<List<String>>(emptyList())
    val installedGraphIds: StateFlow<List<String>> = _installedGraphIds.asStateFlow()

    /** Last tile-download bbox — drives the alongside-graph fetch. */
    private var lastTileBounds: LatLngBounds? = null

    /** M4: last journaled progress (5% throttle for the cache file). */
    private var lastJournaledPct = 0f

    init {
        refreshRegions()
        refreshGraphState()
        // Warm the region manifest (7-day cache) so graph lookups and
        // update badges work without opening the routing screen first.
        viewModelScope.launch { runCatching { manifestRepo.fetchManifest() } }
        viewModelScope.launch { restoreInterruptedDownload() }
    }

    fun clearError() {
        _error.value = null
        _canRetry.value = lastRequest != null && _downloadProgress.value == null
    }

    fun retryDownload() {
        val req = lastRequest ?: return
        if (_downloadProgress.value != null) return
        _error.value = null
        _canRetry.value = false
        startDownload(req.first, req.second)
    }

    // ---- Phase 10.4.3.9: offline routing data (on-demand, never bundled).
    val graphProgress = graphManager.downloadProgress
    val graphDownloadingId = graphManager.downloadingId

    fun refreshGraphState() {
        // Check if any region is installed
        val installedIds = runCatching { graphManager.installedRegionIds() }
            .getOrDefault(emptyList())
        _graphInstalled.value = installedIds.isNotEmpty()
        // Upgrade: all-region installed ids (IO inside manager is cheap
        // file checks; refresh on the caller thread is safe — no network).
        // ── OFFLINE BOUNDARY: file checks only, zero network. ──
        _installedGraphIds.value = installedIds
    }

    /** Which known graph overlaps this tile bbox (null = uncharted area). */
    fun graphForBounds(bounds: LatLngBounds): com.app.wasalna.data.routing.RoutingRegion? =
        graphManager.graphForTileBounds(
            south = bounds.latitudeSouth,
            west = bounds.longitudeWest,
            north = bounds.latitudeNorth,
            east = bounds.longitudeEast,
        )

    /**
     * Upgrade: download-or-ensure the graph alongside tiles. Called
     * after a tile pack verifies: when the overlapping graph is missing
     * it downloads in the background with progress in the existing
     * graph card ([graphProgress]/[graphDownloadingId]); when present
     * it preloads the engine region-aware. Never throws, no `!!`.
     */
    fun ensureGraphForBounds(bounds: LatLngBounds) {
        val region = graphForBounds(bounds) ?: return
        if (graphManager.isInstalled(region.id)) {
            viewModelScope.launch {
                runCatching {
                    routeEngine.ensureLoadedFor(region.id, graphManager.graphDir(region.id))
                }
                refreshGraphState()
            }
            return
        }
        downloadRegionById(region.id)
    }

    /** Download any region by id (tile-flow entry; progress in graph card). */
    fun downloadRegionById(regionId: String) {
        val region = manifestRepo.getManifest()?.regionById(regionId) ?: return
        if (_graphBusy.value) return
        _graphBusy.value = true
        viewModelScope.launch {
            val ok = graphManager.download(region)
            if (ok) {
                runCatching {
                    routeEngine.ensureLoadedFor(region.id, graphManager.graphDir(region.id))
                }
                // Auto-download gazetteer for this region
                gazetteer.ensureRegion(region)
            } else {
                _error.value = "Routing data download failed — check connection."
            }
            _graphBusy.value = false
            refreshGraphState()
        }
    }

    /** Download a specific region by id (replaces old downloadGraph). */
    fun downloadGraph(regionId: String) {
        val region = manifestRepo.getManifest()?.regionById(regionId) ?: return
        if (_graphBusy.value) return
        _graphBusy.value = true
        viewModelScope.launch {
            val ok = graphManager.download(region)
            if (ok) {
                val dir = graphManager.graphDir(region.id)
                routeEngine.ensureLoadedFor(region.id, dir)
                // Auto-download gazetteer for this region
                gazetteer.ensureRegion(region)
            } else {
                _error.value = "Routing data download failed — check connection."
            }
            _graphBusy.value = false
            refreshGraphState()
        }
    }

    /** Delete a specific region by id (replaces old deleteGraph). */
    fun deleteGraph(regionId: String) {
        viewModelScope.launch {
            routeEngine.reset()
            graphManager.delete(regionId)
            refreshGraphState()
        }
    }

    /** Get first available region ID from manifest for generic download. */
    fun getFirstRegionId(): String? = manifestRepo.getManifest()?.regions?.firstOrNull()?.id

    /** Get first installed region ID for generic delete. */
    fun getFirstInstalledRegionId(): String? = graphManager.installedRegionIds().firstOrNull()

    fun refreshRegions() {
        _isLoading.value = true
        runCatching {
            offlineManager.listOfflineRegions(
                object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(regions: Array<OfflineRegion>?) {
                        val list = regions.orEmpty()
                        _regions.value = list.map { region ->
                            OfflineRegionInfo(
                                id = region.id,
                                name = regionName(region),
                            )
                        }
                        // M3: flag packs older than 30 days (missing stamp
                        // = pre-M3 pack → treat as stale, honest default).
                        val now = System.currentTimeMillis()
                        _staleRegionIds.value = list.mapNotNull { region ->
                            val at = regionDownloadedAt(region)
                            region.id.takeIf { at == null || now - at > TILE_STALE_MS }
                        }.toSet()
                        _isLoading.value = false
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "listOfflineRegions: $error")
                        _error.value = "Couldn't list downloads: $error"
                        _isLoading.value = false
                    }
                },
            )
        }.onFailure {
            _error.value = "Offline storage unavailable: ${it.message}"
            _isLoading.value = false
        }
        viewModelScope.launch { queryOfflineDbSize() }
    }

    /**
     * Downloads [bounds] at zooms 10–14 under [name]. Vela-style pre-check:
     * estimates tiles first and refuses oversized areas before spending
     * a byte. Progress flows to [downloadProgress]; completion verifies
     * the new region in the listing, then refreshes automatically.
     */
    fun startDownload(name: String, bounds: LatLngBounds) {
        if (_downloadProgress.value != null) return
        val cleanName = name.ifBlank { appContext.getString(R.string.offline_default_city) }
        lastRequest = cleanName to bounds
        // Upgrade: remember the tile bbox so the verified completion can
        // fetch the overlapping routing graph alongside the tiles.
        lastTileBounds = bounds
        // Pre-download estimate — refuse before spending bytes (Vela).
        val tiles = OfflineTileEstimate.countTiles(
            south = bounds.latitudeSouth,
            west = bounds.longitudeWest,
            north = bounds.latitudeNorth,
            east = bounds.longitudeEast,
            minZoom = MIN_ZOOM_INT,
            maxZoom = MAX_ZOOM_INT,
        )
        if (tiles > OfflineTileEstimate.TILE_CAP) {
            _error.value = appContext.getString(
                R.string.offline_too_large, tiles, OfflineTileEstimate.TILE_CAP,
            )
            _canRetry.value = false
            lastRequest = null
            return
        }
        _canRetry.value = false
        _downloadingName.value = cleanName
        _downloadProgress.value = 0f
        runCatching {
            // Generous per-pack cap so a screen-sized area downloads; very
            // large areas still hit it and report back (Vela TILE_LIMIT).
            runCatching {
                offlineManager.setOfflineMapboxTileCountLimit(OfflineTileEstimate.TILE_CAP)
            }
            val definition = OfflineTilePyramidRegionDefinition(
                OPENFREEMAP_STYLE_URL,
                bounds,
                MIN_ZOOM,
                MAX_ZOOM,
                appContext.resources.displayMetrics.density,
            )
            // M3: stamp creation time so 30-day staleness can banner.
            val metadata = JSONObject()
                .put(META_NAME, cleanName)
                .put(META_DOWNLOADED_AT, System.currentTimeMillis())
                .toString().toByteArray(Charsets.UTF_8)
            offlineManager.createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        downloadingRegion = region
                        region.setObserver(
                            object : OfflineRegion.OfflineRegionObserver {
                                // MapLibre keeps firing around completion —
                                // the finish path must run EXACTLY once (Vela).
                                var done = false
                                override fun onStatusChanged(status: OfflineRegionStatus) {
                                    if (done) return
                                    val required = status.requiredResourceCount
                                    val doneCount = status.completedResourceCount
                                    val pct =
                                        if (status.isComplete || required <= 0) 1f
                                        else (doneCount.toFloat() / required).coerceIn(0f, 1f)
                                    _downloadProgress.value = pct
                                    // M4: journal progress (5% steps) so a
                                    // process death offers a restart, not
                                    // a silent partial pack.
                                    if (!status.isComplete && pct - lastJournaledPct >= 0.05f) {
                                        lastJournaledPct = pct
                                        journalProgress(cleanName, lastRequest?.second, pct)
                                    }
                                    if (status.isComplete) {
                                        done = true
                                        finishDownload(verifyId = region.id)
                                    }
                                }

                                override fun onError(error: OfflineRegionError) {
                                    if (done) return
                                    done = true
                                    Log.e(TAG, "download: ${error.reason} ${error.message}")
                                    _error.value = "Download failed: ${error.message}"
                                    _canRetry.value = true
                                    finishDownload(verifyId = null)
                                }

                                // Limit guard — MapLibre caps offline tiles per pack.
                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    if (done) return
                                    done = true
                                    runCatching {
                                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    }
                                    Log.w(TAG, "tile limit exceeded: $limit")
                                    _error.value =
                                        appContext.getString(R.string.offline_too_large, limit, limit)
                                    _canRetry.value = false
                                    lastRequest = null
                                    finishDownload(verifyId = null)
                                }
                            },
                        )
                        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "createOfflineRegion: $error")
                        _error.value = "Couldn't start download: $error"
                        _canRetry.value = true
                        _downloadProgress.value = null
                    }
                },
            )
        }.onFailure {
            _error.value = "Couldn't start download: ${it.message}"
            _canRetry.value = true
            _downloadProgress.value = null
        }
    }

    /**
     * Cancels the running download and discards the partial pack so no
     * orphan tiles linger in the offline DB. Re-download starts clean.
     */
    fun cancelDownload() {
        val partial = downloadingRegion
        downloadingRegion = null
        _downloadProgress.value = null
        lastRequest = null
        _canRetry.value = false
        clearJournal()
        runCatching { partial?.setObserver(null) }
        runCatching { partial?.setDownloadState(OfflineRegion.STATE_INACTIVE) }
        if (partial == null) {
            refreshRegions()
            return
        }
        runCatching {
            partial.delete(
                object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        packDatabase()
                        refreshRegions()
                    }

                    override fun onError(error: String) {
                        Log.w(TAG, "cancel cleanup delete failed: $error")
                        refreshRegions()
                    }
                },
            )
        }.onFailure { refreshRegions() }
    }

    fun deleteRegion(info: OfflineRegionInfo) {
        viewModelScope.launch {
            runCatching {
                offlineManager.listOfflineRegions(
                    object : OfflineManager.ListOfflineRegionsCallback {
                        override fun onList(regions: Array<OfflineRegion>?) {
                            val target = regions?.firstOrNull { it.id == info.id }
                            if (target == null) {
                                refreshRegions()
                                return
                            }
                            target.delete(
                                object : OfflineRegion.OfflineRegionDeleteCallback {
                                    override fun onDelete() {
                                        // Deleting rows does not shrink the
                                        // single SQLite file — pack it (Vela).
                                        packDatabase()
                                        refreshRegions()
                                    }
                                    override fun onError(error: String) {
                                        _error.value = "Delete failed: $error"
                                    }
                                },
                            )
                        }

                        override fun onError(error: String) {
                            _error.value = "Delete failed: $error"
                        }
                    },
                )
            }
        }
    }

    /**
     * Gives the space back: packs (VACUUM) the shared offline SQLite file.
     * Always completes — an error is swallowed and reported as done (Vela).
     */
    private fun packDatabase() {
        runCatching {
            offlineManager.packDatabase(
                object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() {
                        viewModelScope.launch { queryOfflineDbSize() }
                    }

                    override fun onError(message: String) {
                        Log.w(TAG, "packDatabase: $message")
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "packDatabase unavailable: ${it.message}")
        }
    }

    /**
     * Best-effort size of the MapLibre offline DB (IO).
     * M5: scans the internal files dir AND the external app-private dir —
     * graphs live externally and the old scan missed that side entirely.
     */
    private suspend fun queryOfflineDbSize() = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            val dirs = listOfNotNull(
                appContext.filesDir,
                appContext.getExternalFilesDir(null),
            )
            dirs.sumOf { dir ->
                dir.listFiles()
                    ?.filter { f ->
                        f.isFile && (f.name.contains("offline", ignoreCase = true) ||
                            f.name.startsWith("mbgl", ignoreCase = true))
                    }
                    ?.sumOf { it.length() } ?: 0L
            }
        }.getOrNull()
        _offlineDbBytes.value = bytes
    }

    /**
     * M4: journal the running tile download to the cache dir (IO).
     * Survives process death; [restoreInterruptedDownload] turns it into
     * a one-tap restart. True resume is impossible (MapLibre limitation)
     * so the copy is honest: a fresh download, orphan partial cleaned
     * from the list by the user.
     */
    private fun journalProgress(name: String, bounds: LatLngBounds?, pct: Float) {
        if (bounds == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val root = JSONObject()
                    .put("name", name)
                    .put("south", bounds.latitudeSouth)
                    .put("west", bounds.longitudeWest)
                    .put("north", bounds.latitudeNorth)
                    .put("east", bounds.longitudeEast)
                    .put("pct", (pct * 100).toInt())
                File(appContext.cacheDir, JOURNAL_FILE).writeText(root.toString())
            }
        }
    }

    private fun clearJournal() {
        lastJournaledPct = 0f
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(appContext.cacheDir, JOURNAL_FILE).delete() }
        }
    }

    /**
     * M4: called once from init — a leftover journal means the process
     * died mid-download. Surfaces the area + a retry affordance via the
     * existing [lastRequest]/error path (no new UI state).
     */
    private fun restoreInterruptedDownload() {
        val (name, bounds) = runCatching {
            val f = File(appContext.cacheDir, JOURNAL_FILE)
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            val b = LatLngBounds.Builder()
                .include(
                    org.maplibre.android.geometry.LatLng(
                        root.getDouble("south"), root.getDouble("west"),
                    ),
                )
                .include(
                    org.maplibre.android.geometry.LatLng(
                        root.getDouble("north"), root.getDouble("east"),
                    ),
                )
                .build()
            root.getString("name") to b
        }.getOrNull() ?: return
        lastRequest = name to bounds
        lastTileBounds = bounds
        _error.value = appContext.getString(R.string.offline_interrupted, name)
        _canRetry.value = true
    }

    /**
     * Detaches the observer, then — on success — verifies the new region
     * is actually in the listing with matching metadata (integrity check).
     */
    private fun finishDownload(verifyId: Long?) {
        runCatching { downloadingRegion?.setObserver(null) }
        downloadingRegion = null
        _downloadProgress.value = null
        clearJournal()
        if (verifyId == null) {
            refreshRegions()
            return
        }
        runCatching {
            offlineManager.listOfflineRegions(
                object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(regions: Array<OfflineRegion>?) {
                        val found = regions?.firstOrNull { it.id == verifyId }
                        val ok = found != null && regionName(found).isNotBlank()
                        if (!ok) {
                            Log.w(TAG, "verify: region $verifyId missing after download")
                            _error.value = appContext.getString(R.string.offline_verify_failed)
                            _canRetry.value = true
                        } else {
                            lastRequest = null
                            _canRetry.value = false
                            // Upgrade: tiles verified → ensure the overlapping
                            // routing graph downloads alongside (progress in
                            // the existing graph card; silent when uncharted).
                            lastTileBounds?.let { bounds ->
                                ensureGraphForBounds(bounds)
                                // Also download gazetteer for the overlapping region
                                graphForBounds(bounds)?.let { region ->
                                    viewModelScope.launch { gazetteer.ensureRegion(region) }
                                }
                            }
                            lastTileBounds = null
                        }
                        refreshRegions()
                    }

                    override fun onError(error: String) {
                        Log.w(TAG, "verify list failed: $error")
                        refreshRegions()
                    }
                },
            )
        }.onFailure { refreshRegions() }
    }

    private fun regionName(region: OfflineRegion): String =
        runCatching {
            val meta = region.metadata?.toString(Charsets.UTF_8).orEmpty()
            JSONObject(meta).optString(META_NAME).ifBlank { "Region ${region.id}" }
        }.getOrDefault("Region ${region.id}")

    /** M3: creation timestamp from metadata (null = pre-M3 pack). */
    private fun regionDownloadedAt(region: OfflineRegion): Long? =
        runCatching {
            val meta = region.metadata?.toString(Charsets.UTF_8).orEmpty()
            JSONObject(meta).optLong(META_DOWNLOADED_AT, -1L).takeIf { it > 0 }
        }.getOrNull()

    override fun onCleared() {
        runCatching { downloadingRegion?.setObserver(null) }
        downloadingRegion = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "OfflineMaps"
        private const val META_NAME = "name"

        /** M3: creation timestamp key in region metadata. */
        private const val META_DOWNLOADED_AT = "downloadedAt"

        /** M3: packs older than this banner as possibly stale. */
        private const val TILE_STALE_MS = 30L * 24L * 60L * 60L * 1000L

        /** M4: cache-dir journal of the running tile download. */
        private const val JOURNAL_FILE = "tile_download.json"

        /**
         * City-level pack: zoom 10 (city) → 14 (streets, vector last zoom).
         * Was 10–16; OpenFreeMap vector tiles carry no detail past 14, so
         * the top zooms only bloated packs (Vela FULL_MAP_ZOOM = 14).
         */
        private const val MIN_ZOOM = 10.0
        private const val MAX_ZOOM = 14.0
        const val MIN_ZOOM_INT = 10
        const val MAX_ZOOM_INT = 14

        /** Center of a bounds (for preset camera jumps). */
        fun centerOf(bounds: LatLngBounds): LatLng = LatLng(
            (bounds.latitudeNorth + bounds.latitudeSouth) / 2.0,
            (bounds.longitudeEast + bounds.longitudeWest) / 2.0,
        )
    }
}
