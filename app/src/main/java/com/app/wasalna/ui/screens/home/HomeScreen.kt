package com.app.wasalna.ui.screens.home

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch

/**
 * Phase 5: Home — list of saved destinations, each with an Activate Alarm button.
 *
 * On Activate: checks foreground location permission, then starts
 * [TrackingService] via [ContextCompat.startForegroundService] with the
 * target extras; shows "Tracking started" feedback. Listens for the
 * package-scoped [TrackingService.ACTION_DESTINATION_REACHED] broadcast
 * to surface a "Destination reached!" snackbar.
 */
@Composable
fun HomeScreen(
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var trackingName by remember { mutableStateOf<String?>(null) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenMap) {
                Icon(Icons.Filled.Add, contentDescription = "Add destination")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = "My alarms",
                style = MaterialTheme.typography.headlineSmall,
            )
            trackingName?.let { name ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tracking started: $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (locations.isEmpty()) {
                Text(
                    text = "No saved destinations yet — tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(locations, key = { it.id }) { location ->
                        LocationCard(
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
                            onDelete = { viewModel.deleteLocation(location) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
                OutlinedButton(onClick = onOpenPaywall) { Text("Pro") }
            }
        }
    }
}

@Composable
private fun LocationCard(
    location: Location,
    isTracking: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${location.latitude}, ${location.longitude}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onActivate, enabled = !isTracking) {
                Icon(
                    Icons.Filled.AlarmOn,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text("Alarm")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete destination")
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
