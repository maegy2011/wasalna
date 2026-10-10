package com.app.wasalna.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.app.wasalna.R
import com.app.wasalna.data.service.TrackingService
import com.app.wasalna.data.simulation.TripSimulationController
import com.app.wasalna.domain.model.TransportMode
import kotlinx.coroutines.launch

/**
 * TRIP SIMULATION UI (وضع محاكاة للرحلة للتجربة) — test-only controls.
 *
 * - [SimulationBanner]: clearly-labeled "وضع تجريبي" strip shown on Map +
 *   TripRoute while a simulation runs. Tertiary container, 48dp-adjacent
 *   icon row, Arabic-first strings. Never shown when sim is off.
 * - [TripSimulationPanel]: origin→destination reuse (destination comes from
 *   the existing Map selection state; origin is the live GPS fix). Speed
 *   chips 1x/4x/16x + pause/resume/stop with Arabic labels. Starting fires
 *   the SAME production TrackingService ACTION_START the My Places path
 *   uses — the service itself swaps fused GPS for virtual fixes.
 *
 * Material3, 8dp grid, 48dp+ targets, RTL-safe (Row/Arrangement only).
 * No `!!`, all strings resourced (EN + AR parity).
 */
@Composable
fun SimulationBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = stringResource(R.string.sim_banner_desc),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.sim_banner),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
fun TripSimulationPanel(
    destLat: Double,
    destLng: Double,
    destName: String,
    mode: TransportMode,
    routeGeoJson: String?,
    originProvider: suspend () -> android.location.Location?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val simulating by TripSimulationController.isSimulating.collectAsState()
    val paused by TripSimulationController.isPaused.collectAsState()
    val speed by TripSimulationController.speedMultiplier.collectAsState()
    val progress by TripSimulationController.progress.collectAsState()
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val noFixMessage = stringResource(R.string.sim_no_fix)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sim_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.sim_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (simulating) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
                Text(
                    text = stringResource(R.string.sim_speed_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TripSimulationController.SPEEDS.forEach { mult ->
                        FilterChip(
                            selected = speed == mult,
                            onClick = { TripSimulationController.setSpeedMultiplier(mult) },
                            label = { Text(stringResource(R.string.sim_speed, mult)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (paused) TripSimulationController.resume()
                            else TripSimulationController.pause()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(
                            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(
                                if (paused) R.string.sim_resume else R.string.sim_pause,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            TripSimulationController.stop()
                            context.stopService(
                                Intent(context, TrackingService::class.java),
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.sim_stop),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (starting) return@Button
                        scope.launch {
                            starting = true
                            error = null
                            val origin = runCatching { originProvider() }.getOrNull()
                            if (origin == null) {
                                error = noFixMessage
                                starting = false
                                return@launch
                            }
                            TripSimulationController.start(
                                originLat = origin.latitude,
                                originLng = origin.longitude,
                                destLat = destLat,
                                destLng = destLng,
                                routeGeoJson = routeGeoJson,
                            )
                            val intent = Intent(context, TrackingService::class.java).apply {
                                action = TrackingService.ACTION_START
                                putExtra(TrackingService.EXTRA_LAT, destLat)
                                putExtra(TrackingService.EXTRA_LNG, destLng)
                                putExtra(TrackingService.EXTRA_NAME, destName)
                                putExtra(TrackingService.EXTRA_MODE, mode.name)
                            }
                            ContextCompat.startForegroundService(context, intent)
                            starting = false
                        }
                    },
                    enabled = !starting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.sim_start),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
