package com.app.wasalna.ui.screens.myplaces

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.R
import com.app.wasalna.data.service.TrackingService
import com.app.wasalna.domain.model.CairoTransitStations
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.model.NearestStation
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.model.displayName
import com.app.wasalna.ui.components.TransitApproxNote
import com.app.wasalna.ui.components.TransitGuideCard
import com.app.wasalna.ui.ads.AdBanner
import com.app.wasalna.ui.ads.InterstitialHelper
import com.app.wasalna.ui.components.ConfirmDialog
import com.app.wasalna.ui.components.HomeWorkQuickRow
import com.app.wasalna.ui.components.QuickPlaceEditDialog
import com.app.wasalna.ui.screens.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Phase 9-2-fix: dedicated "My Places" screen.
 * Owns its TopAppBar (no shared header) + the saved-locations list
 * with Activate Alarm + Delete per item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPlacesScreen(
    onBack: () -> Unit = {},
    onAddDestination: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    // Elderly simplification (user order Oct 2026): no ads.
    val elderly = com.app.wasalna.ui.theme.LocalElderlyMode.current
    // Phase 10.4.2: persisted mode rides along into the tracking service.
    val transportMode by viewModel.transportMode.collectAsState()
    // DYNAMIC WAKE-UP: persisted alarm mode + minutes (trip-setup dialog).
    val alarmMode by viewModel.alarmMode.collectAsState()
    val alarmTimeMinutes by viewModel.alarmTimeMinutes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var trackingName by remember { mutableStateOf<String?>(null) }
    // Phase 10.4.2.1: battery-optimization gate dialog state.
    var showBatteryDialog by remember { mutableStateOf(false) }
    // DYNAMIC WAKE-UP: trip-setup dialog state (pending place + visibility).
    var pendingTrip by remember { mutableStateOf<Location?>(null) }
    var showAlarmSetup by remember { mutableStateOf(false) }
    // Phase 10-Fix: local filter + rename dialog state (CRUD).
    var filter by remember { mutableStateOf("") }
    // Declutter (user order Oct 2026): backup lives in the overflow menu;
    // Home/Work auto-collapses once both are set.
    var showMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Location?>(null) }
    // رسائل التأكيد: حذف مكان محفوظ إجراء مُتلِف — يُطلب التأكيد أولًا.
    var pendingDelete by remember { mutableStateOf<Location?>(null) }
    // Map UX fixes: Home & Work quick places (visible on My Places only,
    // never on the Map view). Tap = quick trip (free plan: interstitial
    // first); long-press = set-location dialog.
    val homeLat by viewModel.homeLat.collectAsState()
    val homeLng by viewModel.homeLng.collectAsState()
    val homeAddress by viewModel.homeAddress.collectAsState()
    val workLat by viewModel.workLat.collectAsState()
    val workLng by viewModel.workLng.collectAsState()
    val workAddress by viewModel.workAddress.collectAsState()
    // Auto-collapses once both Home & Work are set.
    var homeWorkExpanded by remember(homeAddress, workAddress) {
        mutableStateOf(homeAddress.isBlank() || workAddress.isBlank())
    }
    var quickEditTarget by remember { mutableStateOf<String?>(null) }
    // Phase 10.8.0b: SAF backup launchers (no permissions needed).
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExport ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
            }
            launch(kotlinx.coroutines.Dispatchers.Main) {
                snackbarHostState.showSnackbar(context.getString(R.string.myplaces_backup_ok))
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.myplaces_backup_unread))
                }
                return@launch
            }
            val count = viewModel.importBackup(text)
            launch(kotlinx.coroutines.Dispatchers.Main) {
                snackbarHostState.showSnackbar(
                    if (count < 0) {
                        context.getString(R.string.myplaces_backup_invalid)
                    } else {
                        context.getString(R.string.myplaces_restored, count)
                    },
                )
            }
        }
    }
    // DYNAMIC WAKE-UP: trip-setup gate — permission/battery checks, then the
    // Arabic mode dialog (per-trip choice, persisted); confirm starts tracking.
    fun requestTrip(place: Location) {
        if (!hasLocationPermission(context)) {
            Toast.makeText(
                context,
                context.getString(R.string.home_location_required),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (!isBatteryUnrestricted(context)) {
            showBatteryDialog = true
            return
        }
        pendingTrip = place
        showAlarmSetup = true
    }
    fun startQuickTrip(lat: Double?, lng: Double?, address: String) {
        if (lat == null || lng == null) {
            quickEditTarget = if (address == homeAddress) "home" else "work"
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.myplaces_set_first))
            }
            return
        }
        requestTrip(
            Location(
                name = address.ifBlank { context.getString(R.string.quick_trip_fallback) },
                latitude = lat,
                longitude = lng,
            ),
        )
    }
    fun confirmTrip() {
        val place = pendingTrip ?: return
        fun go() {
            startTracking(context, place, transportMode)
            trackingName = place.name
        }
        showAlarmSetup = false
        pendingTrip = null
        if (!isPremium) {
            InterstitialHelper.showInterstitial(context) { go() }
        } else {
            go()
        }
    }
    val visiblePlaces = remember(locations, filter) {
        if (filter.isBlank()) locations
        else locations.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == TrackingService.ACTION_DESTINATION_REACHED) {
                    trackingName = null
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.home_destination_reached))
                    }
                }
            }
        }
        val filter = IntentFilter(TrackingService.ACTION_DESTINATION_REACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.myplaces_title)) },
                navigationIcon = {
                    // Phase 10.4.1: back reliably returns to the Home tab.
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_home))
                    }
                },
                actions = {
                    // Backup (export/import) moved here from the content
                    // column — rare action, no permanent row.
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.myplaces_export),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.myplaces_export)) },
                            onClick = {
                                showMenu = false
                                val json = com.app.wasalna.utils.PlacesBackup.buildJson(
                                    homeLat, homeLng, homeAddress,
                                    workLat, workLng, workAddress,
                                    locations,
                                )
                                pendingExport = json
                                exportLauncher.launch("wasalna-places.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.myplaces_import)) },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("application/json"))
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Elderly: bigger add button (64dp) via dimens.
            FloatingActionButton(
                onClick = onAddDestination,
                modifier = Modifier.size(com.app.wasalna.ui.theme.LocalWasalnaDimens.current.buttonHeight + 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.common_add_destination))
            }
        },
        bottomBar = {
            // Elderly: ads hidden — fewer distractions, calmer screen.
            if (!isPremium && !elderly) {
                AdBanner()
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            trackingName?.let { name ->
                Text(
                    text = stringResource(R.string.home_tracking_started, name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Search + count in one row (user order Oct 2026).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.myplaces_search)) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                )
                Text(
                    text = "${visiblePlaces.size}/${locations.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Collapsible Home & Work (user order Oct 2026): open until
            // both are set, then folds to one header row.
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = { homeWorkExpanded = !homeWorkExpanded },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.myplaces_home_work),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (homeWorkExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.myplaces_home_work),
                    )
                }
            }
            if (homeWorkExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HomeWorkQuickRow(
                    homeAddress = homeAddress,
                    workAddress = workAddress,
                    onHomeClick = { startQuickTrip(homeLat, homeLng, homeAddress) },
                    onWorkClick = { startQuickTrip(workLat, workLng, workAddress) },
                    onHomeEdit = { quickEditTarget = "home" },
                    onWorkEdit = { quickEditTarget = "work" },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (locations.isEmpty()) {
                Text(
                    text = stringResource(R.string.myplaces_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else if (visiblePlaces.isEmpty()) {
                Text(
                    text = stringResource(R.string.myplaces_no_match, filter),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                // Audit fix (9 Oct 2026): unconstrained LazyColumn inside a
                // non-scrollable Column could be pushed off-screen — the
                // saved-places list gets remaining space and scrolls itself.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visiblePlaces, key = { it.id }) { location ->
                        SavedPlaceCard(
                            location = location,
                            isTracking = trackingName != null,
                            onActivate = {
                                // DYNAMIC WAKE-UP: opens the Arabic mode dialog;
                                // confirmTrip() starts tracking (gates inside).
                                requestTrip(location)
                            },
                            onEdit = { renameTarget = location },
                            onDelete = { pendingDelete = location },
                        )
                    }
                }
            }
        }
    }
    // DYNAMIC WAKE-UP: Arabic trip-setup dialog (vehicle + mode + minutes).
    if (showAlarmSetup) {
        // TRANSIT pre-trip hint: nearest table station to the chosen
        // destination (origin unknown until tracking starts). Pure
        // haversine, no IO — safe to compute during composition.
        val transitAlighting: NearestStation? = pendingTrip?.let {
            CairoTransitStations.nearest(it.latitude, it.longitude)
        }
        AlarmSetupDialog(
            vehicle = transportMode,
            onVehicleChange = viewModel::setTransportMode,
            transitAlighting = transitAlighting,
            mode = alarmMode,
            minutes = alarmTimeMinutes,
            onModeChange = viewModel::setAlarmMode,
            onMinutesChange = viewModel::setAlarmTimeMinutes,
            onConfirm = { confirmTrip() },
            onDismiss = {
                showAlarmSetup = false
                pendingTrip = null
            },
        )
    }
    // Phase 10.4.2.1: blocking battery gate — no unrestricted battery,
    // no background alarm (OEMs would silently kill it).
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.myplaces_battery_title)) },
            text = {
                Text(
                    stringResource(R.string.myplaces_battery_text),
                )
            },
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
    // Map UX fixes: Home/Work set-location dialog.
    quickEditTarget?.let { target ->
        val isHome = target == "home"
        QuickPlaceEditDialog(
            title = if (isHome) stringResource(R.string.quick_home) else stringResource(R.string.quick_work),
            initialAddress = if (isHome) homeAddress else workAddress,
            initialLat = if (isHome) homeLat else workLat,
            initialLng = if (isHome) homeLng else workLng,
            onDismiss = { quickEditTarget = null },
            onSave = { lat, lng, address ->
                if (isHome) viewModel.setHome(lat, lng, address)
                else viewModel.setWork(lat, lng, address)
            },
        )
    }
    // رسالة تأكيد قبل حذف المكان المحفوظ — التراجع = لا يحدث شيء.
    pendingDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.myplaces_delete_title),
            message = stringResource(R.string.myplaces_delete_msg, target.name),
            confirmLabel = stringResource(R.string.myplaces_delete_confirm),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteLocation(target)
                pendingDelete = null
                // Elderly safety net: undo via snackbar action.
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.myplaces_deleted, target.name),
                        actionLabel = context.getString(R.string.common_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreLocation(target)
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
    // Phase 10-Fix: rename dialog (Update CRUD).
    renameTarget?.let { target ->
        var draft by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.myplaces_rename)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.myplaces_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameLocation(target, draft)
                    renameTarget = null
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * DYNAMIC WAKE-UP (التنبيه المرتبط بالزحام) — Arabic trip-setup dialog.
 * Segmented DISTANCE/TIME choice + minutes stepper (TIME only) with a
 * one-line explanation of the difference. Choices persist via HomeViewModel
 * (StateFlow → DataStore); TrackingService honors them live, mid-trip
 * switches included. All touch targets ≥ 48dp (Material3).
 */
@Composable
private fun AlarmSetupDialog(
    vehicle: TransportMode,
    onVehicleChange: (TransportMode) -> Unit,
    transitAlighting: NearestStation? = null,
    mode: String,
    minutes: Int,
    onModeChange: (String) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isTime = mode == "TIME"
    // Two-step wizard (user order Oct 2026): 0 = vehicle, 1 = alarm mode.
    var step by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_setup_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (step == 0) {
                // VEHICLE TYPE (user order Oct 2026): big icon cards per
                // vehicle — one tap selects (persisted, last choice
                // remembered); Start then begins tracking with it.
                // 64dp cards, 8dp grid, RTL-safe (Row/Arrangement only).
                Text(
                    text = stringResource(R.string.vehicle_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TransportMode.pickerEntries.chunked(2).forEach { rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowModes.forEach { option ->
                            val label = when (option) {
                                TransportMode.CAR -> stringResource(R.string.transport_car)
                                TransportMode.MOTORCYCLE -> stringResource(R.string.vehicle_motorcycle)
                                TransportMode.BICYCLE -> stringResource(R.string.vehicle_bicycle)
                                TransportMode.WALK -> stringResource(R.string.transport_walk)
                                TransportMode.TRANSIT -> stringResource(R.string.vehicle_transit)
                                TransportMode.METRO -> stringResource(R.string.vehicle_metro)
                                TransportMode.MONORAIL -> stringResource(R.string.vehicle_monorail)
                                TransportMode.TRAIN -> stringResource(R.string.vehicle_train_lrt)
                            }
                            if (option == vehicle) {
                                Button(
                                    onClick = { onVehicleChange(option) },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onVehicleChange(option) },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                        // Keep the 2-column grid balanced on odd rows.
                        if (rowModes.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // TRANSIT (Oct 2026, approximate): alighting hint near the
                // chosen destination + honesty label. Origin unknown
                // pre-trip, so no boarding line here (mid-trip card has it).
                if (vehicle == TransportMode.TRANSIT && transitAlighting != null) {
                    TransitGuideCard(
                        boardingText = null,
                        alightingText = stringResource(
                            R.string.transit_alight_at,
                            transitAlighting.station.displayName(),
                        ) + " " + stringResource(
                            R.string.transit_walk_leg,
                            walkKm(transitAlighting.distanceM),
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (vehicle == TransportMode.TRANSIT) {
                    TransitApproxNote()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlarmModeButton(
                        selected = !isTime,
                        label = stringResource(R.string.alarm_mode_distance),
                        onClick = { onModeChange("DISTANCE") },
                    )
                    AlarmModeButton(
                        selected = isTime,
                        label = stringResource(R.string.alarm_mode_time),
                        onClick = { onModeChange("TIME") },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (isTime) R.string.alarm_mode_explain_time
                        else R.string.alarm_mode_explain_distance,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isTime) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(3, 5, 10).forEach { preset ->
                            if (preset == minutes) {
                                Button(
                                    onClick = { onMinutesChange(preset) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Text("$preset")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onMinutesChange(preset) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Text("$preset")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = { onMinutesChange(minutes - 1) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Remove,
                                contentDescription = stringResource(R.string.alarm_minutes_decrease),
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.alarm_time_minutes_label,
                                minutes,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        IconButton(
                            onClick = { onMinutesChange(minutes + 1) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.alarm_minutes_increase),
                            )
                        }
                    }
                }
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                TextButton(onClick = { step = 1 }) {
                    Text(stringResource(R.string.alarm_setup_next))
                }
            } else {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.alarm_setup_start))
                }
            }
        },
        dismissButton = {
            if (step == 0) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.alarm_setup_cancel))
                }
            } else {
                TextButton(onClick = { step = 0 }) {
                    Text(stringResource(R.string.alarm_setup_back))
                }
            }
        },
    )
}

@Composable
private fun RowScope.AlarmModeButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun SavedPlaceCard(
    location: Location,
    isTracking: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                // Card overflow (user order Oct 2026): rename/delete hide
                // behind ⋮ — the big activate button keeps the stage.
                var cardMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { cardMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.myplaces_card_menu),
                    )
                }
                DropdownMenu(
                    expanded = cardMenu,
                    onDismissRequest = { cardMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.myplaces_rename)) },
                        onClick = {
                            cardMenu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.myplaces_delete_confirm)) },
                        onClick = {
                            cardMenu = false
                            onDelete()
                        },
                    )
                }
            }
            Text(
                text = "${location.latitude}, ${location.longitude}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onActivate,
                enabled = !isTracking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.AlarmOn,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.home_activate_alarm))
            }
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

/** Straight-line meters → "1.2" km string (US locale, caller adds the unit resource). */
private fun walkKm(distanceM: Float): String =
    String.format(java.util.Locale.US, "%.1f", distanceM / 1000f)

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
