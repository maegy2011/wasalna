package com.app.wasalna.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.app.wasalna.utils.AppLocaleHelper
import com.app.wasalna.utils.AutoStartUtils
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.R
import com.app.wasalna.domain.audio.HeadphoneState
import com.app.wasalna.utils.DndChoice
import com.app.wasalna.utils.DndPolicy

/**
 * Phase 7: Settings — Material3 switches bound to DataStore.
 * - Elderly Mode → DataStore, theme recomposes via MainActivity.
 * - Silent-mode (DND) bypass → persisted 3-way choice (respect/bypass/ask).
 *   BYPASS needs Notification-policy access; denial falls back to respect.
 * - Alarm Lead Time → slider 1..30 min, default 5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    onOpenRestrictions: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val elderlyMode by viewModel.elderlyMode.collectAsState()
    val leadTime by viewModel.leadTimeMinutes.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val silentAlarm by viewModel.silentAlarm.collectAsState()
    val headphoneOnly by viewModel.headphoneOnly.collectAsState()
    val headphoneState by viewModel.headphoneState.collectAsState()
    val customRingtoneUri by viewModel.customRingtoneUri.collectAsState()
    val adaptiveAlarm by viewModel.adaptiveAlarm.collectAsState()
    val proactiveAlert by viewModel.proactiveAlert.collectAsState()
    val nudgeLead by viewModel.nudgeLeadMinutes.collectAsState()
    val forceOffline by viewModel.forceOffline.collectAsState()
    val isTrafficEnabled by viewModel.isTrafficEnabled.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val gradualEnabled by viewModel.gradualEnabled.collectAsState()
    val gradualVibSec by viewModel.gradualVibrationSec.collectAsState()
    val gradualRampSec by viewModel.gradualRampSec.collectAsState()
    val gradualStrength by viewModel.gradualStrength.collectAsState()
    val gradualRingtoneUri by viewModel.gradualRingtoneUri.collectAsState()
    val dndChoice by viewModel.dndChoice.collectAsState()

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

    // Headphone-only alerts: Bluetooth profile state needs BLUETOOTH_CONNECT
    // on API 31+. getDevices-based detection works without it, so a denial
    // only reduces BT-profile accuracy — the toggle still enables.
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission,
    ) { viewModel.setHeadphoneOnly(true) }

    // DND policy-access settings round-trip: picking BYPASS without the
    // grant routes to system Settings; coming back still-denied falls
    // back to RESPECT with an explanation (never a dead toggle).
    val dndSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (viewModel.dndChoice.value == DndChoice.BYPASS &&
            !viewModel.isDndPolicyAccessGranted()
        ) {
            viewModel.setDndChoice(DndChoice.RESPECT)
            Toast.makeText(
                context,
                context.getString(R.string.settings_dnd_denied),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    fun pickDndChoice(choice: String) {
        viewModel.setDndChoice(choice)
        if (choice == DndChoice.BYPASS && !viewModel.isDndPolicyAccessGranted()) {
            dndSettingsLauncher.launch(DndPolicy.policyAccessIntent())
        }
    }


    // Hidden developer section: 5 taps on the version text reveal it.
    // Ordinary users never see Force Offline Routing.
    var devTaps by remember { mutableStateOf(0) }
    var showDev by remember { mutableStateOf(false) }
    // Phase 10.9.x: in-app gradual-alarm tone picker dialog.
    var showGradualPicker by remember { mutableStateOf(false) }

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
    // Audit fix (9 Oct 2026): the content Column was NOT scrollable — on
    // small/elderly phones the bottom cards (Offline Maps, Lead Time)
    // were cut off and unreachable. Now vertically scrollable.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
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

        // In-app language: System default + العربية + English.
        // Persisted in the existing SettingsDataStore (app_language_code).
        // API 33+: LocaleManager recreates activities automatically;
        // API 26–32: recreate manually so the new locale takes effect.
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_language_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_language_sub),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val languageOptions = listOf(
                    "" to stringResource(R.string.settings_language_auto),
                    "ar" to stringResource(R.string.settings_language_arabic),
                    "en" to stringResource(R.string.settings_language_english),
                )
                languageOptions.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                viewModel.setAppLanguage(code)
                                AppLocaleHelper.apply(context, code)
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    (context as? Activity)?.recreate()
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = appLanguage == code,
                            onClick = {
                                viewModel.setAppLanguage(code)
                                AppLocaleHelper.apply(context, code)
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    (context as? Activity)?.recreate()
                                }
                            },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        // Silent-mode (DND) bypass choice — persisted, default RESPECT.
        // Each option carries a one-line explanation; 48dp+ radio rows.
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_dnd_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_dnd_sub),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val dndOptions = listOf(
                    Triple(
                        DndChoice.RESPECT,
                        stringResource(R.string.settings_dnd_respect),
                        stringResource(R.string.settings_dnd_respect_sub),
                    ),
                    Triple(
                        DndChoice.BYPASS,
                        stringResource(R.string.settings_dnd_bypass),
                        stringResource(R.string.settings_dnd_bypass_sub),
                    ),
                    Triple(
                        DndChoice.ASK,
                        stringResource(R.string.settings_dnd_ask),
                        stringResource(R.string.settings_dnd_ask_sub),
                    ),
                )
                dndOptions.forEach { (choice, label, explain) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { pickDndChoice(choice) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = dndChoice == choice,
                            onClick = { pickDndChoice(choice) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = explain,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (dndChoice == DndChoice.BYPASS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (viewModel.isDndPolicyAccessGranted()) {
                        Text(
                            text = stringResource(R.string.settings_dnd_granted),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_dnd_rationale),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                dndSettingsLauncher.launch(DndPolicy.policyAccessIntent())
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.settings_dnd_grant))
                        }
                    }
                }
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

        // Headphone-only alerts (premium): destination alarm plays ONLY
        // through connected headphones — the phone speaker stays silent.
        // Free users tapping the card see the Paywall upsell. The status
        // line below the toggle shows the live headphone connection.
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = {
                if (!isPremium) onOpenPaywall()
            },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        Icons.Filled.Headset,
                        contentDescription = null,
                        tint = if (!isPremium) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_headphone_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (isPremium) {
                                stringResource(R.string.settings_headphone_sub)
                            } else {
                                stringResource(R.string.settings_headphone_locked)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = headphoneOnly && isPremium,
                        enabled = isPremium,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                viewModel.setHeadphoneOnly(false)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                viewModel.setHeadphoneOnly(true)
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (headphoneState) {
                        HeadphoneState.BLUETOOTH ->
                            stringResource(R.string.settings_headphone_bt)
                        HeadphoneState.WIRED ->
                            stringResource(R.string.settings_headphone_wired)
                        HeadphoneState.NONE ->
                            stringResource(R.string.settings_headphone_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (headphoneState == HeadphoneState.NONE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        // Phase 10.10: smartwatch companion (الساعة الذكية) — connection
        // status + test vibration + premium-gated watch-only alert toggle.
        WatchSettingsSection(
            viewModel = viewModel,
            onOpenPaywall = onOpenPaywall,
        )

        // Phase 10.9.x: smart gradual alarm (المنبه التدريجي) — vibration
        // first, then a quiet sound that rises slowly. Disabled by default
        // so the legacy full-volume path is untouched when off.
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_gradual_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_gradual_sub),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = gradualEnabled,
                        onCheckedChange = viewModel::setGradualEnabled,
                    )
                }
                if (gradualEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_gradual_strength),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val strengthOptions = listOf(
                        1 to stringResource(R.string.settings_gradual_strength_1),
                        2 to stringResource(R.string.settings_gradual_strength_2),
                        3 to stringResource(R.string.settings_gradual_strength_3),
                    )
                    strengthOptions.forEach { (level, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { viewModel.setGradualStrength(level) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = gradualStrength == level,
                                onClick = { viewModel.setGradualStrength(level) },
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_gradual_vib_duration),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.settings_gradual_seconds, gradualVibSec),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = gradualVibSec.toFloat(),
                        onValueChange = { viewModel.setGradualVibrationSec(it.toInt()) },
                        valueRange = 5f..20f,
                        steps = 14,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_gradual_ramp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.settings_gradual_seconds, gradualRampSec),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = gradualRampSec.toFloat(),
                        onValueChange = { viewModel.setGradualRampSec(it.toInt()) },
                        valueRange = 15f..60f,
                        steps = 44,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { showGradualPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_gradual_ringtone),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = if (gradualRingtoneUri.isBlank()) {
                                    stringResource(R.string.settings_gradual_ringtone_default)
                                } else {
                                    stringResource(R.string.settings_gradual_ringtone_set)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = stringResource(R.string.settings_gradual_ringtone),
                        )
                    }
                }
            }
        }

        if (showGradualPicker) {
            GradualRingtonePickerDialog(
                currentUri = gradualRingtoneUri,
                onConfirm = { uri ->
                    viewModel.setGradualRingtoneUri(uri)
                    showGradualPicker = false
                    Toast.makeText(context, "Gradual tone saved", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showGradualPicker = false },
            )
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

        // Pre-arrival NUDGE: silent once-per-trip heads-up strictly before
        // the main alarm. Enable switch (default ON) + lead slider 1..10
        // min (default 3) — Arabic-first labels.
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_nudge_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_nudge_sub),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = proactiveAlert,
                        onCheckedChange = viewModel::setProactiveAlert,
                    )
                }
                if (proactiveAlert) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_nudge_lead, nudgeLead),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = nudgeLead.toFloat(),
                        onValueChange = { viewModel.setNudgeLeadMinutes(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    )
                }
            }
        }

        // Hidden developer section — Force Offline + crash-log share live here.
        if (showDev) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Force Offline Routing (Dev)",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Bypass OSRM — always route on-device",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = forceOffline,
                            onCheckedChange = viewModel::setForceOffline,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share crash log",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Send the latest local crash report",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = {
                                runCatching {
                                    com.app.wasalna.utils.CrashReporter.shareLatest(context)
                                }.onFailure {
                                    Toast.makeText(context, "No crash log yet", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text("Share")
                        }
                    }
                }
            }
        }

        // Phase 10.4.3.6: strict-OEM revisit entry (hidden on lenient devices).
        if (AutoStartUtils.isStrictDevice()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = onOpenRestrictions,
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
                            text = "Fix Background Tracking",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "AutoStart + background restrictions",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        Icons.Filled.BatteryAlert,
                        contentDescription = "Fix background tracking",
                    )
                }
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

        // Phase 10.8.0g (traffic): enable/disable switch bound to the
        // existing SettingsDataStore traffic flow (reused, not duplicated).
        // Default OFF. Tile viewport may leave the device (Arabic disclosure).
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
                        text = "الازدحام — Traffic",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "طبقة الازدحام من Google — عرض الخريطة يرسل موقع المنطقة إلى Google. متوقف افتراضيًا.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = isTrafficEnabled,
                    onCheckedChange = viewModel::setTrafficEnabled,
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

        // App version — tap 5x to reveal hidden developer options.
        Text(
            text = "Wasalna v${com.app.wasalna.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    devTaps += 1
                    if (devTaps >= 5) {
                        showDev = true
                        devTaps = 0
                    }
                }
                .padding(16.dp),
        )
    }
    }
}
