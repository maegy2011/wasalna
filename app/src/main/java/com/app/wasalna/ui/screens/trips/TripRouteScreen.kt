package com.app.wasalna.ui.screens.trips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.app.wasalna.ui.components.DestinationShareSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.app.wasalna.domain.model.RoutePoint
import com.app.wasalna.ui.screens.offline.OPENFREEMAP_STYLE_URL
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val ROUTE_SRC = "trip-route-src"
private const val ROUTE_LAYER = "trip-route-layer"
private const val DEST_IMAGE = "trip-dest-marker"

/**
 * Phase 10.4.3.8: live trip route map. ACTIVE trips redraw as the service
 * inserts breadcrumbs; PAST trips render their recorded path once.
 * Source + layer are created once per style — point updates only rewrite
 * the GeoJSON payload (main thread, via Compose effects).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripRouteScreen(
    onBack: () -> Unit = {},
    viewModel: TripRouteViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val trip by viewModel.trip.collectAsState()
    val points by viewModel.points.collectAsState()

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var cameraFitted by remember { mutableStateOf(false) }
    // Phase 10.11: dynamic destination share sheet (re-queries on open).
    var showDestinationShare by remember { mutableStateOf(false) }

    // Reactive redraw: every breadcrumb emission rebuilds the LineString.
    LaunchedEffect(points, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        if (points.size < 2) return@LaunchedEffect
        runCatching {
            val src = style.getSource(ROUTE_SRC) as? GeoJsonSource ?: return@LaunchedEffect
            src.setGeoJson(lineStringGeoJson(points))
        }
    }

    // First fit: destination + travelled path in frame, then hands off.
    LaunchedEffect(points, trip, mapRef, styleRef) {
        if (cameraFitted) return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val dest = trip ?: return@LaunchedEffect
        if (styleRef == null) return@LaunchedEffect
        if (points.isEmpty()) return@LaunchedEffect
        runCatching {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(dest.destLat, dest.destLng))
            points.forEach { bounds.include(LatLng(it.lat, it.lng)) }
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
            cameraFitted = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.locationName ?: "Trip route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        onCreate(null)
                        val observer = object : DefaultLifecycleObserver {
                            override fun onStart(owner: LifecycleOwner) = onStart()
                            override fun onResume(owner: LifecycleOwner) = onResume()
                            override fun onPause(owner: LifecycleOwner) = onPause()
                            override fun onStop(owner: LifecycleOwner) = onStop()
                            override fun onDestroy(owner: LifecycleOwner) = onDestroy()
                        }
                        lifecycle.addObserver(observer)
                        getMapAsync { map ->
                            mapRef = map
                            map.setStyle(
                                Style.Builder().fromUri(OPENFREEMAP_STYLE_URL),
                            ) { style ->
                                styleRef = style
                                runCatching {
                                    if (style.getSource(ROUTE_SRC) == null) {
                                        style.addSource(GeoJsonSource(ROUTE_SRC))
                                    }
                                    if (style.getLayer(ROUTE_LAYER) == null) {
                                        style.addLayer(
                                            LineLayer(ROUTE_LAYER, ROUTE_SRC).withProperties(
                                                PropertyFactory.lineColor("#0D7C66"),
                                                PropertyFactory.lineWidth(5f),
                                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                                            ),
                                        )
                                    }
                                    style.addImage(DEST_IMAGE, destMarkerBitmap())
                                }
                                // Destination pin (recreated per style load).
                                val dest = trip
                                if (dest != null) {
                                    runCatching {
                                        SymbolManager(this, map, style).apply {
                                            iconAllowOverlap = true
                                            textAllowOverlap = true
                                        }.create(
                                            SymbolOptions()
                                                .withLatLng(LatLng(dest.destLat, dest.destLng))
                                                .withIconImage(DEST_IMAGE),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Header overlay: name + status + ETA + breadcrumb count.
            trip?.let { t ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${t.transportMode.icon} ${t.locationName}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${t.status} • " +
                                (t.etaMinutes?.let { "~$it mins" } ?: "ETA --") +
                                " • ${points.size} points",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // Phase 10.11: share the trip destination to the
                        // user's own installed maps / ride apps (dynamic).
                        TextButton(
                            onClick = { showDestinationShare = true },
                        ) {
                            Text("📤 مشاركة الوجهة")
                        }
                    }
                }
            }
        }
    }

    // Phase 10.11: dynamic destination share (trip dest state reused).
    if (showDestinationShare) {
        val dest = trip
        if (dest != null) {
            DestinationShareSheet(
                lat = dest.destLat,
                lng = dest.destLng,
                label = dest.locationName,
                onDismiss = { showDestinationShare = false },
            )
        } else {
            showDestinationShare = false
        }
    }

    // MapView teardown is lifecycle-driven (observer above); drop refs here.
    DisposableEffect(Unit) {
        onDispose {
            mapRef = null
            styleRef = null
        }
    }
}

/** LineString FeatureCollection from breadcrumbs ([lng, lat] order). */
private fun lineStringGeoJson(points: List<RoutePoint>): String {
    val coords = points.joinToString(",") { "[${it.lng},${it.lat}]" }
    return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
        "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coords]}," +
        "\"properties\":{}}]}"
}

/** Gold destination pin (programmatic — no drawable needed). */
private fun destMarkerBitmap(): android.graphics.Bitmap {
    val size = 96
    val bmp = android.graphics.Bitmap.createBitmap(
        size, size, android.graphics.Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bmp)
    val outer = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F2A007")
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
