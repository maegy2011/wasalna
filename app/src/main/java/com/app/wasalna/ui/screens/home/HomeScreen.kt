package com.app.wasalna.ui.screens.home

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.ui.ads.AdBanner
import com.app.wasalna.ui.ads.InterstitialHelper
import com.app.wasalna.ui.components.OfflineModeButton
import kotlinx.coroutines.launch

/**
 * Phase 9-2-fix: Home is now a welcoming landing screen.
 * Owns its TopAppBar (no shared header) — greeting + empty-state
 * illustration + massive "Add New Destination" button (→ MapScreen).
 * The saved-places list moved to MyPlacesScreen.
 * Phase 8 freemium gate (2 places max) still applies on add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit = {},
    // Phase 10.4.3.1: null = blank picker; a trip opens its live view.
    onOpenMap: (TripHistory?) -> Unit,
    // Phase 10.4.3.8: null = keep previous map behavior.
    onOpenTripRoute: ((Long) -> Unit)? = null,
    onOpenPaywall: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    // Phase 10.4.2.1: live active trip for the top widget.
    val activeTrip by viewModel.activeTrip.collectAsState()
    // True offline mode: same StateFlow the Map header writes (two-way synced).
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    // First-run wizard: only while unseen AND no saved places yet AND no
    // live trip — everyone else keeps the classic welcome screen.
    val isWizardSeen by viewModel.isWizardSeen.collectAsState()
    val showWizard = !isWizardSeen && locations.isEmpty() && activeTrip == null
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun handleAddDestination() {
        // Phase 8 freemium: free plan allows 2 places max.
        if (!isPremium && locations.size >= 2) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.home_free_limit),
                )
            }
            Toast.makeText(
                context,
                context.getString(R.string.home_free_limit),
                Toast.LENGTH_LONG,
            ).show()
            onOpenPaywall()
            return
        }
        if (!isPremium) {
            InterstitialHelper.showInterstitial(context) {
                onOpenMap(null)
            }
        } else {
            onOpenMap(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.home_open_menu))
                    }
                },
                actions = {
                    // Header cloud button: icon shows the CURRENT mode
                    // (cloud = online, cloud-off = offline); tap toggles
                    // with an Arabic confirmation Snackbar.
                    OfflineModeButton(
                        isOffline = isOfflineMode,
                        onToggle = { enabled ->
                            viewModel.setOfflineMode(enabled)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (enabled) {
                                        context.getString(R.string.map_offline_on)
                                    } else {
                                        context.getString(R.string.map_offline_off)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Phase 8: banner above BottomAppBar (BottomAppBar lives in MainContainer).
            if (!isPremium) {
                AdBanner()
            }
        },
    ) { padding ->
        if (showWizard) {
            HomeWizard(
                viewModel = viewModel,
                onPickOnMap = { onOpenMap(null) },
                modifier = Modifier.padding(padding),
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Phase 10.4.2.1: prominent active-trip widget. Tap opens the
            // map to view/edit mode or destination.
            activeTrip?.let { trip ->
                Card(
                    // Phase 10.4.3.8: widget opens the live route map.
                    onClick = { onOpenTripRoute?.invoke(trip.id) ?: onOpenMap(trip) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                R.string.home_current_trip,
                                trip.transportMode.icon,
                                trip.locationName,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = trip.etaMinutes?.let { stringResource(R.string.trips_eta, it) }
                                ?: stringResource(R.string.trips_tracking_live),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = stringResource(R.string.home_empty_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (locations.isEmpty()) {
                    stringResource(R.string.home_empty_hint)
                } else {
                    stringResource(R.string.home_sub_count, locations.size)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { handleAddDestination() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.home_add_cta),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        }
    }
}
