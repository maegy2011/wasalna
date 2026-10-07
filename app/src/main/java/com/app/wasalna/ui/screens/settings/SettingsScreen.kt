package com.app.wasalna.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val elderlyMode by viewModel.elderlyMode.collectAsState()
    val leadTime by viewModel.leadTimeMinutes.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val silentAlarm by viewModel.silentAlarm.collectAsState()
    val customRingtoneUri by viewModel.customRingtoneUri.collectAsState()
    val adaptiveAlarm by viewModel.adaptiveAlarm.collectAsState()

    // Phase 10.4.3.1: system ringtone picker (premium only). Crashes and
    // unreadable files are caught — a toast explains, nothing breaks.
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        runCatching {
            if (result.resultCode != Activity.RESULT_OK) return@runCatching
            val data = result.data ?: return@runCatching
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            } ?: return@runCatching
            uri?.let { picked ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        picked,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            viewModel.setCustomRingtoneUri(uri.toString())
            Toast.makeText(context, "Custom ringtone saved", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Couldn't use that audio file", Toast.LENGTH_LONG).show()
        }
    }

    // DND override has no DataStore field — visual toggle only for now.
    var dndOverride by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

        // Phase 10.4.3.1: premium custom alarm ringtone (locked → paywall).
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = {
                if (!isPremium) {
                    onOpenPaywall()
                } else {
                    runCatching {
                        val existing: Uri? = customRingtoneUri.ifBlank { null }?.let {
                            runCatching { Uri.parse(it) }.getOrNull()
                        } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose alarm sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            if (existing != null) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                            }
                        }
                        ringtoneLauncher.launch(picker)
                    }.onFailure {
                        Toast.makeText(context, "No ringtone picker found", Toast.LENGTH_LONG).show()
                    }
                }
            },
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
                        text = "Custom Alarm Ringtone",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (!isPremium) {
                            "First Class only"
                        } else if (customRingtoneUri.isBlank()) {
                            "System default"
                        } else {
                            "Custom tone set ✓"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    if (!isPremium) Icons.Filled.Lock else Icons.Filled.MusicNote,
                    contentDescription = if (!isPremium) "Premium only" else "Pick ringtone",
                )
            }
        }

        // Phase 10.4.3.3: adaptive trigger distance (Walk 500m / Car 2km /
        // Train 4km + speed stretch); off = legacy fixed 2 km.
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
                        text = "Adaptive Alarm Distance",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Trigger adapts to Walk / Car / Train + speed",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = adaptiveAlarm,
                    onCheckedChange = viewModel::setAdaptiveAlarm,
                )
            }
        }

        // Phase 10.4.3.2: offline regions (premium only → paywall).
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = {
                if (isPremium) onOpenOfflineMaps() else onOpenPaywall()
            },
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
                        text = "Offline Maps (Download Regions)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (isPremium) {
                            "Use the map without internet"
                        } else {
                            "First Class only"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    if (!isPremium) Icons.Filled.Lock else Icons.Filled.Download,
                    contentDescription = if (!isPremium) "Premium only" else "Open offline maps",
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
}
