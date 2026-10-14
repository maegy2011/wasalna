package com.app.wasalna.ui.screens.trips

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R
import com.app.wasalna.domain.model.displayName

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.app.wasalna.ui.components.WasalnaTopBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import com.app.wasalna.domain.model.CairoTransitStations
import com.app.wasalna.domain.model.TransitPlanner
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.ui.components.DestinationShareSheet
import com.app.wasalna.ui.components.TransitApproxNote
import com.app.wasalna.ui.components.TransitGuideCard
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
private const val PLAN_SRC = "trip-plan-src"
private const val PLAN_LAYER = "trip-plan-layer"
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
    // Planned origin → destination corridor (drawn under the trail).
    val plannedRoute by viewModel.plannedRouteGeoJson.collectAsState()
    // Past-trip recorded summary (null while the trip is still active).
    val pastSummary by viewModel.pastSummary.collectAsState()
    // User order Oct 2026: trip sharing is First Class.
    val isPremium by viewModel.isPremium.collectAsState()
    // VEHICLE (user order Oct 2026): mid-trip switch — persists live;
    // the running service recomputes the route with the new profile.
    val vehicle by viewModel.transportMode.collectAsState()
    // TRIP SIMULATION: test-mode strip while virtual GPS drives the trip.

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var cameraFitted by remember { mutableStateOf(false) }
    // Reduced motion (Oct 2026, elderly): read in composition, used below.
    val reducedMotion = com.app.wasalna.ui.theme.LocalElderlyMode.current
    // Phase 10.11: dynamic destination share sheet (re-queries on open).
    var showDestinationShare by remember { mutableStateOf(false) }
    // REDESIGN 10 Oct 2026: snackbar for share-as-image failures.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shareImageFailed = stringResource(R.string.trip_share_image_failed)

    // Reactive redraw: every breadcrumb emission rebuilds the LineString.
    LaunchedEffect(points, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        if (points.size < 2) return@LaunchedEffect
        runCatching {
            val src = style.getSource(ROUTE_SRC) as? GeoJsonSource ?: return@LaunchedEffect
            src.setGeoJson(lineStringGeoJson(points))
        }
    }

    // Planned corridor redraw (one payload per fetch — mode/origin/dest).
    LaunchedEffect(plannedRoute, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        val geoJson = plannedRoute ?: return@LaunchedEffect
        runCatching {
            val src = style.getSource(PLAN_SRC) as? GeoJsonSource ?: return@LaunchedEffect
            src.setGeoJson(geoJson)
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
            val update = CameraUpdateFactory.newLatLngBounds(bounds.build(), 120)
            // Reduced motion (Oct 2026, elderly): jump instead of easing.
            if (reducedMotion) map.moveCamera(update)
            else map.easeCamera(update)
            cameraFitted = true
        }
    }

    Scaffold(
        topBar = {
            WasalnaTopBar(
                title = trip?.locationName ?: stringResource(R.string.trip_share_title),
                showBack = true,
                onBackClick = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                    // Planned corridor FIRST (under) — travelled trail on top.
                                    if (style.getSource(PLAN_SRC) == null) {
                                        style.addSource(GeoJsonSource(PLAN_SRC))
                                    }
                                    if (style.getLayer(PLAN_LAYER) == null) {
                                        style.addLayer(
                                            LineLayer(PLAN_LAYER, PLAN_SRC).withProperties(
                                                PropertyFactory.lineColor("#0D7C66"),
                                                PropertyFactory.lineOpacity(0.45f),
                                                PropertyFactory.lineWidth(4f),
                                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                                            ),
                                        )
                                    }
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

            // REDESIGN 10 Oct 2026: live progress hero (top) — big
            // remaining time/distance + progress bar + mode/failsafe line.
            // Map keeps the full stage; hero + actions are overlays.
            // Kept: destination name, status, ETA, breadcrumb count,
            // dynamic destination share sheet entry point.
            trip?.let { t ->
                val isPast = t.status == com.app.wasalna.data.local.entity.TripStatus.PAST
                val lastPoint = points.lastOrNull()
                val travelledKm = points.zipWithNext { a, b ->
                    haversineKm(a.lat, a.lng, b.lat, b.lng)
                }.sum()
                val remainingKm = if (lastPoint != null) {
                    haversineKm(lastPoint.lat, lastPoint.lng, t.destLat, t.destLng)
                } else {
                    null
                }
                val totalKm = travelledKm + (remainingKm ?: 0.0)
                val progress = if (totalKm > 0.01) {
                    (travelledKm / totalKm).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
            // TRIP SIMULATION: clearly-labeled test-mode strip +
            // prominent pause/resume/stop bar on top (same state-driven
            // bar as MapScreen; stop also ends the service → real GPS).
                // PRO HERO (Oct 2026): floating journey sheet — grab handle +
                // badge/title header + dual stat tiles + rounded progress.
                // Signature: oversized tabular numerals on tinted tiles.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .align(Alignment.TopCenter),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(0.12f)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                                ),
                        )
                        // Transit design system (Oct 2026): live status badge.
                        val badgeKind = when {
                            points.size < 2 -> com.app.wasalna.ui.components.BadgeKind.OFFLINE
                            progress >= 0.8f -> com.app.wasalna.ui.components.BadgeKind.APPROACHING
                            else -> com.app.wasalna.ui.components.BadgeKind.ACTIVE
                        }
                        val badgeText = when {
                            points.size < 2 -> stringResource(R.string.badge_offline)
                            progress >= 0.8f -> stringResource(R.string.badge_approaching)
                            else -> stringResource(R.string.badge_active)
                        }
                        com.app.wasalna.ui.components.StatusBadge(
                            kind = badgeKind,
                            text = badgeText,
                        )
                        Text(
                            text = "${t.transportMode.icon} ${t.locationName}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        // Past browsing (user order Oct 2026): recorded summary
                        // replaces the live tiles + progress + mode line.
                        val past = pastSummary
                        if (isPast && past != null) {
                            PastSummaryBlock(
                                summary = past,
                                originFallback = points.firstOrNull()?.let {
                                    String.format(
                                        java.util.Locale.US,
                                        "%.4f, %.4f",
                                        it.lat,
                                        it.lng,
                                    )
                                } ?: "—",
                            )
                        } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TripStatTile(
                                modifier = Modifier.weight(1f),
                                emoji = "⏱️",
                                value = t.etaMinutes?.let {
                                    stringResource(R.string.trip_hero_remaining_time, it)
                                } ?: stringResource(R.string.trip_hero_arrived),
                                label = stringResource(R.string.trip_hero_remaining_time_label),
                            )
                            TripStatTile(
                                modifier = Modifier.weight(1f),
                                emoji = "📍",
                                value = remainingKm?.let {
                                    stringResource(
                                        R.string.trip_hero_remaining_dist,
                                        String.format(java.util.Locale.US, "%.1f", it),
                                    )
                                } ?: "—",
                                label = stringResource(R.string.trip_hero_remaining_dist_label),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // Mode + failsafe status line (read-only).
                        Text(
                            text = if (points.size < 2) {
                                "${t.status} • ${stringResource(R.string.trip_failsafe_offline)}"
                            } else {
                                "${t.status} • ${t.transportMode.name.lowercase().replaceFirstChar { it.uppercase() }} • ${points.size} pts"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                        // Transit design system (Oct 2026): vertical route
                        // timeline with green progress + vehicle icon.
                        if (points.size >= 2) {
                            val origin = points.firstOrNull()
                            com.app.wasalna.ui.components.RouteTimeline(
                                boardingName = origin?.let {
                                    String.format(
                                        java.util.Locale.US,
                                        "%.4f, %.4f",
                                        it.lat,
                                        it.lng,
                                    )
                                } ?: "—",
                                alightingName = t.locationName,
                                progress = progress,
                                mode = vehicle,
                                remainingText = remainingKm?.let {
                                    stringResource(
                                        R.string.timeline_km_left,
                                        String.format(java.util.Locale.US, "%.1f", it),
                                    )
                                },
                                etaText = t.etaMinutes?.let {
                                    stringResource(R.string.trip_hero_remaining_time, it)
                                },
                            )
                        }
                        // VEHICLE mid-trip switch (user order Oct 2026):
                        // 48dp+ chips, 8dp spacing, RTL-safe. Persists to
                        // DataStore; TrackingService picks it up live and
                        // recomputes the route (next OSRM fetch, new profile).
                        if (!isPast) {
                        Text(
                            text = stringResource(R.string.vehicle_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 5 vehicles now: horizontal scroll keeps 48dp+
                        // chips reachable on narrow screens (RTL-safe).
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TransportMode.pickerEntries.forEach { option ->
                                FilterChip(
                                    selected = vehicle == option,
                                    onClick = { viewModel.setTransportMode(option) },
                                    label = {
                                        Text(
                                            when (option) {
                                                TransportMode.CAR -> stringResource(R.string.transport_car)
                                                TransportMode.MOTORCYCLE -> stringResource(R.string.vehicle_motorcycle)
                                                TransportMode.BICYCLE -> stringResource(R.string.vehicle_bicycle)
                                                TransportMode.WALK -> stringResource(R.string.transport_walk)
                                                TransportMode.TRANSIT -> stringResource(R.string.vehicle_transit)
                                                TransportMode.METRO -> stringResource(R.string.vehicle_metro)
                                                TransportMode.MONORAIL -> stringResource(R.string.vehicle_monorail)
                                                TransportMode.TRAIN -> stringResource(R.string.vehicle_train_lrt)
                                            },
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.vehicle_midtrip_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                        // TRANSIT mid-trip guide (Oct 2026, approximate):
                        // boarding = nearest station to the trip origin
                        // (first breadcrumb), alighting = nearest to the
                        // destination. Honesty label always attached.
                        if (!isPast && vehicle == TransportMode.TRANSIT) {
                            val dest = trip
                            if (dest != null) {
                                val origin = points.firstOrNull()
                                val guide = remember(origin?.lat, origin?.lng, dest) {
                                    CairoTransitStations.buildGuide(
                                        originLat = origin?.lat ?: dest.destLat,
                                        originLng = origin?.lng ?: dest.destLng,
                                        destLat = dest.destLat,
                                        destLng = dest.destLng,
                                    )
                                }
                                TransitGuideCard(
                                    boardingText = stringResource(
                                        R.string.transit_board_at,
                                        guide.boarding.station.displayName(),
                                    ) + " " + stringResource(
                                        R.string.transit_walk_leg,
                                        walkKm(guide.boarding.distanceM),
                                    ),
                                    alightingText = stringResource(
                                        R.string.transit_alight_at,
                                        guide.alighting.station.displayName(),
                                    ) + " " + stringResource(
                                        R.string.transit_walk_leg,
                                        walkKm(guide.alighting.distanceM),
                                    ),
                                    // OSM outcome (c): drop the approximate
                                    // label only when both ends are OSM-covered.
                                    showApproxNote = guide.approximate,
                                )
                            } else {
                                TransitApproxNote()
                            }
                        }
                        // Static transit mid-trip guide (METRO/MONORAIL/
                        // TRAIN, Oct 2026): TransitPlanner on embedded data
                        // (origin = first breadcrumb, else the destination).
                        // Walk legs in km; honesty label always attached.
                        if (!isPast && vehicle in TransportMode.staticTransitEntries) {
                            val dest = trip
                            if (dest != null) {
                                val plan = remember(points, dest, vehicle) {
                                    val origin = points.firstOrNull()
                                    com.app.wasalna.domain.model.TransitPlanner.plan(
                                        origin?.lat ?: dest.destLat,
                                        origin?.lng ?: dest.destLng,
                                        dest.destLat, dest.destLng, vehicle,
                                    )
                                }
                                if (plan.kind == "NONE") {
                                    Text(
                                        text = stringResource(
                                            R.string.transit_static_none,
                                            vehicleStaticLabel(vehicle),
                                            stringResource(R.string.transit_off_corridor),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TransitApproxNote()
                                } else {
                                    TransitGuideCard(
                                        boardingText = stringResource(
                                            R.string.transit_board_at,
                                            plan.board?.displayName() ?: "—",
                                        ) + " " + stringResource(
                                            R.string.transit_walk_leg,
                                            walkKm(plan.walkOriginM.toFloat()),
                                        ),
                                        alightingText = staticRideSummary(plan) + " " + stringResource(
                                            R.string.transit_alight_at,
                                            plan.alight?.displayName() ?: "—",
                                        ) + " " + stringResource(
                                            R.string.transit_walk_leg,
                                            walkKm(plan.walkDestM.toFloat()),
                                        ),
                                        // OSM outcome (c): planner flags any
                                        // estimated leg — label stays unless fully
                                        // scheduled + OSM-covered.
                                        showApproxNote = plan.approximate,
                                    )
                                }
                            }
                        }
                        // Phase 10.11: share the trip destination to the
                        // user's own installed maps / ride apps (dynamic).
                        TextButton(
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
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.share_destination))
                        }
                    }
                }
                // Bottom action bar PRO (Oct 2026): floating pill sheet —
                // ONE destructive-primary (end trip) + share image.
                // 56dp targets, 8dp spacing, elderly-friendly.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .align(Alignment.BottomCenter),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Past browsing: no end-trip action (already finished).
                    if (!isPast) {
                    Button(
                        onClick = { viewModel.finishTrip(context, onBack) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.trip_end),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    }
                    OutlinedButton(
                        onClick = {
                            // User order Oct 2026: sharing = First Class.
                            if (!isPremium) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.share_premium_only),
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                                return@OutlinedButton
                            }
                            scope.launch {
                                val distText = context.getString(
                                    R.string.trip_card_distance_km,
                                    String.format(java.util.Locale.US, "%.1f", totalKm),
                                )
                                val durText = t.etaMinutes?.let {
                                    context.getString(R.string.trip_card_duration_min, "$it")
                                } ?: tripCardDate(t.startTime)
                                val past = pastSummary
                                val ok = captureAndShareTripImage(
                                    context = context,
                                    destination = t.locationName,
                                    distanceKm = distText,
                                    durationMin = durText,
                                    dateText = tripCardDate(
                                        t.endTime ?: System.currentTimeMillis(),
                                    ),
                                    distanceLabel = context.getString(R.string.past_distance),
                                    durationLabel = context.getString(R.string.past_duration),
                                    originName = past?.originName,
                                    avgSpeed = past?.avgKmh?.let {
                                        context.getString(
                                            R.string.past_avg_speed_value,
                                            String.format(java.util.Locale.US, "%.1f", it),
                                        )
                                    },
                                    avgSpeedLabel = context.getString(R.string.past_avg_speed),
                                )
                                if (!ok) snackbarHostState.showSnackbar(shareImageFailed)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.trip_share_image),
                            style = MaterialTheme.typography.titleSmall,
                        )
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

/**
 * PRO HERO stat tile (Oct 2026): tinted rounded tile with emoji lead,
 * oversized tabular value + small caption. Elderly legibility first:
 * 20sp bold value, 48dp+ touch-free info tile, RTL-safe Column.
 */
@Composable
private fun TripStatTile(
    modifier: Modifier = Modifier,
    emoji: String,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Past-trip recorded summary (user order Oct 2026): origin →
 * destination names, start/arrival times, and the recorded stats
 * (duration · distance · average speed) from the saved breadcrumbs.
 */
@Composable
private fun PastSummaryBlock(
    summary: TripRouteViewModel.PastTripSummary,
    originFallback: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PastPlaceRow(
            emoji = "🟢",
            label = stringResource(R.string.past_origin),
            value = summary.originName ?: originFallback,
        )
        PastPlaceRow(
            emoji = "🔴",
            label = stringResource(R.string.past_destination),
            value = summary.destName,
        )
        PastPlaceRow(
            emoji = "🕐",
            label = stringResource(R.string.past_start_time),
            value = tripCardTime(summary.startTime),
        )
        summary.endTime?.let { end ->
            PastPlaceRow(
                emoji = "🏁",
                label = stringResource(R.string.past_end_time),
                value = tripCardTime(end),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TripStatTile(
                modifier = Modifier.weight(1f),
                emoji = "⏱️",
                value = summary.durationMin?.let {
                    stringResource(R.string.trip_card_duration_min, "$it")
                } ?: "—",
                label = stringResource(R.string.past_duration),
            )
            TripStatTile(
                modifier = Modifier.weight(1f),
                emoji = "📏",
                value = stringResource(
                    R.string.trip_card_distance_km,
                    String.format(java.util.Locale.US, "%.1f", summary.distanceKm),
                ),
                label = stringResource(R.string.past_distance),
            )
            TripStatTile(
                modifier = Modifier.weight(1f),
                emoji = "🚀",
                value = summary.avgKmh?.let {
                    stringResource(
                        R.string.past_avg_speed_value,
                        String.format(java.util.Locale.US, "%.1f", it),
                    )
                } ?: "—",
                label = stringResource(R.string.past_avg_speed),
            )
        }
    }
}

@Composable
private fun PastPlaceRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** LineString FeatureCollection from breadcrumbs ([lng, lat] order). */
/** Straight-line meters → "1.2" km string (US locale, caller adds the unit resource). */
private fun walkKm(distanceM: Float): String =
    String.format(java.util.Locale.US, "%.1f", distanceM / 1000f)

/** Static-transit mode label (METRO/MONORAIL/LRT-train). */
@Composable
private fun vehicleStaticLabel(mode: TransportMode): String = when (mode) {
    TransportMode.METRO -> stringResource(R.string.vehicle_metro)
    TransportMode.MONORAIL -> stringResource(R.string.vehicle_monorail)
    else -> stringResource(R.string.vehicle_train_lrt)
}

/** One-line ride summary for a static itinerary (mid-trip card). */
@Composable
private fun staticRideSummary(plan: TransitPlanner.TransitItinerary): String {
    return if (plan.segments.size > 1) {
        val legs = plan.segments.joinToString(" + ") { seg ->
            "${seg.line.nameAr} (${seg.stops.size})"
        }
        stringResource(R.string.transit_static_transfer, legs, plan.totalMin)
    } else {
        val seg = plan.segments.firstOrNull()
        val line = seg?.line?.nameAr ?: stringResource(R.string.vehicle_title)
        val from = plan.board?.displayName() ?: "—"
        val to = plan.alight?.displayName() ?: "—"
        stringResource(
            R.string.transit_static_direct,
            line, from, to, seg?.stops?.size ?: 0, plan.totalMin,
        )
    }
}

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
