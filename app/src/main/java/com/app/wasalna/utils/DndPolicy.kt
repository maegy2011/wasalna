package com.app.wasalna.utils

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Silent-mode (DND) bypass choice model — single source of truth for the
 * persisted values. Default everywhere is [RESPECT] (safe: existing users
 * see zero behaviour change until they explicitly opt in).
 *
 * - [RESPECT]: alarm obeys the system's Do-Not-Disturb alarm policy.
 * - [BYPASS]: alarm tries to ring through DND (needs policy access).
 * - [ASK]: the user is asked once per trip (notification actions at trip
 *   start); an unanswered trip falls back to [RESPECT].
 */
object DndChoice {
    const val RESPECT = "respect"
    const val BYPASS = "bypass"
    const val ASK = "ask"

    fun normalize(raw: String?): String = when (raw) {
        BYPASS -> BYPASS
        ASK -> ASK
        else -> RESPECT
    }
}

/**
 * Notification-policy access helpers. The manifest MUST declare
 * ACCESS_NOTIFICATION_POLICY — otherwise the app never appears in the
 * system "Do Not Disturb access" list and the grant cannot be given.
 * The user still approves it in system Settings, and every bypass path
 * re-checks the grant at fire time so a denial/revocation always
 * degrades gracefully to respect-DND. Never throws.
 */
object DndPolicy {
    fun isPolicyAccessGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return false
        return runCatching { manager.isNotificationPolicyAccessGranted }
            .getOrDefault(false)
    }

    /** Opens the DND-access screen scrolled straight to this app. */
    fun policyAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
}
