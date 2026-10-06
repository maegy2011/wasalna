package com.app.wasalna.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.R

/**
 * Phase 7: Settings — Material3 switches bound to DataStore.
 * - Elderly Mode → DataStore, theme recomposes via MainActivity.
 * - Force Alarm DND Override → local visual toggle only (no DataStore field yet).
 * - Alarm Lead Time → slider 1..30 min, default 5.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val elderlyMode by viewModel.elderlyMode.collectAsState()
    val leadTime by viewModel.leadTimeMinutes.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val silentAlarm by viewModel.silentAlarm.collectAsState()

    // DND override has no DataStore field — visual toggle only for now.
    var dndOverride by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_elderly_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_elderly_sub),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = elderlyMode,
                    onCheckedChange = viewModel::setElderlyMode,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_dnd_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_dnd_sub),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = dndOverride,
                    onCheckedChange = { dndOverride = it },
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_silent_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (isPremium) {
                            stringResource(R.string.settings_silent_sub)
                        } else {
                            stringResource(R.string.settings_silent_locked)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = silentAlarm && isPremium,
                    enabled = isPremium,
                    onCheckedChange = viewModel::setSilentAlarm,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Alarm Lead Time",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "$leadTime min",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Warn me before arrival",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = leadTime.toFloat(),
                    onValueChange = { viewModel.setLeadTimeMinutes(it.toInt()) },
                    valueRange = 1f..30f,
                    steps = 28,
                )
            }
        }
    }
}
