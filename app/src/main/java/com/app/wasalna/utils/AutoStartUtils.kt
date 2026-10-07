package com.app.wasalna.utils

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Phase 10.4.3.6: strict-OEM autostart/background guidance.
 *
 * Xiaomi / Huawei / Honor / Oppo / Realme / OnePlus / Vivo kill background
 * apps even with `isIgnoringBatteryOptimizations` — the user must enable
 * AutoStart + "no restrictions" in the vendor's own settings app, whose
 * package/activity differs per OEM (and changes between vendor updates, so
 * every launch is wrapped in try-catch and we try several fallbacks).
 */
object AutoStartUtils {

    private const val TAG = "AutoStart"

    fun manufacturer(): String = Build.MANUFACTURER.lowercase()

    fun displayName(): String =
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** True for OEMs known to kill background work aggressively. */
    fun isStrictDevice(): Boolean = manufacturer() in STRICT_MANUFACTURERS

    /**
     * Ordered candidate intents for this device: vendor AutoStart screens
     * first, generic battery-optimization request as the last resort.
     */
    fun autoStartIntents(context: Context): List<Intent> {
        val vendor = when (manufacturer()) {
            "xiaomi", "redmi", "poco", "blackshark" -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
            )
            "huawei", "honor" -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )
            "oppo", "realme" -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.FakeActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                component("com.realme.securitycheck", "com.realme.securitycheck.permission.startup.StartupAppListActivity"),
            )
            "oneplus" -> listOf(
                component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            )
            "vivo", "iqoo" -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"),
            )
            "samsung" -> listOf(
                component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )
            "asus" -> listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
            )
            "nokia", "hmd global" -> listOf(
                component("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"),
            )
            else -> emptyList()
        }
        return vendor + Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
    }

    /**
     * Fires the first working intent. Returns true if *any* screen opened.
     * Never throws — a renamed vendor package must not crash the app.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        for (intent in autoStartIntents(context)) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.i(TAG, "not found: ${intent.component ?: intent.action}")
            } catch (e: Exception) {
                Log.w(TAG, "autostart intent failed: ${e.message}")
            }
        }
        return false
    }

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private val STRICT_MANUFACTURERS = setOf(
        "xiaomi", "redmi", "poco", "blackshark",
        "huawei", "honor",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
    )
}
