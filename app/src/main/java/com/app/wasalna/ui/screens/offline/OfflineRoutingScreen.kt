package com.app.wasalna.ui.screens.offline

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.app.wasalna.ui.components.WasalnaTopBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.data.routing.OfflineGraphState
import com.app.wasalna.ui.components.ConfirmDialog
import com.app.wasalna.data.routing.RoutingRegion

/**
 * Phase 10.5.x Step 3: Offline Routing screen — per-region graph manager.
 * Region list (Cairo+Giza, list-designed for future regions).
 * Per item: Download (if not downloaded), Delete (if READY/ERROR),
 * clear status (progress % or exact error). All work on Dispatchers.IO
 * inside RoutingGraphManager/GraphHopperRouteEngine — never here.
 * Touch targets ≥ 48dp (Button/OutlinedButton defaults + 16dp padding).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineRoutingScreen(
    onBack: () -> Unit = {},
    viewModel: OfflineRoutingViewModel = hiltViewModel(),
) {
    val engineError by viewModel.engineError.collectAsState()
    // رسالة تأكيد قبل حذف جراف التوجيه (إعادة التنزيل مجهود كبير).
    var pendingGraphDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            WasalnaTopBar(
                title = stringResource(R.string.routing_title),
                showBack = true,
                onBackClick = onBack,
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
            engineError?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.routing_engine_error, msg),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(viewModel.regions, key = { it.id }) { region ->
                    val state by viewModel.stateFor(region.id).collectAsState()
                    // Gazetteer row state (Oct 2026): file-based, re-read
                    // on every gazVersion bump (download/delete/fail).
                    val gazVersion by viewModel.gazVersion.collectAsState()
                    val gazBusyIds by viewModel.gazBusy.collectAsState()
                    val gazFailedIds by viewModel.gazFailed.collectAsState()
                    @Suppress("UNUSED_EXPRESSION") gazVersion
                    OfflineRegionCard(
                        region = region,
                        state = state,
                        onDownload = { viewModel.download(region) },
                        onDelete = { pendingGraphDelete = region.id },
                        gazStored = viewModel.isGazStored(region.id),
                        gazBusy = gazBusyIds.contains(region.id),
                        gazFailed = gazFailedIds.contains(region.id),
                        gazKb = viewModel.gazSizeKb(region.id),
                        onGazDownload = { viewModel.downloadGazetteer(region) },
                        onGazDelete = { viewModel.deleteGazetteer(region.id) },
                    )
                }
            }
        }
    }
    pendingGraphDelete?.let { regionId ->
        val regionName = viewModel.regions.firstOrNull { it.id == regionId }?.name
            ?: stringResource(R.string.routing_region_fallback)
        ConfirmDialog(
            title = stringResource(R.string.routing_delete_title),
            message = stringResource(R.string.routing_delete_msg, regionName),
            confirmLabel = stringResource(R.string.myplaces_delete_confirm),
            isDestructive = true,
            onConfirm = {
                viewModel.delete(regionId)
                pendingGraphDelete = null
            },
            onDismiss = { pendingGraphDelete = null },
        )
    }
}

@Composable
private fun OfflineRegionCard(
    region: RoutingRegion,
    state: OfflineGraphState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    gazStored: Boolean,
    gazBusy: Boolean,
    gazFailed: Boolean,
    gazKb: Long,
    onGazDownload: () -> Unit,
    onGazDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🧭 ${region.name}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.routing_size, region.sizeMb),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Offline search index (Oct 2026): same download/delete UX
            // as the graph above — streets, districts, stations, POIs.
            Text(
                text = "🔍 ${stringResource(R.string.routing_gaz_title)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.routing_gaz_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            when {
                gazBusy -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.routing_gaz_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                gazStored -> {
                    Text(
                        text = stringResource(R.string.routing_gaz_ready, gazKb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onGazDelete) {
                            Text(stringResource(R.string.common_delete))
                        }
                    }
                }
                else -> {
                    Text(
                        text = if (gazFailed) {
                            stringResource(R.string.routing_gaz_failed)
                        } else {
                            stringResource(R.string.routing_gaz_missing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (gazFailed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = onGazDownload) {
                        Text(
                            if (gazFailed) stringResource(R.string.common_retry)
                            else stringResource(R.string.common_download),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is OfflineGraphState.NotDownloaded -> {
                    Text(
                        text = stringResource(R.string.routing_not_downloaded),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.common_download))
                    }
                }
                is OfflineGraphState.Downloading -> {
                    val pct = state.progressPct
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.routing_downloading, pct),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is OfflineGraphState.Extracting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.routing_extracting),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is OfflineGraphState.Ready -> {
                    Text(
                        text = stringResource(R.string.routing_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDelete) {
                            Text(stringResource(R.string.common_delete))
                        }
                    }
                }
                is OfflineGraphState.Error -> {
                    Text(
                        text = stringResource(R.string.routing_error, state.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onDownload) {
                            Text(stringResource(R.string.common_retry))
                        }
                        OutlinedButton(onClick = onDelete) {
                            Text(stringResource(R.string.common_delete))
                        }
                    }
                }
            }
        }
    }
}
