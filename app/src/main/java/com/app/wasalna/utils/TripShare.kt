package com.app.wasalna.utils

import android.content.Context
import android.content.Intent
import com.app.wasalna.R
import com.app.wasalna.domain.model.TripHistory

/**
 * Phase 10.8.0c: live trip sharing — no server.
 * Shareable text with destination + ETA + OSM position link.
 * Status refreshes via TrackingService; recipients re-open for updates
 * (periodic-update note included in the text).
 */
object TripShare {

    fun buildText(context: Context, trip: TripHistory): String = buildString {
        appendLine(context.getString(R.string.trip_share_text_header))
        appendLine(context.getString(R.string.trip_share_text_heading, trip.locationName))
        trip.etaMinutes?.let { appendLine(context.getString(R.string.trip_share_text_eta, it)) }
            ?: appendLine(context.getString(R.string.trip_share_text_live))
        appendLine(
            context.getString(R.string.trip_share_text_dest) + " https://www.openstreetmap.org/" +
                "?mlat=${trip.destLat}&mlon=${trip.destLng}#map=16/${trip.destLat}/${trip.destLng}",
        )
        appendLine()
        appendLine(context.getString(R.string.trip_share_text_note))
    }

    fun share(context: Context, trip: TripHistory) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildText(context, trip))
            putExtra(Intent.EXTRA_SUBJECT, "Live trip: ${trip.locationName}")
        }
        context.startActivity(Intent.createChooser(intent, "Share live trip"))
    }
}
