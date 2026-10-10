package com.app.wasalna.data.wear

/**
 * Phase 10.10: phone ↔ watch Data Layer contract (shared verbatim with
 * the `:wear` module — keep both copies in sync).
 *
 * - [CAPABILITY] is advertised by both sides via the
 *   `android_wear_capabilities` string-array so CapabilityClient node
 *   discovery finds the companion app.
 * - Message paths carry tiny UTF-8 payloads (destination name) or empty
 *   payloads (test / dismiss signals).
 */
object WatchAlertContract {
    const val CAPABILITY = "wasalna_watch_alert"

    const val PATH_ALERT = "/wasalna/alert"
    const val PATH_TEST = "/wasalna/test"
    const val PATH_DISMISS = "/wasalna/dismiss"
    const val PATH_DISMISSED = "/wasalna/dismissed"

    const val EXTRA_DESTINATION_NAME = "extra_destination_name"

    const val MAX_NAME_BYTES = 256
}
