package com.app.wasalna.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.app.wasalna.data.local.datastore.SettingsDataStore.MapEngines
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker as OsmMarker

private val DefaultCairo = LatLng(30.0444, 31.2357)
private val DefaultCairoGeo = GeoPoint(30.0444, 31.2357)

/**
 * Phase 4: destination picker — search at top, predictions dropdown,
 * map filling the rest, Save FAB enabled only when a place is selected.
 * Phase 9-1: dual map engine — Free (OSM) default + Google for premium,
 * with a top toggle and graceful Google→OSM fallback.
 * Phase 9-2: tap-to-select on both engines + reverse-geocoded address
 * shown in/above the search bar and above the Save FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onSaved: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val query by viewModel.searchQuery.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val selectedName by viewModel.selectedName.collectAsState()
    val selectedAddress by viewModel.selectedAddress.collectAsState()
    val selectedLatLng by viewModel.selectedLatLng.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val activeEngine by viewModel.activeMapEngine.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLatLng ?: DefaultCairo, 12f)
    }

    // Move Google camera whenever a place is selected (search or tap).
    LaunchedEffect(selectedLatLng) {
        selectedLatLng?.let {
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
        }
    }

    // Phase 9-2: address line shown in/above search + above Save FAB.
    // Save uses this address as the Location name for map taps.
    val addressLine: String? = selectedAddress ?: selectedName

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Pick Destination") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Enabled only when a destination is selected (hidden otherwise).
            if (selectedLatLng != null && selectedName != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    addressLine?.let { line ->
                        Card(
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.saveDestination(onSaved) },
                        icon = {
                            if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp)
                            else Icon(Icons.Default.Check, contentDescription = null)
                        },
                        text = { Text(if (isSaving) "Saving…" else "Save Destination") },
                    )
                }
            }
        },
    ) { padding ->
        // Phase 9-3-1-fix: Box layout — map FIRST (background), floating
        // search Column SECOND with zIndex(1f) so the dropdown floats ABOVE
        // the map on both engines instead of being covered by it.
        val keyboardController = LocalSoftwareKeyboardController.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // --- Child 1 (background): the map fills everything. ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (activeEngine == MapEngines.GOOGLE) {
                    GoogleMapBranch(
                        selectedLatLng = selectedLatLng,
                        selectedName = addressLine,
                        cameraPositionState = cameraPositionState,
                        onGoogleFailed = { viewModel.setMapEngine(MapEngines.OSM) },
                        onMapClick = { latLng ->
                            viewModel.onMapClicked(latLng.latitude, latLng.longitude)
                        },
                    )
                } else {
                    OsmMapView(
                        selectedLatLng = selectedLatLng,
                        selectedName = addressLine,
                        modifier = Modifier.fillMaxSize(),
                        onMapClick = { latLng ->
                            viewModel.onMapClicked(latLng.latitude, latLng.longitude)
                        },
                    )
                }
                if (selectedLatLng == null && predictions.isEmpty()) {
                    Text(
                        text = "Search above or tap the map to pick a destination",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                }
            }
            // --- Child 2 (foreground): floating search overlay. ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            ) {
            // Phase 9-1: engine toggle. Google locked for non-premium.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = activeEngine == MapEngines.OSM,
                    onClick = { viewModel.setMapEngine(MapEngines.OSM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = activeEngine == MapEngines.OSM) {
                            Icon(Icons.Default.Map, contentDescription = null)
                        }
                    },
                    label = { Text("Free Map") },
                )
                SegmentedButton(
                    selected = activeEngine == MapEngines.GOOGLE,
                    onClick = {
                        if (!isPremium) {
                            scope.launch {
                                snackbarHostState.showSnackbar(MapViewModel.MSG_UPGRADE_FOR_GOOGLE)
                            }
                        } else {
                            viewModel.setMapEngine(MapEngines.GOOGLE)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = activeEngine == MapEngines.GOOGLE) {
                            if (!isPremium) {
                                Icon(Icons.Default.Lock, contentDescription = "Premium only")
                            } else {
                                Icon(Icons.Default.Map, contentDescription = null)
                            }
                        }
                    },
                    label = { Text("Google Map") },
                )
            }

            // Phase 9-2: selected address echoed in/above the search bar.
            addressLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text("Search destination") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onQueryChange("")
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
            )

            // Phase 9-3-1-fix: results dropdown floats ABOVE the map — solid
            // surface background + 8.dp elevation + scrollable heightIn(350.dp)
            // so 5+ results show comfortably without covering the screen.
            if (predictions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 350.dp),
                    ) {
                        items(predictions, key = { it.placeId }) { prediction ->
                            ListItem(
                                headlineContent = {
                                    Text(prediction.getPrimaryText(null).toString())
                                },
                                supportingContent = {
                                    Text(prediction.getSecondaryText(null).toString())
                                },
                                modifier = Modifier.clickable {
                                    // a. fetch + marker update (ViewModel also clears
                                    //    predictions so the dropdown collapses).
                                    viewModel.fetchPlaceDetails(prediction)
                                    // c. hide the software keyboard.
                                    keyboardController?.hide()
                                },
                            )
                        }
                    }
                }
            }

            selectedName?.let { name ->
                Text(
                    text = "Selected: ${addressLine ?: name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            } // floating search Column
        } // background/foreground Box
    }
}

/**
 * Phase 9-1: Google branch. Before rendering, MapsInitializer is probed
 * inside runCatching — any init exception falls back to the free OSM
 * engine instead of crashing the screen.
 * Phase 9-2: onMapClick forwards taps to MapViewModel.onMapClicked.
 */
@Composable
private fun GoogleMapBranch(
    selectedLatLng: LatLng?,
    selectedName: String?,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    onGoogleFailed: () -> Unit,
    onMapClick: (LatLng) -> Unit,
) {
    val context = LocalContext.current
    var googleReady by remember { mutableStateOf(false) }
    var googleFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            com.google.android.gms.maps.MapsInitializer.initialize(context)
        }.onSuccess {
            googleReady = true
        }.onFailure {
            googleFailed = true
            onGoogleFailed()
        }
    }

    if (googleFailed) {
        OsmMapView(
            selectedLatLng = selectedLatLng,
            selectedName = selectedName,
            modifier = Modifier.fillMaxSize(),
            onMapClick = onMapClick,
        )
        return
    }
    if (!googleReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapClick = onMapClick,
    ) {
        selectedLatLng?.let { latLng ->
            Marker(
                state = MarkerState(position = latLng),
                title = selectedName,
            )
        }
    }
}

/**
 * Phase 9-1: free OSM engine. MAPNIK tiles, multi-touch, marker at the
 * selected LatLng, camera recenters on selection. Lifecycle-aware with
 * onDetach() on dispose.
 * Phase 9-2: tap-to-select via MapEventsOverlay. The overlay is recreated
 * on every update block (never reused across recompositions) so its
 * receiver always captures the latest onMapClick closure.
 */
@Composable
private fun OsmMapView(
    selectedLatLng: LatLng?,
    selectedName: String?,
    modifier: Modifier = Modifier,
    onMapClick: (LatLng) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(DefaultCairoGeo)
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            // CRITICAL OSM LESSON: recreate the events overlay every update —
            // reusing one instance captures a stale onMapClick closure and
            // taps get ignored.
            view.overlays.removeAll { it is MapEventsOverlay }
            val receiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    p?.let { onMapClick(LatLng(it.latitude, it.longitude)) }
                    return true
                }

                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }
            view.overlays.add(0, MapEventsOverlay(receiver))

            // Marker refresh: clear old markers, add the new one.
            val target = selectedLatLng?.let {
                GeoPoint(it.latitude, it.longitude)
            } ?: DefaultCairoGeo
            view.overlays.removeAll { it is OsmMarker }
            val marker = OsmMarker(view).apply {
                position = target
                title = selectedName
                setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
            }
            view.overlays.add(marker)
            view.controller.setZoom(if (selectedLatLng != null) 15.0 else 12.0)
            view.controller.setCenter(target)
            view.invalidate()
        },
    )
}
