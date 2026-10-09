package com.app.wasalna.utils

import android.content.Context
import android.content.Intent
import com.app.wasalna.domain.model.TripHistory

/**
 * Phase 10.8.0c: live trip sharing — no server.
 * Shareable text with destination + ETA + OSM position link.
 * Status refreshes via TrackingService; recipients re-open for updates
 * (periodic-update note included in the text).
 */
object TripShare {

    fun buildText(trip: TripHistory): String = buildString {
        appendLine("🚕 Live trip via Wasalna")
        appendLine("Heading to: ${trip.locationName}")
        trip.etaMinutes?.let { appendLine("ETA: ~$it mins") } ?: appendLine("Tracking live…")
        appendLine(
            "Destination: https://www.openstreetmap.org/" +
                "?mlat=${trip.destLat}&mlon=${trip.destLng}#map=16/${trip.destLat}/${trip.destLng}",
        )
        appendLine()
        appendLine("Live status updates as the trip progresses — ask the sender for a fresh share.")
    }

    fun share(context: Context, trip: TripHistory) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildText(trip))
            putExtra(Intent.EXTRA_SUBJECT, "Live trip: ${trip.locationName}")
        }
        context.startActivity(Intent.createChooser(intent, "Share live trip"))
    }
}
