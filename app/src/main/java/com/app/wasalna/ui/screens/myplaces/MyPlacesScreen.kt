package com.app.wasalna.ui.screens.myplaces

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.data.service.TrackingService
import com.app.wasalna.domain.model.Location
import com.app.wasalna.ui.ads.AdBanner
import com.app.wasalna.ui.screens.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Phase 9-2-fix: dedicated "My Places" screen.
 * Owns its TopAppBar (no shared header) + the saved-locations list
 * with Activate Alarm + Delete per item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPlacesScreen(
    onBack: () -> Unit = {},
    onAddDestination: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var trackingName by remember { mutableStateOf<String?>(null) }
    // Phase 10-Fix: local filter + rename dialog state (CRUD).
    var filter by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Location?>(null) }
    val visiblePlaces = remember(locations, filter) {
        if (filter.isBlank()) locations
        else locations.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == TrackingService.ACTION_DESTINATION_REACHED) {
                    trackingName = null
                    scope.launch {
                        snackbarHostState.showSnackbar("Destination reached! ⏰")
                    }
                }
            }
        }
        val filter = IntentFilter(TrackingService.ACTION_DESTINATION_REACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Places") },
                navigationIcon = {
                    // Phase 10.4.1: back reliably returns to the Home tab.
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDestination) {
                Icon(Icons.Filled.Add, contentDescription = "Add destination")
            }
        },
        bottomBar = {
            if (!isPremium) {
                AdBanner()
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            trackingName?.let { name ->
                Text(
                    text = "Tracking started: $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            // Phase 10-Fix: local search over saved places.
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search saved places") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (locations.isEmpty()) {
                Text(
                    text = "No places saved yet. Tap + to add one!",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else if (visiblePlaces.isEmpty()) {
                Text(
                    text = "No saved places match \"$filter\".",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visiblePlaces, key = { it.id }) { location ->
                        SavedPlaceCard(
                            location = location,
                            isTracking = trackingName != null,
                            onActivate = {
                                if (hasLocationPermission(context)) {
                                    startTracking(context, location)
                                    trackingName = location.name
                                    Toast.makeText(
                                        context,
                                        "Tracking started",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Location permission required — grant it in Permissions",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                            onEdit = { renameTarget = location },
                            onDelete = { viewModel.deleteLocation(location) },
                        )
                    }
                }
            }
        }
    }
    // Phase 10-Fix: rename dialog (Update CRUD).
    renameTarget?.let { target ->
        var draft by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename place") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Place name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameLocation(target, draft)
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SavedPlaceCard(
    location: Location,
    isTracking: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Rename destination")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete destination")
                }
            }
            Text(
                text = "${location.latitude}, ${location.longitude}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onActivate,
                enabled = !isTracking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.AlarmOn,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("Activate Alarm")
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun startTracking(context: Context, location: Location) {
    val intent = Intent(context, TrackingService::class.java).apply {
        action = TrackingService.ACTION_START
        putExtra(TrackingService.EXTRA_LAT, location.latitude)
        putExtra(TrackingService.EXTRA_LNG, location.longitude)
    }
    ContextCompat.startForegroundService(context, intent)
}
