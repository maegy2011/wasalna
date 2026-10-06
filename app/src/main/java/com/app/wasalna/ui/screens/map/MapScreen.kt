package com.app.wasalna.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private val DefaultCairo = LatLng(30.0444, 31.2357)

/**
 * Phase 4: destination picker — search at top, predictions dropdown,
 * GoogleMap filling the rest, Save FAB enabled only when a place is selected.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onSaved: () -> Unit = {},
) {
    val query by viewModel.searchQuery.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val selectedName by viewModel.selectedName.collectAsState()
    val selectedLatLng by viewModel.selectedLatLng.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLatLng ?: DefaultCairo, 12f)
    }

    // Move camera whenever a place is selected.
    LaunchedEffect(selectedLatLng) {
        selectedLatLng?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
        }
    }

    Scaffold(
        floatingActionButton = {
            // Enabled only when a destination is selected (hidden otherwise).
            if (selectedLatLng != null && selectedName != null) {
            ExtendedFloatingActionButton(
                onClick = { viewModel.saveDestination(onSaved) },
                icon = {
                    if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp)
                    else Icon(Icons.Default.Check, contentDescription = null)
                },
                text = { Text(if (isSaving) "Saving…" else "Save Destination") },
            )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Search destination") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onQueryChange("")
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
            )

            if (predictions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    LazyColumn {
                        items(predictions, key = { it.placeId }) { prediction ->
                            ListItem(
                                headlineContent = {
                                    Text(prediction.getPrimaryText(null).toString())
                                },
                                supportingContent = {
                                    Text(prediction.getSecondaryText(null).toString())
                                },
                                modifier = Modifier.clickable {
                                    viewModel.fetchPlaceDetails(prediction)
                                },
                            )
                        }
                    }
                }
            }

            selectedName?.let { name ->
                Text(
                    text = "Selected: $name",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                ) {
                    selectedLatLng?.let { latLng ->
                        Marker(
                            state = MarkerState(position = latLng),
                            title = selectedName,
                        )
                    }
                }
                if (selectedLatLng == null) {
                    Text(
                        text = "Search above to pick a destination",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
