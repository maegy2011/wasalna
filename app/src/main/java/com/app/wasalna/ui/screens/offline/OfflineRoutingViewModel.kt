package com.app.wasalna.ui.screens.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.data.routing.GraphHopperRouteEngine
import com.app.wasalna.data.routing.OfflineGraphState
import com.app.wasalna.data.routing.RoutingGraphManager
import com.app.wasalna.data.routing.RoutingRegion
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    /** Region list (hardcoded for now, list-designed via REGIONS). */
    val regions: List<RoutingRegion> = RoutingGraphManager.REGIONS

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
}
