package com.app.wasalna.ui.screens.offline

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.ui.components.ConfirmDialog
import org.maplibre.android.camera.CameraPosition
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
                title = { Text("Offline Maps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Download New Region") },
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
                            text = "Downloading $downloadingName… ${(pct * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { showCancelDownloadConfirm = true }) {
                            Text("Cancel")
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
                        OutlinedButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
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
                            text = "Offline Routing (GraphHopper)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Per-region graphs with live status",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = onOpenOfflineRouting) {
                        Text("Manage")
                    }
                }
            }
            // Phase 10.4.3.9: offline routing data card with progress.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧭 Offline Routing Data",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (graphInstalled)
                            "Cairo + Giza graph installed — on-device routing ready."
                        else
                            "Optional routing graph (Cairo + Giza, ~42 MB). Downloaded only if you tap.",
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
                            text = "Downloading routing graph… $pct%",
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
                                Text("Delete")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.downloadGraph() },
                                enabled = !graphBusy,
                            ) {
                                Text(if (graphBusy) "Working…" else "Download")
                            }
                        }
                    }
                }
            }
            when {
                isLoading -> Text("Loading downloads…")
                regions.isEmpty() && progress == null ->
                    Text(
                        "No offline regions yet — tap below to download your city. " +
                            "The map then renders without internet.",
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
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف المنطقة")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRegionDelete?.let { info ->
        ConfirmDialog(
            title = "حذف المنطقة؟",
            message = "سيتم حذف خريطة \"${info.name}\" المحمّلة نهائيًا وستحتاج لتنزيلها من جديد.",
            confirmLabel = "احذف",
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
            title = "حذف بيانات التوجيه؟",
            message = "سيتم حذف جراف التوجيه (القاهرة + الجيزة، ~42 م.ب) وستحتاج لتنزيله من جديد للملاحة دون إنترنت.",
            confirmLabel = "احذف",
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
            title = "إلغاء التنزيل؟",
            message = "التنزيل جارٍ الآن — إلغاؤه يعني فقدان ما تم تحميله وستبدأ من جديد.",
            confirmLabel = "ألغِ",
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

/**
 * Full-screen picker: pan/zoom the mini-map to the wanted area, name it,
 * then download exactly the visible bounds.
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
                    text = "Pick an area",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = regionName,
                    onValueChange = { regionName = it },
                    label = { Text("Region name (e.g. My City)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    text = "Pan & zoom to the area, then tap Download Here.",
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
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val bounds = runCatching {
                                mapRef?.projection?.visibleRegion?.latLngBounds
                            }.getOrNull()
                            if (bounds == null) {
                                Toast.makeText(
                                    context,
                                    "Map isn't ready yet — wait a second",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                onDownload(regionName.ifBlank { "My City" }, bounds)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Download Here")
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
