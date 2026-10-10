package com.app.wasalna.wear

/**
 * Phase 10.10: phone ↔ watch Data Layer contract — must stay identical to
 * the phone-side `com.app.wasalna.data.wear.WatchAlertContract`.
 */
object WatchAlertContract {
    const val CAPABILITY = "wasalna_watch_alert"

    const val PATH_ALERT = "/wasalna/alert"
    const val PATH_TEST = "/wasalna/test"
    const val PATH_DISMISS = "/wasalna/dismiss"
    const val PATH_DISMISSED = "/wasalna/dismissed"

    const val ACTION_DISMISS_LOCAL = "com.app.wasalna.wear.DISMISS_ALARM"

    const val MAX_NAME_BYTES = 256
}
