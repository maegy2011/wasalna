package com.app.wasalna.ui.screens.map

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R
import com.app.wasalna.domain.model.displayName

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.app.wasalna.ui.components.WasalnaTopBar
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
import com.app.wasalna.domain.model.CairoTransitStations
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.ui.ads.InterstitialHelper
import com.app.wasalna.ui.components.TransitApproxNote
import com.app.wasalna.ui.components.TransitGuideCard
import com.app.wasalna.ui.components.TransitLiveCard
import com.app.wasalna.ui.components.HomeWorkQuickRow
import com.app.wasalna.ui.components.OfflineModeBanner
import com.app.wasalna.ui.components.OfflineModeButton
import com.app.wasalna.ui.screens.simulation.TripSimulatorDialog
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
import com.app.wasalna.data.service.TripStarter
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
private const val ROUTE_CASING_LAYER_ID = "route-casing-layer"
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
    // Live follow (user order Oct 2026): fired on a REAL trip start so
    // the container opens the live TripRoute view for the active trip.
    onTripStarted: () -> Unit = {},
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
    // Via stops (Oct 2026, offline edit): chained into road routes.
    val viaPoints by viewModel.viaPoints.collectAsState()
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
    // Map layers (Oct 2026): transit vector + AQI raster (WAQI BYOK).
    val isTransitLayer by viewModel.isTransitLayer.collectAsState()
    val isAqiLayer by viewModel.isAqiLayer.collectAsState()
    val waqiKey by viewModel.waqiKey.collectAsState()
    // AQI upgrade: numeric value at the selected destination.
    val destinationAqi by viewModel.destinationAqi.collectAsState()
    var layersOpen by remember { mutableStateOf(false) }
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
    // Trip simulator (user order Oct 2026): setting-gated visibility.
    val isSimulationVisible by viewModel.isSimulationVisible.collectAsState()
    var simOpen by remember { mutableStateOf(false) }
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
    // Voice search launcher (Oct 2026): transcript → query, bar stays open.
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val heard = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }
            if (heard != null) {
                viewModel.onQueryChange(heard)
                searchActive = true
            }
        }
    }

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
            WasalnaTopBar(
                title = stringResource(R.string.map_pick_title),
                showBack = true,
                onBackClick = onBack,
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
                    } else {
                        // Voice search (Oct 2026): system recognizer (Arabic)
                        // → transcript straight into the query. No RECORD_
                        // AUDIO permission needed (system dialog owns the mic).
                        IconButton(onClick = {
                            if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                                viewModel.voiceNotAvailable()
                            } else {
                                val voiceIntent = android.content.Intent(
                                    android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
                                ).apply {
                                    putExtra(
                                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    )
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar")
                                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                    putExtra(
                                        android.speech.RecognizerIntent.EXTRA_PROMPT,
                                        context.getString(R.string.voice_prompt),
                                    )
                                }
                                runCatching { voiceLauncher.launch(voiceIntent) }
                                    .onFailure { viewModel.voiceNotAvailable() }
                            }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_search_desc))
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
                        viaPoints = viaPoints.map {
                            com.google.android.gms.maps.model.LatLng(it.lat, it.lng)
                        },
                        transitLayerOn = isTransitLayer,
                        aqiLayerOn = isAqiLayer && waqiKey.isNotBlank() && !isOfflineMode,
                        aqiToken = waqiKey,
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
                        // Long-press: with NO destination yet it selects the
                        // point (old behavior: reverse-geocode + Home/Work
                        // sheet); once a destination exists it adds a VIA
                        // STOP instead (Oct 2026, offline edit — works
                        // fully offline, chained in OSRM/GraphHopper).
                        onMapLongClick = { latLng ->
                            if (selectedLatLng != null) {
                                viewModel.addViaPoint(latLng.latitude, latLng.longitude)
                            } else {
                                viewModel.onMapClicked(latLng.latitude, latLng.longitude)
                                tripSheetOpen = false
                                homeWorkSheetPoint = latLng
                            }
                        },
                    )
                    // Fixed map hint (user order Oct 2026): static pill,
                    // top-center, always visible over the map — never moves,
                    // never disappears (independent of selection/sheets).
                    Text(
                        text = stringResource(R.string.map_fixed_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                RoundedCornerShape(20.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // Via stops row (Oct 2026, offline edit): one chip per
                    // stop (tap ✕ removes) + clear-all. Top-start so it
                    // never collides with the trip bottom sheet.
                    if (viaPoints.isNotEmpty()) {
                        ViaStopsRow(
                            stops = viaPoints,
                            onRemove = { viewModel.removeViaPoint(it) },
                            onClear = { viewModel.clearViaPoints() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                        )
                    }
                    if (selectedLatLng == null && predictions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.map_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                        )
                    }
                    // AQI upgrade: live value chip under the fixed hint —
                    // colored dot + number + EPA band at the destination.
                    destinationAqi?.let { aqi ->
                        AqiValueChip(
                            aqi = aqi,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 60.dp),
                        )
                    }
                    // Phase 10.4.3.1: My Location FAB (bottom-start, clear of
                    // the action FABs) — centers the camera on the live fix.
                    SmallFloatingActionButton(
                        onClick = { viewModel.recenterToMyLocation() },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
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
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
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
                        // Layers (Oct 2026): one button opens the sheet with
                        // traffic + transit + air-quality toggles.
                        SmallFloatingActionButton(
                            onClick = {
                                viewModel.refreshLayerPrefs()
                                layersOpen = true
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
                        ) {
                            Icon(
                                Icons.Filled.Layers,
                                contentDescription = stringResource(R.string.layers_title),
                            )
                        }
                        // Trip simulator (user order Oct 2026): own button,
                        // standalone screen — visible ONLY when enabled in
                        // Settings → Advanced, and only with a destination
                        // selected (the simulator needs one to run).
                        if (isSimulationVisible && selectedLatLng != null) {
                            SmallFloatingActionButton(
                                onClick = { simOpen = true },
                                modifier = Modifier.size(48.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
                            ) {
                                Icon(
                                    Icons.Filled.Science,
                                    contentDescription = stringResource(R.string.sim_button_desc),
                                )
                            }
                        }
                        // Add Destination — icon-only (visible only when a
                        // destination is selected; same save flow as before).
                        if (selectedLatLng != null && selectedName != null) {
                            FloatingActionButton(
                                onClick = { viewModel.saveDestination(onSaved) },
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
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
                    // Zoom controls (user order Oct 2026): NO background
                    // panel — two floating translucent buttons straight on
                    // the map (premium glass feel, zero map occlusion).
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Elderly: touch targets grow to 64dp via dimens.
                        FilledTonalIconButton(
                            onClick = { viewModel.zoomIn() },
                            modifier = Modifier.size(com.app.wasalna.ui.theme.LocalWasalnaDimens.current.buttonHeight),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                            ),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.map_zoom_in))
                        }
                        FilledTonalIconButton(
                            onClick = { viewModel.zoomOut() },
                            modifier = Modifier.size(com.app.wasalna.ui.theme.LocalWasalnaDimens.current.buttonHeight),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                            ),
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.map_zoom_out))
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
                // Transit design: alighting stop — extra-large bold,
                // instantly scannable for a waking user.
                val elderlyNow = com.app.wasalna.ui.theme.LocalElderlyMode.current
                com.app.wasalna.ui.theme.HeroText(
                    text = stringResource(R.string.map_selected, addressLine ?: name),
                    baseStyle = if (elderlyNow) com.app.wasalna.ui.theme.ElderlyStopDisplayStyle else com.app.wasalna.ui.theme.StopDisplayStyle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // Phase 10.4.0: Vela-style multimodal ETA selector. Changing
            // the mode recomputes route + ETA in the ViewModel.
            if (selectedLatLng != null) {
                // Start-trip CTA (user order Oct 2026): a destination pick
                // must offer a DIRECT way to begin tracking — previously the
                // sheet only saved / shared, and the bottom-bar play icon
                // just reopened the map. Funnels through TripStarter's
                // pre-flight gate (GPS / permission / sim-conflict Toasts).
                Button(
                    onClick = {
                        val dest = selectedLatLng
                        val name = addressLine ?: selectedName
                        if (dest != null && name != null) {
                            val started = TripStarter.startRealTrip(
                                context, dest.latitude, dest.longitude, name, transportMode,
                            )
                            if (started) {
                                tripSheetOpen = false
                                onTripStarted()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nav_start_trip),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                // Four choices only (user order Oct 2026): car,
                // motorcycle, public transit, walk — one segmented row.
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    val roadOptions = TransportMode.primaryEntries
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
                // Public-transit preview card: the ViewModel plans the
                // best system (live when keyed, else static metro/monorail/
                // LRT) — summary + honesty label (ViewModel text).
                if (transportMode == TransportMode.TRANSIT) {
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
                        // Transit design: ETA hero — extra-large bold.
                        val elderlyEta = com.app.wasalna.ui.theme.LocalElderlyMode.current
                        com.app.wasalna.ui.theme.HeroText(
                            text = stringResource(R.string.trips_eta, mins),
                            baseStyle = if (elderlyEta) com.app.wasalna.ui.theme.ElderlyEtaDisplayStyle else com.app.wasalna.ui.theme.EtaDisplayStyle,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                // Phase 10.11: share the picked destination to the
                // user's own installed maps / ride apps (dynamic list).
                OutlinedButton(
                    // User order Oct 2026: sharing = First Class.
                    onClick = {
                        if (!isPremium) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.share_premium_only),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            showDestinationShare = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.share_destination))
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
                    // REDESIGN: restyled sheet header — stop-display size.
                    val elderlyHw = com.app.wasalna.ui.theme.LocalElderlyMode.current
                    com.app.wasalna.ui.theme.HeroText(
                        text = addressLine ?: selectedName ?: stringResource(R.string.map_selected_point),
                        baseStyle = if (elderlyHw) com.app.wasalna.ui.theme.ElderlyStopDisplayStyle else com.app.wasalna.ui.theme.StopDisplayStyle,
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
        // Standalone trip simulator (user order Oct 2026): the dialog
        // owns banner + controls + panel; destination comes from the
        // current map selection (button only shows with one).
        if (simOpen) {
            val point = selectedLatLng
            if (point != null) {
                TripSimulatorDialog(
                    destLat = point.latitude,
                    destLng = point.longitude,
                    destName = addressLine ?: selectedName ?: "",
                    mode = transportMode,
                    routeGeoJson = routeGeoJson,
                    originProvider = { viewModel.peekCurrentFix() },
                    onDismiss = { simOpen = false },
                )
            } else {
                simOpen = false
            }
        }
        // Map layers sheet (Oct 2026): traffic + transit + air-quality.
        if (layersOpen) {
            MapLayersSheet(
                trafficOn = isTrafficEnabled,
                onTraffic = { viewModel.setTrafficEnabled(it) },
                transitOn = isTransitLayer,
                onTransit = { viewModel.setTransitLayer(it) },
                aqiOn = isAqiLayer,
                onAqi = { viewModel.setAqiLayer(it) },
                waqiConfigured = waqiKey.isNotBlank(),
                onDismiss = { layersOpen = false },
            )
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
    viaPoints: List<LatLng> = emptyList(),
    transitLayerOn: Boolean = false,
    aqiLayerOn: Boolean = false,
    aqiToken: String = "",
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
    // Map layers (Oct 2026): transit + AQI last-applied (token included —
    // a changed token rebuilds the raster source).
    var lastTransitApplied by remember { mutableStateOf<Boolean?>(null) }
    var lastAqiApplied by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val mapView = remember(context) {
        MapView(context).apply { onCreate(null) }
    }

    // Destination pin (red) + via stops (blue, Oct 2026 offline edit).
    fun refreshMarker(target: LatLng?, via: List<LatLng> = emptyList()) {
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
            via.forEach {
                manager.create(
                    SymbolOptions()
                        .withLatLng(MapLibreLatLng(it.latitude, it.longitude))
                        .withIconImage(VIA_IMAGE_ID),
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
                lastTransitApplied = null
                lastAqiApplied = null
                // Phase 10.4.3.1: live blue dot (guarded by runtime permission).
                enableLocationComponent(context, map, style)
                runCatching { style.addImage(MARKER_IMAGE_ID, makeMarkerBitmap()) }
                runCatching { style.addImage(VIA_IMAGE_ID, makeViaBitmap()) }
                // Phase 10-2: route source + styled line layer (created once
                // per style; source data updates dynamically below).
                runCatching {
                    if (style.getSource(ROUTE_SOURCE_ID) == null) {
                        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
                    }
                    // Route drawing fix (Oct 2026): white casing UNDER the
                    // blue line — visible on any basemap, round joints so
                    // via-point corners don't spike. Same source, two layers.
                    if (style.getLayer(ROUTE_CASING_LAYER_ID) == null) {
                        style.addLayer(
                            LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor("#FFFFFF"),
                                PropertyFactory.lineWidth(9f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                                PropertyFactory.lineOpacity(0.9f),
                            ),
                        )
                    }
                    if (style.getLayer(ROUTE_LAYER_ID) == null) {
                        style.addLayerAbove(
                            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor("#1976D2"),
                                PropertyFactory.lineWidth(5f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                            ROUTE_CASING_LAYER_ID,
                        )
                    }
                }
                routeGeoJson?.let { applyRoute(style, it) }
                lastRouteApplied = routeGeoJson
                // Phase 10.8.0g (traffic): apply overlay on fresh style.
                ensureTraffic(style, trafficEnabled)
                lastTrafficApplied = trafficEnabled
                // Map layers (Oct 2026): transit vector (offline-safe) +
                // AQI raster (token-gated) on fresh style.
                ensureTransitLayer(style, transitLayerOn)
                lastTransitApplied = transitLayerOn
                ensureAqiLayer(style, aqiLayerOn, aqiToken)
                lastAqiApplied = aqiLayerOn to aqiToken
                symbolManager = SymbolManager(mapView, map, style).apply {
                    iconAllowOverlap = true
                }
                refreshMarker(selectedLatLng, viaPoints)
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
            // Map layers (Oct 2026): re-apply on toggle or token change.
            if (style != null && transitLayerOn != lastTransitApplied) {
                ensureTransitLayer(style, transitLayerOn)
                lastTransitApplied = transitLayerOn
            }
            val aqiState = aqiLayerOn to aqiToken
            if (style != null && aqiState != lastAqiApplied) {
                ensureAqiLayer(style, aqiLayerOn, aqiToken)
                lastAqiApplied = aqiState
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
            refreshMarker(selectedLatLng, viaPoints)
            val target = selectedLatLng?.let {
                MapLibreLatLng(it.latitude, it.longitude)
            } ?: DefaultCairoLibre
            // Map UX fixes: zoom selector drives the camera. Selection
            // moves keep their zoom; explicit selector changes animate.
            // Zoom-in-place fix (user order Oct 2026): +/- must zoom on
            // the CURRENT camera position — never teleport back to the
            // selection or the default city (that was the reported bug).
            if (cameraZoom != lastZoomApplied) {
                lastZoomApplied = cameraZoom
                val current = map.cameraPosition?.target ?: target
                lastCameraTarget = current
                glideCamera(
                    map,
                    MapLibreCameraUpdateFactory.newLatLngZoom(
                        current,
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

/**
 * Map layers sheet (Oct 2026): three switch rows — traffic (online),
 * transit metro (offline-safe), air quality (online, WAQI BYOK).
 * Material3, 48dp+ targets, RTL-safe, all strings resourced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapLayersSheet(
    trafficOn: Boolean,
    onTraffic: (Boolean) -> Unit,
    transitOn: Boolean,
    onTransit: (Boolean) -> Unit,
    aqiOn: Boolean,
    onAqi: (Boolean) -> Unit,
    waqiConfigured: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.layers_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LayerRow(
                title = stringResource(R.string.layer_traffic),
                sub = stringResource(R.string.layer_traffic_sub),
                checked = trafficOn,
                onChecked = onTraffic,
            )
            LayerRow(
                title = stringResource(R.string.layer_transit),
                sub = stringResource(R.string.layer_transit_sub),
                checked = transitOn,
                onChecked = onTransit,
            )
            LayerRow(
                title = stringResource(R.string.layer_aqi),
                sub = if (waqiConfigured) {
                    stringResource(R.string.layer_aqi_sub)
                } else {
                    stringResource(R.string.layer_aqi_need_token)
                },
                checked = aqiOn && waqiConfigured,
                onChecked = onAqi,
            )
            // AQI upgrade: EPA color-scale legend under the toggle.
            if (aqiOn && waqiConfigured) {
                AqiLegendCard()
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LayerRow(
    title: String,
    sub: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/**
 * Via stops row (Oct 2026, offline edit): one chip per stop (✕ removes
 * that leg) + a clear-all chip. Shown only while stops exist.
 * Material3, 48dp targets, RTL-safe, all strings resourced.
 */
@Composable
private fun ViaStopsRow(
    stops: List<ViaPoint>,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(stops, key = { i, s -> "$i|${s.lat}|${s.lng}" }) { i, _ ->
            FilterChip(
                selected = true,
                onClick = { onRemove(i) },
                label = {
                    Text(stringResource(R.string.via_stop, i + 1))
                },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.via_remove),
                    )
                },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onClear,
                label = {
                    Text(stringResource(R.string.via_clear))
                },
            )
        }
    }
}

/** Programmatic pin bitmap (white casing layer needs no image). */
private const val VIA_IMAGE_ID = "via-marker"

/** Programmatic pin bitmap (red dot, white core) — no drawable resource needed. */
private fun makeMarkerBitmap(): android.graphics.Bitmap =
    makeDotBitmap(android.graphics.Color.parseColor("#D32F2F"))

/** Via-stop dot (Oct 2026, offline edit): blue twin of the red pin. */
private fun makeViaBitmap(): android.graphics.Bitmap =
    makeDotBitmap(android.graphics.Color.parseColor("#1976D2"))

private fun makeDotBitmap(outerColor: Int): android.graphics.Bitmap {
    val size = 96
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val outer = android.graphics.Paint().apply {
        color = outerColor
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
