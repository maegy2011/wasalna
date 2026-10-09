package com.app.wasalna.ui.screens.offline

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.data.routing.OfflineGraphState
import com.app.wasalna.data.routing.RoutingRegion

/**
 * Phase 10.5.x Step 3: Offline Routing screen — per-region graph manager.
 * Region list (hardcoded "Monaco Test" + Cairo+Giza, list-designed).
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Routing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                        text = "Engine error: $msg",
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
                    OfflineRegionCard(
                        region = region,
                        state = state,
                        onDownload = { viewModel.download(region) },
                        onDelete = { viewModel.delete(region.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineRegionCard(
    region: RoutingRegion,
    state: OfflineGraphState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🧭 ${region.name}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Size ~${region.sizeMb} MB",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is OfflineGraphState.NotDownloaded -> {
                    Text(
                        text = "Not downloaded",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDownload) {
                        Text("Download")
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
                        text = "Downloading… $pct%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is OfflineGraphState.Extracting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Extracting…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is OfflineGraphState.Ready -> {
                    Text(
                        text = "Ready — on-device routing available ✓",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDelete) {
                            Text("Delete")
                        }
                    }
                }
                is OfflineGraphState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onDownload) {
                            Text("Retry")
                        }
                        OutlinedButton(onClick = onDelete) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
