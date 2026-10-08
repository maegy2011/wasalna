package com.app.wasalna.ui.screens.trips

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
    val activeTrip by viewModel.activeTrip.collectAsState()
    val pastTrips by viewModel.pastTrips.collectAsState()
    val savedPlaces by viewModel.savedPlaces.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Current", "Future", "Past")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
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
                        EmptyNote("No active trip — start tracking from My Places.")
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
                                            text = trip.etaMinutes?.let { "ETA: $it mins" }
                                                ?: "Tracking live…",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Started ${formatTime(trip.startTime)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (savedPlaces.isEmpty()) {
                        EmptyNote("No saved places yet — add destinations from Home.")
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
                        EmptyNote("No completed trips yet — your history appears here.")
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
                                            text = "Finished ${formatTime(trip.endTime ?: trip.startTime)}" +
                                                (trip.etaMinutes?.let { " • ~$it mins" } ?: ""),
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
