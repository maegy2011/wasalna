package com.app.wasalna.ui.screens.offline

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
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
 */
@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val graphManager: com.app.wasalna.data.routing.RoutingGraphManager,
    private val routeEngine: com.app.wasalna.data.routing.GraphHopperRouteEngine,
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

    private var downloadingRegion: OfflineRegion? = null

    init {
        refreshRegions()
        refreshGraphState()
    }

    fun clearError() {
        _error.value = null
    }

    // ---- Phase 10.4.3.9: offline routing data (on-demand, never bundled).
    val graphProgress = graphManager.downloadProgress
    val graphDownloadingId = graphManager.downloadingId

    private val _graphInstalled = MutableStateFlow(false)
    val graphInstalled: StateFlow<Boolean> = _graphInstalled.asStateFlow()

    private val _graphBusy = MutableStateFlow(false)
    val graphBusy: StateFlow<Boolean> = _graphBusy.asStateFlow()

    fun refreshGraphState() {
        _graphInstalled.value = graphManager.isInstalled(
            com.app.wasalna.data.routing.RoutingGraphManager.TEST_REGION.id,
        )
    }

    fun downloadGraph() {
        if (_graphBusy.value) return
        _graphBusy.value = true
        viewModelScope.launch {
            val ok = graphManager.download(
                com.app.wasalna.data.routing.RoutingGraphManager.TEST_REGION,
            )
            if (ok) {
                val dir = graphManager.graphDir(
                    com.app.wasalna.data.routing.RoutingGraphManager.TEST_REGION.id,
                )
                routeEngine.ensureLoaded(dir)
            } else {
                _error.value = "Routing data download failed — check connection."
            }
            _graphBusy.value = false
            refreshGraphState()
        }
    }

    fun deleteGraph() {
        viewModelScope.launch {
            val id = com.app.wasalna.data.routing.RoutingGraphManager.TEST_REGION.id
            routeEngine.reset()
            graphManager.delete(id)
            refreshGraphState()
        }
    }

    fun refreshRegions() {
        _isLoading.value = true
        runCatching {
            offlineManager.listOfflineRegions(
                object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(regions: Array<OfflineRegion>?) {
                        _regions.value = regions.orEmpty().map { region ->
                            OfflineRegionInfo(
                                id = region.id,
                                name = regionName(region),
                            )
                        }
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
    }

    /**
     * Downloads [bounds] at zooms 10–16 under [name]. Progress flows to
     * [downloadProgress]; completion refreshes the list automatically.
     */
    fun startDownload(name: String, bounds: LatLngBounds) {
        if (_downloadProgress.value != null) return
        val cleanName = name.ifBlank { "My City" }
        _downloadingName.value = cleanName
        _downloadProgress.value = 0f
        runCatching {
            val definition = OfflineTilePyramidRegionDefinition(
                OPENFREEMAP_STYLE_URL,
                bounds,
                MIN_ZOOM,
                MAX_ZOOM,
                appContext.resources.displayMetrics.density,
            )
            val metadata = JSONObject().put(META_NAME, cleanName).toString()
                .toByteArray(Charsets.UTF_8)
            offlineManager.createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        downloadingRegion = region
                        region.setObserver(
                            object : OfflineRegion.OfflineRegionObserver {
                                override fun onStatusChanged(status: OfflineRegionStatus) {
                                    val required = status.requiredResourceCount
                                    val done = status.completedResourceCount
                                    _downloadProgress.value =
                                        if (status.isComplete || required <= 0) 1f
                                        else (done.toFloat() / required).coerceIn(0f, 1f)
                                    if (status.isComplete) {
                                        finishDownload()
                                    }
                                }

                                override fun onError(error: OfflineRegionError) {
                                    Log.e(TAG, "download: ${error.reason} ${error.message}")
                                    _error.value = "Download failed: ${error.message}"
                                    finishDownload()
                                }

                                // Limit guard — MapLibre caps offline tiles per pack.
                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    Log.w(TAG, "tile limit exceeded: $limit")
                                    _error.value =
                                        "Area too large — zoom in and try a smaller region."
                                    finishDownload()
                                }
                            },
                        )
                        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "createOfflineRegion: $error")
                        _error.value = "Couldn't start download: $error"
                        _downloadProgress.value = null
                    }
                },
            )
        }.onFailure {
            _error.value = "Couldn't start download: ${it.message}"
            _downloadProgress.value = null
        }
    }

    fun cancelDownload() {
        runCatching {
            downloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        }
        finishDownload()
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
                                    override fun onDelete() = refreshRegions()
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

    private fun finishDownload() {
        runCatching { downloadingRegion?.setObserver(null) }
        downloadingRegion = null
        _downloadProgress.value = null
        refreshRegions()
    }

    private fun regionName(region: OfflineRegion): String =
        runCatching {
            val meta = region.metadata?.toString(Charsets.UTF_8).orEmpty()
            JSONObject(meta).optString(META_NAME).ifBlank { "Region ${region.id}" }
        }.getOrDefault("Region ${region.id}")

    override fun onCleared() {
        runCatching { downloadingRegion?.setObserver(null) }
        downloadingRegion = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "OfflineMaps"
        private const val META_NAME = "name"

        /** City-level pack: zoom 10 (city) → 16 (streets). */
        private const val MIN_ZOOM = 10.0
        private const val MAX_ZOOM = 16.0
    }
}
