package com.app.wasalna.ui.screens.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.wasalna.R
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.ui.components.SimulationBanner
import com.app.wasalna.ui.components.SimulationControlBar
import com.app.wasalna.ui.components.TripSimulationPanel

/**
 * Standalone trip-simulator screen (user order Oct 2026): fully
 * separated from every other screen — no panel inside the trip sheet,
 * no banner on the map. Opened only from its own map button, which
 * itself appears only when Settings → Advanced → simulator is enabled.
 *
 * Full-screen dialog (not a bottom sheet): the simulator is a testing
 * tool that deserves the whole stage. All controls read the global
 * [TripSimulationController] flows directly — same behavior as before,
 * new home. No `!!`, all strings resourced (EN + AR parity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSimulatorDialog(
    destLat: Double,
    destLng: Double,
    destName: String,
    mode: TransportMode,
    routeGeoJson: String?,
    originProvider: suspend () -> android.location.Location?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.sim_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.common_close),
                            )
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimulationBanner(modifier = Modifier.padding(vertical = 4.dp))
                SimulationControlBar(modifier = Modifier.padding(vertical = 4.dp))
                TripSimulationPanel(
                    destLat = destLat,
                    destLng = destLng,
                    destName = destName,
                    mode = mode,
                    routeGeoJson = routeGeoJson,
                    originProvider = originProvider,
                )
            }
        }
    }
}
