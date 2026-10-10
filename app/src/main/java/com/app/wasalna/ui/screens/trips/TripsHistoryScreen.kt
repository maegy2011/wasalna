package com.app.wasalna.ui.screens.trips

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 10.4.2.1: trip history with top tabs —
 * Current (live trip), Future (saved places), Past (finished trips).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsHistoryScreen(
    onBack: () -> Unit = {},
    // Phase 10.4.3.8: null = cards stay static.
    onOpenTripRoute: ((Long) -> Unit)? = null,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activeTrip by viewModel.activeTrip.collectAsState()
    val pastTrips by viewModel.pastTrips.collectAsState()
    val savedPlaces by viewModel.savedPlaces.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.trips_tab_current), stringResource(R.string.trips_tab_future), stringResource(R.string.trips_tab_past))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_home))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                    )
                }
            }
            when (tab) {
                0 -> {
                    val trip = activeTrip
                    if (trip == null) {
                        EmptyNote(stringResource(R.string.trips_empty_current))
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "${trip.transportMode.icon} ${trip.locationName}",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = trip.etaMinutes?.let { stringResource(R.string.trips_eta, it) }
                                                ?: stringResource(R.string.trips_tracking_live),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.trips_started, formatTime(trip.startTime)),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        // Phase 10.8.0c: live trip share (no server).
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    com.app.wasalna.utils.TripShare.share(context, trip)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                        ) {
                                            Text(stringResource(R.string.trips_share_live))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (savedPlaces.isEmpty()) {
                        EmptyNote(stringResource(R.string.trips_empty_future))
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(savedPlaces, key = { it.id }) { place ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "📍 ${place.name}",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${place.latitude}, ${place.longitude}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (pastTrips.isEmpty()) {
                        EmptyNote(stringResource(R.string.trips_empty_past))
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(pastTrips, key = { it.id }) { trip ->
                                Card(
                                    // Phase 10.4.3.8: tap reviews the recorded path.
                                    onClick = { onOpenTripRoute?.invoke(trip.id) },
                                    enabled = onOpenTripRoute != null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "${trip.transportMode.icon} ${trip.locationName}",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.trips_finished,
                                                formatTime(trip.endTime ?: trip.startTime),
                                            ) + (trip.etaMinutes?.let { " • ~$it mins" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(millis))
