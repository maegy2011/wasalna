package com.app.wasalna.ui.screens.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.data.routing.GraphHopperRouteEngine
import com.app.wasalna.data.routing.OfflineGraphState
import com.app.wasalna.data.routing.RegionManifestRepository
import com.app.wasalna.data.routing.RoutingGraphManager
import com.app.wasalna.data.routing.RoutingRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in the offline-routing region list. */
data class OfflineRoutingRow(
    val region: RoutingRegion,
    val state: OfflineGraphState,
)

/**
 * Phase 10.5.x Step 3: per-region offline-routing state.
 * Each region exposes its [OfflineGraphState] from [RoutingGraphManager];
 * download/delete run on Dispatchers.IO inside the manager — never here.
 */
@HiltViewModel
class OfflineRoutingViewModel @Inject constructor(
    private val graphManager: RoutingGraphManager,
    private val routeEngine: GraphHopperRouteEngine,
    private val gazetteer: com.app.wasalna.data.search.OfflineGazetteer,
    private val manifestRepo: RegionManifestRepository,
) : ViewModel() {

    /** Region list from dynamic manifest. */
    val regions: List<RoutingRegion> = manifestRepo.getManifest()?.regions ?: emptyList()

    /** Engine load error (exact message) for status display. */
    val engineError: StateFlow<String?> = routeEngine.loadError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun stateFor(regionId: String): StateFlow<OfflineGraphState> =
        graphManager.stateFor(regionId)

    fun download(region: RoutingRegion) {
        viewModelScope.launch {
            val ok = graphManager.download(region)
            if (ok) {
                runCatching {
                    routeEngine.ensureLoadedFor(region.id, graphManager.graphDir(region.id))
                }
            }
        }
    }

    fun delete(regionId: String) {
        viewModelScope.launch {
            runCatching { routeEngine.reset() }
            runCatching { graphManager.delete(regionId) }
        }
    }

    // ---- Offline search indexes (Oct 2026): downloadable gazetteers,
    // same UX pattern as graphs. [gazVersion] bumps on every change so
    // the screen re-reads stored state (file-based, not a Flow).
    private val _gazVersion = MutableStateFlow(0)
    val gazVersion: StateFlow<Int> = _gazVersion

    private val _gazBusy = MutableStateFlow<Set<String>>(emptySet())
    val gazBusy: StateFlow<Set<String>> = _gazBusy

    private val _gazFailed = MutableStateFlow<Set<String>>(emptySet())
    val gazFailed: StateFlow<Set<String>> = _gazFailed

    fun isGazStored(regionId: String): Boolean =
        runCatching { gazetteer.isStored(regionId) }.getOrDefault(false)

    fun gazSizeKb(regionId: String): Long =
        runCatching { gazetteer.storedBytes(regionId) / 1024L }.getOrDefault(0L)

    fun downloadGazetteer(region: RoutingRegion) {
        if (_gazBusy.value.contains(region.id)) return
        _gazBusy.value = _gazBusy.value + region.id
        _gazFailed.value = _gazFailed.value - region.id
        viewModelScope.launch {
            val ok = runCatching { gazetteer.ensureRegion(region) }
                .getOrDefault(false)
            if (!ok) _gazFailed.value = _gazFailed.value + region.id
            _gazBusy.value = _gazBusy.value - region.id
            _gazVersion.value = _gazVersion.value + 1
        }
    }

    fun deleteGazetteer(regionId: String) {
        viewModelScope.launch {
            runCatching { gazetteer.delete(regionId) }
            _gazFailed.value = _gazFailed.value - regionId
            _gazVersion.value = _gazVersion.value + 1
        }
    }
}
