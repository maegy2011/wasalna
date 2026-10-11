package com.app.wasalna.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.app.wasalna.R
import com.app.wasalna.data.service.TrackingService
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.ui.ads.InterstitialHelper
import kotlinx.coroutines.launch

/**
 * Home first-run wizard (mini scope — user order Oct 2026).
 *
 * Shown ONLY when there are no saved places yet and the wizard was never
 * seen. 4 steps inside Home: destination → vehicle → alarm → review/start.
 * The classic welcome screen + one-tap map CTA stay untouched for everyone
 * else. Finish OR skip persists `wizard_seen` — the wizard never reappears.
 */
private const val WIZARD_STEPS = 4

@Composable
fun HomeWizard(
    viewModel: HomeViewModel,
    onPickOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val locations by viewModel.locations.collectAsState()
    val homeLat by viewModel.homeLat.collectAsState()
    val homeLng by viewModel.homeLng.collectAsState()
    val homeAddress by viewModel.homeAddress.collectAsState()
    val workLat by viewModel.workLat.collectAsState()
    val workLng by viewModel.workLng.collectAsState()
    val workAddress by viewModel.workAddress.collectAsState()
    val savedMode by viewModel.transportMode.collectAsState()
    val savedAlarmMode by viewModel.alarmMode.collectAsState()
    val savedAlarmMinutes by viewModel.alarmTimeMinutes.collectAsState()
    val savedLeadMinutes by viewModel.alarmLeadTimeMinutes.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

    // Just-in-time permissions (Oct 2026): trip start requests missing
    // permissions first, then runs — onboarding stays all-optional.
    val permGate = com.app.wasalna.ui.components.rememberPermissionGate()
    var step by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Location?>(null) }
    var vehicle by remember(savedMode) { mutableStateOf(savedMode) }
    var alarmMode by remember(savedAlarmMode) { mutableStateOf(savedAlarmMode) }
    var alarmMinutes by remember(savedAlarmMinutes) { mutableIntStateOf(savedAlarmMinutes) }
    var leadMinutes by remember(savedLeadMinutes) { mutableIntStateOf(savedLeadMinutes) }
    var showBatteryDialog by remember { mutableStateOf(false) }

    fun finish() {
        viewModel.setTransportMode(vehicle)
        viewModel.setAlarmMode(alarmMode)
        viewModel.setAlarmTimeMinutes(alarmMinutes)
        viewModel.setAlarmLeadTimeMinutes(leadMinutes)
        viewModel.markWizardSeen()
    }

    fun startTrip() {
        val dest = picked ?: return
        // Grant-first (Oct 2026): missing permissions are requested now;
        // the trip runs right after the grant (TripStarter re-checks).
        permGate.runTripGated {
            if (!isBatteryUnrestricted(context)) {
                showBatteryDialog = true
                return@runTripGated
            }
            finish()
            fun go() = startTracking(context, dest, vehicle)
            if (!isPremium) {
                InterstitialHelper.showInterstitial(context) { go() }
            } else {
                go()
            }
        }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.myplaces_battery_title)) },
            text = { Text(stringResource(R.string.myplaces_battery_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    openBatterySettings(context)
                }) {
                    Text(stringResource(R.string.common_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.wizard_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (step + 1) / WIZARD_STEPS.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_step_of, step + 1, WIZARD_STEPS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            0 -> WizardDestinationStep(
                locations = locations,
                homeLat = homeLat,
                homeLng = homeLng,
                homeAddress = homeAddress,
                workLat = workLat,
                workLng = workLng,
                workAddress = workAddress,
                picked = picked,
                onPick = { picked = it },
                onPickOnMap = onPickOnMap,
            )
            1 -> WizardVehicleStep(
                selected = vehicle,
                onSelect = { vehicle = it },
            )
            2 -> WizardAlarmStep(
                alarmMode = alarmMode,
                onAlarmMode = { alarmMode = it },
                alarmMinutes = alarmMinutes,
                onAlarmMinutes = { alarmMinutes = it },
                leadMinutes = leadMinutes,
                onLeadMinutes = { leadMinutes = it },
            )
            else -> WizardReviewStep(
                picked = picked,
                vehicle = vehicle,
                alarmMode = alarmMode,
                alarmMinutes = alarmMinutes,
                leadMinutes = leadMinutes,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step -= 1 },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.wizard_back))
                }
            }
            TextButton(
                onClick = {
                    viewModel.markWizardSeen()
                },
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.wizard_skip))
            }
            if (step < WIZARD_STEPS - 1) {
                Button(
                    onClick = {
                        if (step == 0 && picked == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.wizard_need_place),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            step += 1
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.wizard_next))
                }
            } else {
                Button(
                    onClick = {
                        if (picked == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.wizard_need_place),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            scope.launch { startTrip() }
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.alarm_setup_start))
                }
            }
        }
    }
}

@Composable
private fun WizardDestinationStep(
    locations: List<Location>,
    homeLat: Double?,
    homeLng: Double?,
    homeAddress: String,
    workLat: Double?,
    workLng: Double?,
    workAddress: String,
    picked: Location?,
    onPick: (Location) -> Unit,
    onPickOnMap: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.wizard_dest_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.wizard_dest_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (homeLat != null && homeLng != null) {
            WizardPlaceCard(
                title = stringResource(R.string.quick_home),
                subtitle = homeAddress,
                selected = picked?.name == homeAddress && picked?.latitude == homeLat,
                onClick = {
                    onPick(Location(name = homeAddress, latitude = homeLat, longitude = homeLng))
                },
            )
        }
        if (workLat != null && workLng != null) {
            WizardPlaceCard(
                title = stringResource(R.string.quick_work),
                subtitle = workAddress,
                selected = picked?.name == workAddress && picked?.latitude == workLat,
                onClick = {
                    onPick(Location(name = workAddress, latitude = workLat, longitude = workLng))
                },
            )
        }
        locations.forEach { place ->
            WizardPlaceCard(
                title = place.name,
                subtitle = "${place.latitude}, ${place.longitude}",
                selected = picked?.name == place.name,
                onClick = { onPick(place) },
            )
        }
        OutlinedButton(
            onClick = onPickOnMap,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.wizard_dest_map))
        }
    }
}

@Composable
private fun WizardPlaceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = (if (selected) "✓ " else "") + title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun vehicleLabel(mode: TransportMode): String = stringResource(
    when (mode) {
        TransportMode.WALK -> R.string.transport_walk
        TransportMode.CAR -> R.string.transport_car
        TransportMode.MOTORCYCLE -> R.string.vehicle_motorcycle
        TransportMode.BICYCLE -> R.string.vehicle_bicycle
        TransportMode.TRANSIT -> R.string.vehicle_transit
        TransportMode.METRO -> R.string.vehicle_metro
        TransportMode.MONORAIL -> R.string.vehicle_monorail
        TransportMode.TRAIN -> R.string.vehicle_train_lrt
    },
)

@Composable
private fun WizardVehicleStep(
    selected: TransportMode,
    onSelect: (TransportMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.vehicle_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TransportMode.entries.forEach { mode ->
            WizardPlaceCard(
                title = vehicleLabel(mode),
                subtitle = "",
                selected = selected == mode,
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun WizardAlarmStep(
    alarmMode: String,
    onAlarmMode: (String) -> Unit,
    alarmMinutes: Int,
    onAlarmMinutes: (Int) -> Unit,
    leadMinutes: Int,
    onLeadMinutes: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.alarm_setup_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        WizardModeCard(
            title = stringResource(R.string.alarm_mode_distance_short),
            subtitle = stringResource(R.string.alarm_mode_explain_distance),
            selected = alarmMode == "DISTANCE",
            onClick = { onAlarmMode("DISTANCE") },
        )
        WizardModeCard(
            title = stringResource(R.string.alarm_mode_time_short),
            subtitle = stringResource(R.string.alarm_mode_explain_time),
            selected = alarmMode == "TIME",
            onClick = { onAlarmMode("TIME") },
        )
        if (alarmMode == "TIME") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.alarm_time_minutes_label, alarmMinutes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = alarmMinutes.toFloat(),
                onValueChange = { onAlarmMinutes(it.toInt().coerceIn(1, 30)) },
                valueRange = 1f..30f,
                steps = 28,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_lead_label, leadMinutes),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = leadMinutes.toFloat(),
            onValueChange = { onLeadMinutes(it.toInt().coerceIn(1, 30)) },
            valueRange = 1f..30f,
            steps = 28,
        )
    }
}

@Composable
private fun WizardModeCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WizardReviewStep(
    picked: Location?,
    vehicle: TransportMode,
    alarmMode: String,
    alarmMinutes: Int,
    leadMinutes: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.wizard_review_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        WizardReviewRow(
            label = stringResource(R.string.wizard_review_dest),
            value = picked?.name ?: "—",
        )
        WizardReviewRow(
            label = stringResource(R.string.wizard_review_vehicle),
            value = vehicleLabel(vehicle),
        )
        WizardReviewRow(
            label = stringResource(R.string.wizard_review_alarm),
            value = if (alarmMode == "TIME") {
                stringResource(R.string.alarm_time_minutes_label, alarmMinutes)
            } else {
                stringResource(R.string.alarm_mode_distance_short)
            } + " • " + stringResource(R.string.wizard_lead_label, leadMinutes),
        )
    }
}

@Composable
private fun WizardReviewRow(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatterySettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.myplaces_battery_manual), Toast.LENGTH_LONG).show()
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

// Real-trip upgrade (Oct 2026): all starts funnel through TripStarter —
// pre-flight gate (GPS/permission/battery) + sim conflict refusal.
private fun startTracking(
    context: Context,
    location: Location,
    mode: TransportMode,
) {
    com.app.wasalna.data.service.TripStarter.startRealTrip(
        context, location.latitude, location.longitude, location.name, mode,
    )
}
