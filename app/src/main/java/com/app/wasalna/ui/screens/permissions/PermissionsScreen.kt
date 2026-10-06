package com.app.wasalna.ui.screens.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Phase 3: runtime-permission onboarding (Material3).
 *
 * Required to start a journey: foreground location + battery-optimization ignore.
 * Optional: background location note, overlay, notifications (API 33+).
 */
@Composable
fun PermissionsScreen(
    onDone: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check statuses when returning from system settings (battery / overlay).
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun isBatteryIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    // Read on every composition (resumeTick forces re-read after settings return).
    @Suppress("UNUSED_EXPRESSION")
    resumeTick.toString()
    val fineLocationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseLocationGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationGranted = fineLocationGranted || coarseLocationGranted
    val backgroundLocationGranted =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }
    val batteryIgnored = isBatteryIgnored()
    val overlayGranted = canDrawOverlays()
    val notificationsGranted =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    // Location launcher (foreground pair; background requested separately per Play policy).
    var locationRationale by remember { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // State re-reads automatically on recomposition via hasPermission() — force it.
        resumeTick++
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        resumeTick++
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        resumeTick++
    }

    // Battery-optimization: must go through system settings intent.
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        resumeTick++
    }

    // Overlay: system settings screen, no result — ON_RESUME re-check covers it.
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        resumeTick++
    }

    val canStartJourney = locationGranted && batteryIgnored

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Enable what Wasalna needs",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Location tracking and uninterrupted background alarms " +
                "are required so we can wake you before your stop — " +
                "even with the screen off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // (a) Foreground location — required.
        PermissionCard(
            icon = Icons.Filled.LocationOn,
            title = "Location (required)",
            description = "Used to track your trip and trigger the alarm near " +
                "your destination. Please choose “While using the app” or “Precise”.",
            granted = locationGranted,
            actionLabel = if (locationGranted) "Granted" else "Grant location",
            onAction = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
                locationRationale = true
            },
        )

        // (a2) Background location — separate, optional-for-now note.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionCard(
                icon = Icons.Filled.LocationOn,
                title = "Background location (recommended)",
                description = "Allows alarms to keep working when the app is in " +
                    "the background. Android requires this to be granted " +
                    "separately from Settings.",
                granted = backgroundLocationGranted,
                actionLabel = if (backgroundLocationGranted) "Granted" else "Grant background",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        )
                    }
                },
            )
        }

        // (b) Battery optimization — required.
        PermissionCard(
            icon = Icons.Filled.BatteryChargingFull,
            title = "Ignore battery optimizations (required)",
            description = "Prevents the system from killing Wasalna mid-trip. " +
                "Opens system settings — choose “Allow” for Wasalna.",
            granted = batteryIgnored,
            actionLabel = if (batteryIgnored) "Allowed" else "Allow",
            onAction = {
                batteryLauncher.launch(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )

        // (c) Overlay — optional.
        PermissionCard(
            icon = Icons.Filled.OpenInNew,
            title = "Display over other apps (optional)",
            description = "Shows a full-screen alarm even when another app is open.",
            granted = overlayGranted,
            actionLabel = if (overlayGranted) "Allowed" else "Allow",
            onAction = {
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )

        // Notifications — Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = "Notifications (recommended)",
                description = "Shows trip status and alarm notifications.",
                granted = notificationsGranted,
                actionLabel = if (notificationsGranted) "Granted" else "Allow",
                onAction = {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = {
                viewModel.completeOnboarding()
                onDone()
            },
            enabled = canStartJourney,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start Journey")
        }
        if (!canStartJourney) {
            Text(
                text = "Grant location access and allow ignoring battery " +
                    "optimizations to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (locationRationale && !locationGranted) {
            Text(
                text = "Location was denied. You can grant it anytime from " +
                    "system Settings → Apps → Wasalna → Permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = if (granted) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.Pending
                        },
                        contentDescription = if (granted) "Granted" else "Pending",
                        tint = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (granted) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}
