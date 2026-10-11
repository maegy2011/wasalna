package com.app.wasalna.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Just-in-time permission gate (Oct 2026, user order): the onboarding
 * Permissions screen stays first-launch + all-optional, but any feature
 * that NEEDS a permission requests it at activation time — grant first,
 * then the feature runs.
 *
 * Usage: `val gate = rememberPermissionGate(); gate.runTripGated { go() }`.
 * The system dialog comes in two chained steps at most (foreground
 * first — Android ignores a background request without it — then
 * background on Q+). After the flow, [action] ALWAYS runs: it re-checks
 * via its own gate (TripStarter) which Toasts + opens settings when
 * still denied. A denial never dead-ends and needs no string here.
 *
 * No `!!`, never throws — a cancelled dialog simply runs the action
 * into the feature's own denial path.
 */
class PermissionGate internal constructor(
    private val context: Context,
    private val foregroundLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    private val backgroundLauncher: ManagedActivityResultLauncher<String, Boolean>,
) {
    private var pendingAction: (() -> Unit)? = null

    internal fun onForegroundResult() {
        // Chain the background request when the foreground grant landed
        // and background is still missing (Q+ only).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            runCatching {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }.onFailure { finish() }
            return
        }
        finish()
    }

    internal fun onBackgroundResult() = finish()

    private fun finish() {
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }

    private fun isGranted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Runs [action] after ensuring trip permissions (location +
     * notifications). Nothing missing → runs immediately, zero dialogs.
     */
    fun runTripGated(action: () -> Unit) {
        val missing = mutableListOf<String>()
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            // Foreground complete — background may still be missing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                pendingAction = action
                runCatching {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }.onFailure { finish() }
                return
            }
            action()
            return
        }
        pendingAction = action
        runCatching { foregroundLauncher.launch(missing.toTypedArray()) }
            .onFailure { finish() }
    }
}

@Composable
fun rememberPermissionGate(): PermissionGate {
    val context = LocalContext.current
    lateinit var gate: PermissionGate
    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { gate.onForegroundResult() }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { gate.onBackgroundResult() }
    gate = remember(context, foregroundLauncher, backgroundLauncher) {
        PermissionGate(context, foregroundLauncher, backgroundLauncher)
    }
    return gate
}
