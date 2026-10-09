package com.app.wasalna.ui.screens.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Phase 3: runtime-permission onboarding (Material3) — first-launch gate.
 * Order: foreground location → background (API 29+) → battery exemption →
 * overlay (optional) → notifications (API 33+).
 * Never blocks hard: user may continue without permissions (degraded mode).
 */
@Composable
fun PermissionsScreen(
    onDone: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    // Re-check statuses when returning from system settings.
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

    fun openAppSettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openBatterySettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            // Some OEMs lack the request action — fall back to the list screen.
            runCatching {
                val fallback = Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            }
        }
    }

    fun openOverlaySettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { openAppSettings() }
    }

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

    var locationAttempted by remember { mutableStateOf(false) }
    var backgroundAttempted by remember { mutableStateOf(false) }
    var notificationsAttempted by remember { mutableStateOf(false) }

    // never-ask-again detection (safe calls only, no !!).
    val locationRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(
            it,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } ?: false
    val locationNeverAsk = locationAttempted && !locationGranted && !locationRationale
    val backgroundRationale = activity?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        } else {
            false
        }
    } ?: false
    val backgroundNeverAsk =
        backgroundAttempted && !backgroundLocationGranted && !backgroundRationale
    val notificationsNeverAsk = notificationsAttempted && !notificationsGranted

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locationAttempted = true
        resumeTick++
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        backgroundAttempted = true
        resumeTick++
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsAttempted = true
        resumeTick++
    }

    val settingsReturnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        resumeTick++
    }

    fun launchBattery(): Unit {
        // Route through the launcher so ON_RESUME re-check still fires.
        runCatching {
            settingsReturnLauncher.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }.onFailure { e ->
            if (e is ActivityNotFoundException) openBatterySettings() else openAppSettings()
        }
    }

    fun launchOverlay(): Unit {
        runCatching {
            settingsReturnLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }.onFailure { openOverlaySettings() }
    }

    val canStartJourney = locationGranted && batteryIgnored

    fun finish(grant: Boolean) {
        viewModel.completeOnboarding()
        onDone()
    }

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
            text = "فعّل ما يحتاجه وصلنا — Location tracking and uninterrupted " +
                "background alarms are required so we can wake you before " +
                "your stop — even with the screen off. " +
                "نحتاج تتبّع الموقع والعمل في الخلفية لإيقاظك قبل محطتك حتى والشاشة مغلقة.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // (1) Foreground location — required.
        PermissionCard(
            icon = Icons.Filled.LocationOn,
            title = "Location — الموقع (required — مطلوب)",
            description = "Used to track your trip and trigger the alarm near " +
                "your destination. Please choose “While using the app” or “Precise”.",
            arabicDescription = "يُستخدم لتتبّع رحلتك وتشغيل المنبّه قرب وجهتك. " +
                "اختر «أثناء استخدام التطبيق» وفعّل «الموقع الدقيق».",
            granted = locationGranted,
            actionLabel = if (locationGranted) "Granted — تم" else "Grant location — منح الموقع",
            onAction = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
                locationAttempted = true
            },
            showSettings = locationNeverAsk || (locationAttempted && !locationGranted),
            settingsHint = if (locationNeverAsk) {
                "تم الرفض نهائيًا — افتح الإعدادات وامنح الموقع يدويًا: " +
                    "Settings → Apps → Wasalna → Permissions."
            } else {
                "تم رفض الموقع — يمكنك منحه من: " +
                    "الإعدادات ← التطبيقات ← وصلنا ← الأذونات."
            },
            onOpenSettings = { openAppSettings() },
        )

        // (2) Background location — API 29+, gated on foreground grant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionCard(
                icon = Icons.Filled.LocationOn,
                title = "Background location — موقع الخلفية (recommended — مستحسن)",
                description = "Allows alarms to keep working when the app is in " +
                    "the background. Android requires this to be granted " +
                    "separately — grant foreground location first, then tap below.",
                arabicDescription = "يسمح باستمرار المنبّه والتتبّع عندما يكون التطبيق في الخلفية. " +
                    "يشترط أندرويد منح موقع المقدّمة أولًا ثم طلب الخلفية بشكل منفصل.",
                granted = backgroundLocationGranted,
                actionLabel = if (backgroundLocationGranted) {
                    "Granted — تم"
                } else {
                    "Grant background — منح الخلفية"
                },
                actionEnabled = locationGranted,
                disabledHint = "امنح موقع المقدّمة أولًا ثم اطلب موقع الخلفية.",
                onAction = {
                    backgroundLocationLauncher.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    )
                    backgroundAttempted = true
                },
                showSettings = backgroundNeverAsk ||
                    (backgroundAttempted && !backgroundLocationGranted),
                settingsHint = "تعذّر المنح من هنا — افتح إعدادات التطبيق واختر " +
                    "«السماح طوال الوقت» للموقع: Settings → Apps → Wasalna → Permissions → Location.",
                onOpenSettings = { openAppSettings() },
            )
        }

        // (3) Battery optimization exemption — required for the wake-up alarm.
        PermissionCard(
            icon = Icons.Filled.BatteryChargingFull,
            title = "Ignore battery optimizations — تجاهل تحسين البطارية (required — مطلوب)",
            description = "Prevents the system from killing Wasalna mid-trip. " +
                "Opens system settings — choose “Allow” for Wasalna. " +
                "Critical for the wake-up alarm with the screen off.",
            arabicDescription = "يمنع النظام من قتل التطبيق أثناء الرحلة — ضروري لمنبّه الاستيقاظ " +
                "والشاشة مغلقة. سيُفتح إعداد النظام — اختر «سماح» لتطبيق وصلنا.",
            granted = batteryIgnored,
            actionLabel = if (batteryIgnored) "Allowed — تم" else "Allow — سماح",
            onAction = { launchBattery() },
            showSettings = false,
            onOpenSettings = { openBatterySettings() },
        )

        // (4) Overlay — optional.
        PermissionCard(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            title = "Display over other apps — الظهور فوق التطبيقات (optional — اختياري)",
            description = "Shows a full-screen alarm even when another app is open.",
            arabicDescription = "يعرض شاشة المنبّه كاملة حتى لو كان تطبيق آخر مفتوحًا.",
            granted = overlayGranted,
            actionLabel = if (overlayGranted) "Allowed — تم" else "Allow — سماح",
            onAction = { launchOverlay() },
            showSettings = false,
            onOpenSettings = { openOverlaySettings() },
        )

        // (5) Notifications — Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = "Notifications — الإشعارات (recommended — مستحسن)",
                description = "Shows trip status and alarm notifications.",
                arabicDescription = "يعرض حالة الرحلة وإشعارات المنبّه.",
                granted = notificationsGranted,
                actionLabel = if (notificationsGranted) "Granted — تم" else "Allow — سماح",
                onAction = {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    notificationsAttempted = true
                },
                showSettings = notificationsNeverAsk,
                settingsHint = "تم رفض الإشعارات نهائيًا — فعّلها من: " +
                    "الإعدادات ← التطبيقات ← وصلنا ← الإشعارات.",
                onOpenSettings = { openAppSettings() },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { finish(true) },
            enabled = canStartJourney,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Start Journey — بدء الرحلة")
        }
        if (!canStartJourney) {
            Text(
                text = "Grant location access and allow ignoring battery " +
                    "optimizations to continue. " +
                    "امنح الموقع وتجاهل تحسين البطارية للمتابعة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Non-blocking escape hatch: degraded mode, alarm explains what is missing.
        TextButton(
            onClick = { finish(false) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Continue without permissions — متابعة بدون الأذونات")
        }
        Text(
            text = "You can use the app without permissions, but the wake-up alarm " +
                "needs location + battery exemption to work. " +
                "يمكنك استخدام التطبيق بدون أذونات، لكن منبّه الاستيقاظ يحتاج " +
                "الموقع وتجاهل تحسين البطارية ليعمل.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    arabicDescription: String = "",
    actionEnabled: Boolean = true,
    disabledHint: String = "",
    showSettings: Boolean = false,
    settingsHint: String = "",
    onOpenSettings: () -> Unit = {},
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
                if (arabicDescription.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = arabicDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (granted) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedButton(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(actionLabel)
                    }
                    if (!actionEnabled && disabledHint.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = disabledHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showSettings) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (settingsHint.isNotEmpty()) {
                            Text(
                                text = settingsHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Open settings — فتح الإعدادات")
                        }
                    }
                }
            }
        }
    }
}
