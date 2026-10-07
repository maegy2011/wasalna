package com.app.wasalna.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
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
import androidx.compose.runtime.setValue
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
import com.google.android.gms.maps.model.LatLng
import org.maplibre.android.camera.CameraPosition as MapLibreCameraPosition
import org.maplibre.android.camera.CameraUpdateFactory as MapLibreCameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

private val DefaultCairoLibre = MapLibreLatLng(30.0444, 31.2357)

/** Phase 10-1: free vector style — Google-like tiles, no API key. */
private const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val MARKER_IMAGE_ID = "wasalna-marker"
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
) {
    val query by viewModel.searchQuery.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val routeGeoJson by viewModel.routeGeoJson.collectAsState()
    val selectedName by viewModel.selectedName.collectAsState()
    val selectedAddress by viewModel.selectedAddress.collectAsState()
    val selectedLatLng by viewModel.selectedLatLng.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Phase 10-Fix: SearchBar active state — while typing, results take
    // over the screen (map hidden); picking a result reveals map + marker.
    var searchActive by remember { mutableStateOf(false) }

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
        // Phase 10-Fix: professional SearchBar — while active the results
        // take over the screen (map hidden, no overlap); picking a result
        // collapses the bar and reveals the map + marker. 100% MapLibre.
        val keyboardController = LocalSoftwareKeyboardController.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    searchActive = false
                    keyboardController?.hide()
                },
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = { Text("Search destination") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                searchError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        predictions,
                        key = { "${it.name}|${it.lat}|${it.lng}" },
                    ) { result ->
                        ListItem(
                            headlineContent = { Text(result.name) },
                            supportingContent = {
                                if (result.lat != null && result.lng != null) {
                                    Text("${result.lat}, ${result.lng}")
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.fetchPlaceDetails(result)
                                searchActive = false
                                keyboardController?.hide()
                            },
                        )
                    }
                }
            }
            if (!searchActive) {
                addressLine?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                searchError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                selectedName?.let { name ->
                    Text(
                        text = "Selected: ${addressLine ?: name}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    MapLibreMapView(
                        selectedLatLng = selectedLatLng,
                        routeGeoJson = routeGeoJson,
                        modifier = Modifier.fillMaxSize(),
                        onMapClick = { latLng ->
                            viewModel.onMapClicked(latLng.latitude, latLng.longitude)
                        },
                    )
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
            }
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
    modifier: Modifier = Modifier,
    onMapClick: (LatLng) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var lastCameraTarget by remember { mutableStateOf<MapLibreLatLng?>(null) }
    var lastRouteApplied by remember { mutableStateOf<String?>(null) }

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

    fun moveCameraIfChanged(map: MapLibreMap, target: MapLibreLatLng, zoom: Double) {
        if (lastCameraTarget != target) {
            lastCameraTarget = target
            runCatching {
                map.animateCamera(
                    MapLibreCameraUpdateFactory.newLatLngZoom(target, zoom),
                    600,
                )
            }
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
            refreshMarker(selectedLatLng)
            val target = selectedLatLng?.let {
                MapLibreLatLng(it.latitude, it.longitude)
            } ?: DefaultCairoLibre
            moveCameraIfChanged(map, target, if (selectedLatLng != null) 15.0 else 12.0)
        },
    )
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
