package com.app.wasalna.ui.screens.map

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R
import com.app.wasalna.domain.model.displayName

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.app.wasalna.data.simulation.TripSimulationController
import com.app.wasalna.domain.model.CairoTransitStations
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.ui.ads.InterstitialHelper
import com.app.wasalna.ui.components.TransitApproxNote
import com.app.wasalna.ui.components.TransitGuideCard
import com.app.wasalna.ui.components.TransitLiveCard
import com.app.wasalna.ui.components.HomeWorkQuickRow
import com.app.wasalna.ui.components.OfflineModeBanner
import com.app.wasalna.ui.components.OfflineModeButton
import com.app.wasalna.ui.components.SimulationBanner
import com.app.wasalna.ui.components.SimulationControlBar
import com.app.wasalna.ui.components.TripSimulationPanel
import com.app.wasalna.ui.components.DestinationShareSheet
import com.app.wasalna.ui.components.QuickPlaceEditDialog
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.LatLng
import org.maplibre.android.camera.CameraPosition as MapLibreCameraPosition
import org.maplibre.android.camera.CameraUpdateFactory as MapLibreCameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.app.wasalna.data.traffic.TrafficOverlayState
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

private val DefaultCairoLibre = MapLibreLatLng(30.0444, 31.2357)

/** Phase 10-1: free vector style — OpenFreeMap tiles, no API key. */
private const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val MARKER_IMAGE_ID = "wasalna-marker"
// True offline mode (GPS-chip only): Arabic toggle confirmations.
/** Phase 10-2: OSRM route source + layer ids (MapLibre free engine). */
private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"

/**
 * Phase 4: destination picker — search at top, results list,
 * map filling the rest, Save FAB enabled only when a place is selected.
 * Phase 10-Fix: 100% MapLibre (OpenFreeMap) — no engine toggle.
 * Professional M3 SearchBar: while active, results take over the screen;
 * picking a result reveals the map + marker. Photon keyless search
 * (real places + coords, one step) + OSRM route line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onSaved: () -> Unit = {},
    onBack: () -> Unit = {},
    // Phase 10.4.3.1: optional live-trip args (Home widget tap).
    tripName: String? = null,
    tripLat: Double? = null,
    tripLng: Double? = null,
    tripMode: String? = null,
) {
    val query by viewModel.searchQuery.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    // REDESIGN 10 Oct 2026: search loading state (clear loading UX).
    val isSearching by viewModel.isSearching.collectAsState()
    val routeGeoJson by viewModel.routeGeoJson.collectAsState()
    val selectedName by viewModel.selectedName.collectAsState()
    val selectedAddress by viewModel.selectedAddress.collectAsState()
    val selectedLatLng by viewModel.selectedLatLng.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val transportMode by viewModel.transportMode.collectAsState()
    val etaMinutes by viewModel.etaMinutes.collectAsState()
    val recenterTarget by viewModel.recenterTarget.collectAsState()
    // Map UX fixes: zoom + traffic + quick places.
    val cameraZoom by viewModel.cameraZoom.collectAsState()
    val isTrafficEnabled by viewModel.isTrafficEnabled.collectAsState()
    val trafficOverlayState by viewModel.trafficState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val homeLat by viewModel.homeLat.collectAsState()
    val homeLng by viewModel.homeLng.collectAsState()
    val homeAddress by viewModel.homeAddress.collectAsState()
    val workLat by viewModel.workLat.collectAsState()
    val workLng by viewModel.workLng.collectAsState()
    val workAddress by viewModel.workAddress.collectAsState()
    // True offline mode (GPS-chip only): same StateFlow the toggle writes
    // (single source of truth, two-way synced with Settings).
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    // TRIP SIMULATION: banner visibility (controller singleton, no VM change).
    val isSimulating by TripSimulationController.isSimulating.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Phase 10-Fix: SearchBar active state — while typing, results take
    // over the screen (map hidden); picking a result reveals map + marker.
    var searchActive by remember { mutableStateOf(false) }

    // Phase 10.4.2: hardware/software back always pops gracefully —
    // never freezes, never traps the user on the map.
    BackHandler(onBack = onBack)

    // Phase 10.4.1: reactive route/ETA — refires on EVERY selection or
    // transport-mode change, so the ETA always matches the chosen mode.
    LaunchedEffect(selectedLatLng, transportMode) {
        if (selectedLatLng != null) {
            viewModel.refreshRouteAndEta()
        }
    }

    // Proposal (أ): trip panel lives in a bottom sheet — the map keeps
    // the full stage. Opened on explicit user picks (tap/search/quick/
    // live-trip), never on auto-center; dismiss keeps the selection.
    var tripSheetOpen by remember { mutableStateOf(false) }
    val tripSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Phase 10.4.3.1: live-trip entry — consumed once per navigation.
    val tripConsumed = remember(tripLat, tripLng) { mutableStateOf(false) }
    LaunchedEffect(tripLat, tripLng) {
        if (!tripConsumed.value && tripLat != null && tripLng != null) {
            tripConsumed.value = true
            viewModel.showTrip(tripName ?: "Active trip", tripLat, tripLng, tripMode)
            tripSheetOpen = true
        }
    }

    // Map UX fixes: auto live-location pin — blank picker (no trip args)
    // centers on the GPS fix + drops a marker + reveals the Add button
    // with no manual tap needed.
    LaunchedEffect(Unit) {
        if (tripLat == null && tripLng == null) {
            viewModel.autoCenterOnMyLocation()
        }
    }

    // Map UX fixes: quick-place edit dialog state (search-mode only).
    var editTarget by remember { mutableStateOf<String?>(null) }
    // Phase 10.11: dynamic destination share sheet (re-queries on open).
    var showDestinationShare by remember { mutableStateOf(false) }

    // Map Home/Work from-map-point: long-press (or tap on the
    // live-location/address pin card) opens a bottom sheet with two BIG
    // elderly-friendly buttons. Null = sheet hidden. No new permissions.
    var homeWorkSheetPoint by remember { mutableStateOf<LatLng?>(null) }
    val homeWorkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun startQuickTrip(lat: Double?, lng: Double?, address: String) {
        if (lat == null || lng == null) {
            editTarget = if (address == homeAddress) "home" else "work"
            return
        }
        fun select() {
            if (viewModel.selectQuickPlace(lat, lng, address)) {
                searchActive = false
                tripSheetOpen = true
            }
        }
        if (!isPremium) {
            InterstitialHelper.showInterstitial(context) { select() }
        } else {
            select()
        }
    }

    // Save uses this address as the Location name for map taps.
    val addressLine: String? = selectedAddress ?: selectedName

    // Smart back (user order Oct 2026): while the search UI, the trip
    // sheet, or the Home/Work sheet is open, back dismisses THEM first
    // (registered later = takes precedence over the container handler);
    // otherwise the container's smart back returns to the last screen.
    BackHandler(enabled = searchActive || tripSheetOpen || homeWorkSheetPoint != null) {
        when {
            homeWorkSheetPoint != null -> homeWorkSheetPoint = null
            tripSheetOpen -> tripSheetOpen = false
            searchActive -> searchActive = false
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.map_pick_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Header cloud button: icon shows the CURRENT mode
                    // (cloud = online, cloud-off = offline); tap toggles
                    // with an Arabic confirmation Snackbar.
                    // (strings hoisted — stringResource is @Composable-only,
                    //  never inside the launch coroutine.)
                    val offlineOnMsg = stringResource(R.string.map_offline_on)
                    val offlineOffMsg = stringResource(R.string.map_offline_off)
                    OfflineModeButton(
                        isOffline = isOfflineMode,
                        onToggle = { enabled ->
                            viewModel.setOfflineMode(enabled)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (enabled) offlineOnMsg else offlineOffMsg,
                                )
                            }
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Phase 10-Fix: professional SearchBar — while active the results
        // take over the screen (map hidden, no overlap); picking a result
        // collapses the bar and reveals the map + marker. 100% MapLibre.
        val keyboardController = LocalSoftwareKeyboardController.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Persistent offline indicator — visible on every frame while
            // offline mode is ON (not a transient toast).
            if (isOfflineMode) {
                OfflineModeBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // TRIP SIMULATION: clearly-labeled test-mode strip +
            // prominent pause/stop bar (start lives in the panel below
            // while idle; hidden here while running by state).
            if (isSimulating) {
                SimulationBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                SimulationControlBar(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            SearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    searchActive = false
                    keyboardController?.hide()
                },
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = { Text(stringResource(R.string.map_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onQueryChange("")
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.map_clear_search))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // REDESIGN 10 Oct 2026: search mode — recent + Home/Work
                // shortcuts on top, big touch targets, clear
                // loading/empty/error states in Arabic. Logic untouched.
                Text(
                    text = stringResource(R.string.search_shortcuts_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HomeWorkQuickRow(
                    homeAddress = homeAddress,
                    workAddress = workAddress,
                    onHomeClick = { startQuickTrip(homeLat, homeLng, homeAddress) },
                    onWorkClick = { startQuickTrip(workLat, workLng, workAddress) },
                    onHomeEdit = { editTarget = "home" },
                    onWorkEdit = { editTarget = "work" },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                searchError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.search_loading),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else if (predictions.isEmpty() && query.trim().length >= 2 && searchError == null) {
                    Text(
                        text = stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        predictions,
                        key = { "${it.name}|${it.lat}|${it.lng}" },
                    ) { result ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    result.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = {
                                if (result.lat != null && result.lng != null) {
                                    Text("${result.lat}, ${result.lng}")
                                }
                            },
                            modifier = Modifier
                                .clickable {
                                    viewModel.fetchPlaceDetails(result)
                                    searchActive = false
                                    keyboardController?.hide()
                                    tripSheetOpen = true
                                }
                                .heightIn(min = 64.dp),
                        )
                    }
                }
            }
            if (!searchActive) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MapLibreMapView(
                        selectedLatLng = selectedLatLng,
                        routeGeoJson = routeGeoJson,
                        recenterTarget = recenterTarget,
                        cameraZoom = cameraZoom,
                        // ── OFFLINE BOUNDARY: traffic raster tiles are
                        // network (Google `/maps/vt`) — never rendered offline.
                        trafficEnabled = isTrafficEnabled && !isOfflineMode,
                        modifier = Modifier.fillMaxSize(),
                        onTrafficCameraSettled = { lat, lng, zoom ->
                            viewModel.onTrafficCameraSettled(lat, lng, zoom)
                        },
                        onMapClick = { latLng ->
                            viewModel.onMapClicked(latLng.latitude, latLng.longitude)
                            tripSheetOpen = true
                        },
                        // Long-press: select the point (reverse-geocode for the
                        // address line) + open the Home/Work bottom sheet.
                        onMapLongClick = { latLng ->
                            viewModel.onMapClicked(latLng.latitude, latLng.longitude)
                            tripSheetOpen = false
                            homeWorkSheetPoint = latLng
                        },
                    )
                    if (selectedLatLng == null && predictions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.map_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                        )
                    }
                    // Phase 10.4.3.1: My Location FAB (bottom-start, clear of
                    // the action FABs) — centers the camera on the live fix.
                    SmallFloatingActionButton(
                        onClick = { viewModel.recenterToMyLocation() },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_my_location))
                    }
                    // REDESIGN: map takes the full stage; FABs grouped
                    // bottom-end with 8dp spacing; restyled address card
                    // (elevated, headline, tappable → Home/Work sheet).
                    // My Location stays bottom-start — no overlap.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        addressLine?.let { line ->
                            Card(
                                onClick = {
                                    tripSheetOpen = true
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            ) {
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                        SmallFloatingActionButton(
                            onClick = { viewModel.setTrafficEnabled(!isTrafficEnabled) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Traffic,
                                contentDescription = if (isTrafficEnabled) {
                                    stringResource(R.string.map_traffic_on)
                                } else {
                                    stringResource(R.string.map_traffic_off)
                                },
                            )
                        }
                        // Add Destination — icon-only (visible only when a
                        // destination is selected; same save flow as before).
                        if (selectedLatLng != null && selectedName != null) {
                            FloatingActionButton(
                                onClick = { viewModel.saveDestination(onSaved) },
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.map_add_desc),
                                    )
                                }
                            }
                        }
                    }
                    // Zoom pill (user order Oct 2026): two compact +/- buttons
                    // in one slim pill — the number readout and slider are
                    // gone, the map keeps the stage.
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Elderly: touch targets grow to 64dp via dimens.
                            FilledTonalIconButton(
                                onClick = { viewModel.zoomIn() },
                                modifier = Modifier.size(com.app.wasalna.ui.theme.LocalWasalnaDimens.current.buttonHeight),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.map_zoom_in))
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.zoomOut() },
                                modifier = Modifier.size(com.app.wasalna.ui.theme.LocalWasalnaDimens.current.buttonHeight),
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.map_zoom_out))
                            }
                        }
                    }
                }
            }
        }
        // Map Home/Work from-map-point: bottom sheet with two BIG (64dp)
        // elderly-friendly buttons. Saves via the SAME
        // SettingsRepository.setHome/setWork storage as the search path —
        // additional path, not a replacement. Snackbar confirms + the
        // HomeWorkQuickRow StateFlows update immediately.
        // Proposal (أ): trip panel bottom sheet (destination header +
        // modes + transit + ETA + share + simulation + traffic).
        if (!searchActive && selectedLatLng != null && tripSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { tripSheetOpen = false },
                sheetState = tripSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 0.dp, vertical = 8.dp),
                ) {
            // Transit design system (Oct 2026): sheet status badge.
            if (isOfflineMode) {
                com.app.wasalna.ui.components.StatusBadge(
                    kind = com.app.wasalna.ui.components.BadgeKind.OFFLINE,
                    text = stringResource(R.string.badge_offline),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            addressLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            searchError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            selectedName?.let { name ->
                Text(
                    text = stringResource(R.string.map_selected, addressLine ?: name),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // Phase 10.4.0: Vela-style multimodal ETA selector. Changing
            // the mode recomputes route + ETA in the ViewModel.
            if (selectedLatLng != null) {
                // Road vehicles (segmented, max 4) + transit systems
                // (chips row below) — 8 modes total (Oct 2026).
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    val roadOptions = TransportMode.roadEntries
                    roadOptions.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = transportMode == mode,
                            onClick = { viewModel.setTransportMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = roadOptions.size,
                            ),
                            label = {
                                Text(
                                    when (mode) {
                                        TransportMode.WALK -> stringResource(R.string.transport_walk)
                                        TransportMode.CAR -> stringResource(R.string.transport_car)
                                        TransportMode.MOTORCYCLE -> stringResource(R.string.vehicle_motorcycle)
                                        TransportMode.BICYCLE -> stringResource(R.string.vehicle_bicycle)
                                        TransportMode.TRANSIT -> stringResource(R.string.vehicle_transit)
                                        TransportMode.METRO -> stringResource(R.string.vehicle_metro)
                                        TransportMode.MONORAIL -> stringResource(R.string.vehicle_monorail)
                                        TransportMode.TRAIN -> stringResource(R.string.vehicle_train_lrt)
                                    },
                                )
                            },
                        )
                    }
                }
                // Transit systems row (Oct 2026): generic live/approx
                // TRANSIT + static-data METRO / MONORAIL / LRT train.
                // Horizontal scroll keeps 48dp+ targets (RTL-safe).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransportMode.transitEntries.forEach { mode ->
                        FilterChip(
                            selected = transportMode == mode,
                            onClick = { viewModel.setTransportMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        TransportMode.TRANSIT -> stringResource(R.string.vehicle_transit)
                                        TransportMode.METRO -> stringResource(R.string.vehicle_metro)
                                        TransportMode.MONORAIL -> stringResource(R.string.vehicle_monorail)
                                        TransportMode.TRAIN -> stringResource(R.string.vehicle_train_lrt)
                                        else -> stringResource(R.string.vehicle_title)
                                    },
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                // Static transit preview card (METRO/MONORAIL/TRAIN):
                // planner summary + honesty label (ViewModel text).
                if (transportMode in TransportMode.staticTransitEntries) {
                    val staticText by viewModel.staticTransitText.collectAsState()
                    staticText?.let { summary ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                TransitApproxNote()
                            }
                        }
                    }
                }
                // TRANSIT planning surface (Oct 2026): live v2 plan
                // when the BYOK key delivered one; otherwise the
                // APPROXIMATE alighting hint near the destination +
                // honesty label (origin = live fix, unknown here).
                if (transportMode == TransportMode.TRANSIT && selectedLatLng != null) {
                    val livePlan by viewModel.transitPlan.collectAsState()
                    val live = livePlan
                    if (live != null) {
                        TransitLiveCard(
                            plan = live,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    } else {
                        val dest = selectedLatLng
                        if (dest != null) {
                            val near = remember(dest) {
                                CairoTransitStations.nearest(dest.latitude, dest.longitude)
                            }
                            TransitGuideCard(
                                boardingText = null,
                                alightingText = stringResource(
                                    R.string.transit_alight_at,
                                    near.station.displayName(),
                                ) + " " + stringResource(
                                    R.string.transit_walk_leg,
                                    walkKm(near.distanceM),
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                etaMinutes?.let { mins ->
                    // REDESIGN: prominent ETA hero chip (resource-based,
                    // no hardcoded literals) + readable traffic state.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.trips_eta, mins),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                // Phase 10.11: share the picked destination to the
                // user's own installed maps / ride apps (dynamic list).
                OutlinedButton(
                    onClick = { showDestinationShare = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.share_destination))
                }
                // TRIP SIMULATION (وضع محاكاة للرحلة للتجربة):
                // destination reused from the existing selection state.
                selectedLatLng?.let { point ->
                    TripSimulationPanel(
                        destLat = point.latitude,
                        destLng = point.longitude,
                        destName = addressLine ?: selectedName ?: "",
                        mode = transportMode,
                        routeGeoJson = routeGeoJson,
                        originProvider = { viewModel.peekCurrentFix() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            // Phase 10.8.0g (traffic): Vela live-traffic raster (Google
            // keyless `/maps/vt` tiles as a MapLibre RasterSource — see
            // TrafficOverlay.kt). Default OFF; Arabic disclosure below.
            // Never invent fake data: overlay only, no parsed segments.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = isTrafficEnabled,
                        onClick = { viewModel.setTrafficEnabled(!isTrafficEnabled) },
                        label = { Text(stringResource(R.string.traffic_congestion)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Traffic, contentDescription = null)
                        },
                    )
                    if (isTrafficEnabled) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Text(
                                text = when (trafficOverlayState) {
                                    is TrafficOverlayState.Live -> stringResource(R.string.traffic_live)
                                    is TrafficOverlayState.Stale -> stringResource(R.string.traffic_stale)
                                    is TrafficOverlayState.Checking -> stringResource(R.string.traffic_checking)
                                    else -> stringResource(R.string.traffic_unavailable)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.map_traffic_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        homeWorkSheetPoint?.let { point ->
            ModalBottomSheet(
                onDismissRequest = { homeWorkSheetPoint = null },
                sheetState = homeWorkSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // REDESIGN: restyled sheet header (larger, bolder).
                    Text(
                        text = addressLine ?: selectedName ?: stringResource(R.string.map_selected_point),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Button(
                        onClick = {
                            viewModel.saveMapPointAsHome(point.latitude, point.longitude)
                            homeWorkSheetPoint = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.map_set_home),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.saveMapPointAsWork(point.latitude, point.longitude)
                            homeWorkSheetPoint = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.map_set_work),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        // Phase 10.11: dynamic destination share (MapScreen selection
        // state reused — no duplicated storage; re-queries on every open).
        if (showDestinationShare) {
            val point = selectedLatLng
            val shareLabel = addressLine ?: selectedName ?: ""
            if (point != null) {
                DestinationShareSheet(
                    lat = point.latitude,
                    lng = point.longitude,
                    label = shareLabel,
                    onDismiss = { showDestinationShare = false },
                )
            } else {
                showDestinationShare = false
            }
        }
        // Map UX fixes: quick-place set dialog (search-mode entry point).
        // Prefills the current selection so a searched/tapped point can
        // become Home/Work in one step.
        editTarget?.let { target ->
            QuickPlaceEditDialog(
                title = if (target == "home") stringResource(R.string.quick_home) else stringResource(R.string.quick_work),
                initialAddress = if (target == "home") homeAddress else workAddress,
                initialLat = (if (target == "home") homeLat else workLat)
                    ?: selectedLatLng?.latitude,
                initialLng = (if (target == "home") homeLng else workLng)
                    ?: selectedLatLng?.longitude,
                onDismiss = { editTarget = null },
                onSave = { lat, lng, address ->
                    if (target == "home") viewModel.setHome(lat, lng, address)
                    else viewModel.setWork(lat, lng, address)
                },
            )
        }
    }
}

/**
 * Phase 10-1: free MapLibre engine (OpenFreeMap vector tiles).
 * Hosted in AndroidView with full lifecycle handling; marker via the
 * annotation plugin's SymbolManager; taps forward to onMapClick.
 * Search + Nominatim reverse-geocode logic is untouched (ViewModel).
 */
@Composable
private fun MapLibreMapView(
    selectedLatLng: LatLng?,
    routeGeoJson: String?,
    recenterTarget: LatLng? = null,
    cameraZoom: Float = 15f,
    trafficEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    onMapClick: (LatLng) -> Unit = {},
    onMapLongClick: (LatLng) -> Unit = {},
    onTrafficCameraSettled: (Double?, Double?, Float?) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var lastCameraTarget by remember { mutableStateOf<MapLibreLatLng?>(null) }
    var lastRouteApplied by remember { mutableStateOf<String?>(null) }
    var lastRecenterApplied by remember { mutableStateOf<LatLng?>(null) }
    // Map UX fixes: last zoom pushed to the camera (selector 3..19).
    var lastZoomApplied by remember { mutableStateOf<Float?>(null) }
    // Phase 10.8.0g (traffic): last applied overlay state (style can reload).
    var lastTrafficApplied by remember { mutableStateOf<Boolean?>(null) }

    val mapView = remember(context) {
        MapView(context).apply { onCreate(null) }
    }

    fun refreshMarker(target: LatLng?) {
        val manager = symbolManager ?: return
        runCatching {
            manager.deleteAll()
            target?.let {
                manager.create(
                    SymbolOptions()
                        .withLatLng(MapLibreLatLng(it.latitude, it.longitude))
                        .withIconImage(MARKER_IMAGE_ID),
                )
            }
        }
    }

    // Reduced motion (Oct 2026, elderly): camera jumps instead of flying.
    val reducedMotion = com.app.wasalna.ui.theme.LocalElderlyMode.current
    fun glideCamera(
        map: MapLibreMap,
        update: org.maplibre.android.camera.CameraUpdate,
        durationMs: Int,
    ) {
        runCatching {
            if (reducedMotion) map.moveCamera(update)
            else map.animateCamera(update, durationMs)
        }
    }
    fun moveCameraIfChanged(map: MapLibreMap, target: MapLibreLatLng, zoom: Double) {
        if (lastCameraTarget != target) {
            lastCameraTarget = target
            glideCamera(
                map,
                MapLibreCameraUpdateFactory.newLatLngZoom(target, zoom),
                600,
            )
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.getMapAsync { map ->
            mapRef = map
            map.setStyle(Style.Builder().fromUri(OPENFREEMAP_STYLE)) { style ->
                styleRef = style
                lastRouteApplied = null
                lastTrafficApplied = null
                // Phase 10.4.3.1: live blue dot (guarded by runtime permission).
                enableLocationComponent(context, map, style)
                runCatching { style.addImage(MARKER_IMAGE_ID, makeMarkerBitmap()) }
                // Phase 10-2: route source + styled line layer (created once
                // per style; source data updates dynamically below).
                runCatching {
                    if (style.getSource(ROUTE_SOURCE_ID) == null) {
                        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
                    }
                    if (style.getLayer(ROUTE_LAYER_ID) == null) {
                        style.addLayer(
                            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor("#1976D2"),
                                PropertyFactory.lineWidth(5f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                        )
                    }
                }
                routeGeoJson?.let { applyRoute(style, it) }
                lastRouteApplied = routeGeoJson
                // Phase 10.8.0g (traffic): apply overlay on fresh style.
                ensureTraffic(style, trafficEnabled)
                lastTrafficApplied = trafficEnabled
                symbolManager = SymbolManager(mapView, map, style).apply {
                    iconAllowOverlap = true
                }
                refreshMarker(selectedLatLng)
                val initial = selectedLatLng?.let {
                    MapLibreLatLng(it.latitude, it.longitude)
                } ?: DefaultCairoLibre
                map.cameraPosition = MapLibreCameraPosition.Builder()
                    .target(initial)
                    .zoom(if (selectedLatLng != null) 15.0 else 12.0)
                    .build()
                lastCameraTarget = initial
            }
            map.addOnMapClickListener { point ->
                onMapClick(LatLng(point.latitude, point.longitude))
                true
            }
            map.addOnMapLongClickListener { point ->
                onMapLongClick(LatLng(point.latitude, point.longitude))
                true
            }
            // Phase 10.8.0g (traffic): camera-idle forwards the settled
            // viewport for debounced health probes (ViewModel, ~1.5s).
            // Tiles themselves refresh via the engine on camera move.
            map.addOnCameraIdleListener {
                runCatching {
                    val pos = map.cameraPosition
                    val target = pos?.target
                    if (pos != null && target != null) {
                        onTrafficCameraSettled(
                            target.latitude,
                            target.longitude,
                            pos.zoom.toFloat(),
                        )
                    } else {
                        onTrafficCameraSettled(null, null, null)
                    }
                }
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { mapView.onDestroy() }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = {
            val map = mapRef ?: return@AndroidView
            // Phase 10-2: push a newly fetched route into the live source.
            val style = styleRef
            if (style != null && routeGeoJson != lastRouteApplied) {
                applyRoute(style, routeGeoJson)
                lastRouteApplied = routeGeoJson
            }
            // Phase 10.8.0g (traffic): Vela raster overlay on toggle/style.
            if (style != null && trafficEnabled != lastTrafficApplied) {
                ensureTraffic(style, trafficEnabled)
                lastTrafficApplied = trafficEnabled
            }
            // Phase 10.4.3.1: My Location jump — camera only, no selection.
            if (recenterTarget != null && recenterTarget != lastRecenterApplied) {
                lastRecenterApplied = recenterTarget
                glideCamera(
                    map,
                    MapLibreCameraUpdateFactory.newLatLngZoom(
                        MapLibreLatLng(recenterTarget.latitude, recenterTarget.longitude),
                        15.0,
                    ),
                    600,
                )
            }
            refreshMarker(selectedLatLng)
            val target = selectedLatLng?.let {
                MapLibreLatLng(it.latitude, it.longitude)
            } ?: DefaultCairoLibre
            // Map UX fixes: zoom selector drives the camera. Selection
            // moves keep their zoom; explicit selector changes animate.
            if (cameraZoom != lastZoomApplied) {
                lastZoomApplied = cameraZoom
                lastCameraTarget = target
                glideCamera(
                    map,
                    MapLibreCameraUpdateFactory.newLatLngZoom(
                        target,
                        cameraZoom.toDouble(),
                    ),
                    300,
                )
            } else {
                moveCameraIfChanged(map, target, cameraZoom.toDouble())
            }
        },
    )
}

/**
 * Phase 10.4.3.1: enables the MapLibre LocationComponent (live blue dot).
 * Requires granted fine location — silently skipped otherwise. The component
 * manages its own engine + compass rendering; camera stays manual (NONE).
 */
@SuppressLint("MissingPermission")
private fun enableLocationComponent(
    context: android.content.Context,
    map: MapLibreMap,
    style: Style,
) {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine) return
    runCatching {
        val options = LocationComponentActivationOptions.builder(context, style).build()
        map.locationComponent.activateLocationComponent(options)
        map.locationComponent.isLocationComponentEnabled = true
        map.locationComponent.cameraMode = CameraMode.NONE
        map.locationComponent.renderMode = RenderMode.NORMAL
    }.onFailure {
        Log.w("MapLibreLocation", "location component failed: ${it.message}")
    }
}

/** Phase 10.4.0: human-friendly ETA — minutes under 2h, else H h M min. */
/** Straight-line meters → "1.2" km string (US locale, caller adds the unit resource). */
private fun walkKm(distanceM: Float): String =
    String.format(java.util.Locale.US, "%.1f", distanceM / 1000f)

private fun formatEta(mins: Int): String {
    if (mins < 120) return "$mins min${if (mins == 1) "" else "s"}"
    val h = mins / 60
    val m = mins % 60
    return if (m == 0) "$h h" else "$h h $m min"
}

/**
 * Phase 10-2: writes route GeoJSON into the live "route-source".
 * Null/blank clears the line. Never throws — logs and skips on failure.
 */
private fun applyRoute(style: Style, geoJson: String?) {
    runCatching {
        val source = style.getSource(ROUTE_SOURCE_ID) as? GeoJsonSource ?: return
        source.setGeoJson(geoJson?.ifBlank { EMPTY_FEATURE_COLLECTION } ?: EMPTY_FEATURE_COLLECTION)
    }
}

/** Programmatic pin bitmap (red dot, white core) — no drawable resource needed. */
private fun makeMarkerBitmap(): android.graphics.Bitmap {
    val size = 96
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val outer = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#D32F2F")
        isAntiAlias = true
    }
    val inner = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, outer)
    canvas.drawCircle(size / 2f, size / 2f, size / 5f, inner)
    return bmp
}
