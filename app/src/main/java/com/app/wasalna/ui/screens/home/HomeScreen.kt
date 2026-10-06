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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.app.wasalna.ui.ads.InterstitialHelper
import kotlinx.coroutines.launch

/**
 * Phase 8: Home + Monetization.
 * - Banner ad above bottom (only when !premium).
 * - FAB: freemium gate (2 places max) + interstitial for free users.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit = {},
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
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

    fun handleAddDestination() {
        // Phase 8 freemium: free plan allows 2 places max.
        if (!isPremium && locations.size >= 2) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Free plan allows 2 places. Upgrade to First Class!",
                )
            }
            Toast.makeText(
                context,
                "Free plan allows 2 places. Upgrade to First Class!",
                Toast.LENGTH_LONG,
            ).show()
            onOpenPaywall()
            return
        }
        if (!isPremium) {
            InterstitialHelper.showInterstitial(context) {
                onOpenMap()
            }
        } else {
            onOpenMap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wasalna") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { handleAddDestination() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add destination")
            }
        },
        bottomBar = {
            // Phase 8: banner above BottomAppBar (BottomAppBar lives in MainContainer).
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
            Text(
                text = "Where are we going today?",
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
                    text = "No places saved yet. Let's add one!",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
