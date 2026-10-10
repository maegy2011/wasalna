package com.app.wasalna.ui.screens.offline

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.ui.components.ConfirmDialog
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Phase 10.4.3.2: Vela-style offline tiles — list downloads, pick an area
 * on a live mini-map, download with progress, delete old packs.
 * Premium-gated by the caller (Settings row).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit = {},
    onOpenOfflineRouting: () -> Unit = {},
    viewModel: OfflineMapsViewModel = hiltViewModel(),
) {
    val regions by viewModel.regions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    val downloadingName by viewModel.downloadingName.collectAsState()
    val error by viewModel.error.collectAsState()
    // Oct 2026 offline upgrade: retry + on-device tile size.
    val canRetry by viewModel.canRetry.collectAsState()
    val offlineDbBytes by viewModel.offlineDbBytes.collectAsState()
    // Phase 10.4.3.9: offline routing graph state (on-demand download).
    val graphInstalled by viewModel.graphInstalled.collectAsState()
    val graphBusy by viewModel.graphBusy.collectAsState()
    val graphPct by viewModel.graphProgress.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    // رسائل التأكيد: حذف منطقة محمّلة / حذف جراف التوجيه / إلغاء تنزيل
    // جارٍ — كلها فقدان (بيانات أو مجهود) فتُطلب الموافقة أولًا.
    var pendingRegionDelete by remember { mutableStateOf<OfflineRegionInfo?>(null) }
    var showGraphDeleteConfirm by remember { mutableStateOf(false) }
    var showCancelDownloadConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.refreshGraphState()
        onDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.offline_download_new)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live download progress.
            progress?.let { pct ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                R.string.offline_downloading,
                                downloadingName,
                                (pct * 100).toInt(),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { showCancelDownloadConfirm = true }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
            error?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canRetry) {
                                Button(
                                    onClick = { viewModel.retryDownload() },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.offline_retry))
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.common_ok))
                            }
                        }
                    }
                }
            }
            // Phase 10.5.x Step 3: entry to the per-region manager.
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.offline_graph_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.offline_graph_sub),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = onOpenOfflineRouting) {
                        Text(stringResource(R.string.common_manage))
                    }
                }
            }
            // Phase 10.4.3.9: offline routing data card with progress.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.offline_data_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (graphInstalled) {
                            stringResource(R.string.offline_graph_installed)
                        } else {
                            stringResource(R.string.offline_graph_optional)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    graphPct?.let { pct ->
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.offline_graph_downloading, pct),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (graphInstalled) {
                            OutlinedButton(
                                onClick = { showGraphDeleteConfirm = true },
                                enabled = !graphBusy,
                            ) {
                                Text(stringResource(R.string.common_delete))
                            }
                        } else {
                            Button(
                                onClick = { viewModel.downloadGraph() },
                                enabled = !graphBusy,
                            ) {
                                Text(if (graphBusy) stringResource(R.string.common_working) else stringResource(R.string.common_download))
                            }
                        }
                    }
                }
            }
            when {
                isLoading -> Text(stringResource(R.string.offline_loading))
                regions.isEmpty() && progress == null ->
                    Text(
                        stringResource(R.string.offline_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                // Audit fix (9 Oct 2026): unconstrained LazyColumn inside a
                // non-scrollable Column could be pushed off-screen — the
                // downloaded-regions list gets remaining space and scrolls.
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(regions, key = { it.id }) { info ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "🗺️ ${info.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { pendingRegionDelete = info }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.region_delete_desc))
                                }
                            }
                        }
                    }
                }
            }
            // Oct 2026: storage transparency (Vela Storage row) + attribution.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = offlineStorageText(
                            bytes = offlineDbBytes,
                            regionCount = regions.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.offline_credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pendingRegionDelete?.let { info ->
        ConfirmDialog(
            title = stringResource(R.string.offline_delete_region_title),
            message = stringResource(R.string.offline_delete_region_msg, info.name),
            confirmLabel = stringResource(R.string.common_delete),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteRegion(info)
                pendingRegionDelete = null
            },
            onDismiss = { pendingRegionDelete = null },
        )
    }
    if (showGraphDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.offline_delete_graph_title),
            message = stringResource(R.string.offline_delete_graph_msg),
            confirmLabel = stringResource(R.string.common_delete),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteGraph()
                showGraphDeleteConfirm = false
            },
            onDismiss = { showGraphDeleteConfirm = false },
        )
    }
    if (showCancelDownloadConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.offline_cancel_title),
            message = stringResource(R.string.offline_cancel_msg),
            confirmLabel = stringResource(R.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.cancelDownload()
                showCancelDownloadConfirm = false
            },
            onDismiss = { showCancelDownloadConfirm = false },
        )
    }
    if (showPicker) {
        RegionPickerDialog(
            onDismiss = { showPicker = false },
            onDownload = { name, bounds ->
                showPicker = false
                viewModel.startDownload(name, bounds)
            },
        )
    }
}

/** Storage row text: on-device tile size (or unknown) + region count. */
@Composable
private fun offlineStorageText(bytes: Long?, regionCount: Int): String {
    val size = if (bytes != null && bytes > 0L) {
        if (bytes >= 1024L * 1024L) {
            stringResource(R.string.offline_size_mb, bytes / (1024L * 1024L))
        } else {
            stringResource(R.string.offline_size_kb, bytes / 1024L)
        }
    } else {
        stringResource(R.string.offline_storage_unknown)
    }
    return stringResource(R.string.offline_storage_note, size, regionCount)
}

/** Vela-style pre-download estimate label for a preset bounds. */
@Composable
private fun presetSizeLabel(bounds: LatLngBounds): String {
    val tiles = OfflineTileEstimate.countTiles(
        south = bounds.latitudeSouth,
        west = bounds.longitudeWest,
        north = bounds.latitudeNorth,
        east = bounds.longitudeEast,
        minZoom = OfflineMapsViewModel.MIN_ZOOM_INT,
        maxZoom = OfflineMapsViewModel.MAX_ZOOM_INT,
    )
    val bytes = OfflineTileEstimate.bytesFor(tiles)
    return if (bytes >= 1024L * 1024L) {
        stringResource(
            R.string.offline_estimate_mb,
            bytes / (1024L * 1024L),
            tiles,
        )
    } else {
        stringResource(
            R.string.offline_estimate_kb,
            (bytes / 1024L).coerceAtLeast(1L),
            tiles,
        )
    }
}

/**
 * Full-screen picker: pan/zoom the mini-map to the wanted area, name it,
 * then download exactly the visible bounds. Cairo/Giza presets first
 * (Vela catalog idea: one tap downloads a known metro area).
 */
@Composable
private fun RegionPickerDialog(
    onDismiss: () -> Unit,
    onDownload: (String, LatLngBounds) -> Unit,
) {
    val context = LocalContext.current
    var regionName by remember { mutableStateOf("") }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.offline_pick_area),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = regionName,
                    onValueChange = { regionName = it },
                    label = { Text(stringResource(R.string.offline_region_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Quick-area presets with pre-download size (Vela catalog).
                Text(
                    text = stringResource(R.string.offline_presets_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val presets = listOf(
                    Triple(
                        R.string.offline_preset_cairo,
                        OfflineMapsViewModel.PRESET_CAIRO,
                        11.0,
                    ),
                    Triple(
                        R.string.offline_preset_giza,
                        OfflineMapsViewModel.PRESET_GIZA,
                        11.0,
                    ),
                    Triple(
                        R.string.offline_preset_cairo_giza,
                        OfflineMapsViewModel.PRESET_CAIRO_GIZA,
                        10.0,
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (nameRes, bounds, zoom) ->
                        OutlinedButton(
                            onClick = {
                                regionName = context.getString(nameRes)
                                runCatching {
                                    mapRef?.moveCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            OfflineMapsViewModel.centerOf(bounds),
                                            zoom,
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = "${context.getString(nameRes)} · ${presetSizeLabel(bounds)}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            mapViewRef = this
                            onCreate(null)
                            onStart()
                            getMapAsync { map ->
                                map.setStyle(Style.Builder().fromUri(OPENFREEMAP_STYLE_URL)) {
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(30.0444, 31.2357))
                                        .zoom(11.0)
                                        .build()
                                    mapRef = map
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.offline_pan_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = {
                            val bounds = runCatching {
                                mapRef?.projection?.visibleRegion?.latLngBounds
                            }.getOrNull()
                            if (bounds == null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.offline_not_ready),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                onDownload(regionName.ifBlank { context.getString(R.string.offline_default_city) }, bounds)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.offline_download_here))
                    }
                }
            }
        }
    }

    // Phase 10.4.3.2: full MapView teardown on dismiss — no leaked GL context.
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mapViewRef?.onPause()
                mapViewRef?.onStop()
                mapViewRef?.onDestroy()
            }
            mapViewRef = null
            mapRef = null
        }
    }
}
