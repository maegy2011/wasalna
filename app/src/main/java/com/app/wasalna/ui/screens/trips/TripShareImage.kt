package com.app.wasalna.ui.screens.trips

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import com.app.wasalna.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "TripShareImage"

/**
 * Renders the trip summary card (framework Canvas) and shares it as a PNG. Returns true on success.
 * Compose measure/layout runs on Main; file I/O on Dispatchers.IO.
 * Never throws — false + log on any failure.
 */
suspend fun captureAndShareTripImage(
    context: Context,
    destination: String,
    distanceKm: String,
    durationMin: String,
    dateText: String,
): Boolean {
    return runCatching {
        // Oct 2026 fix: framework Canvas render — the old detached
        // ComposeView never composed, producing a 1×1/blank image.
        val bitmap = withContext(Dispatchers.Default) {
            TripCardRenderer.render(
                context = context,
                destination = destination,
                distanceKm = distanceKm,
                durationMin = durationMin,
                dateText = dateText,
                appName = context.getString(R.string.trip_card_app_name),
            ) ?: return@withContext null
        } ?: return false
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "trip-summary-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    }.onFailure {
        Log.w(TAG, "share-as-image failed: ${it.message}")
    }.getOrDefault(false)
}

/** "10 Oct 2026" style date for the card, device locale. */
fun tripCardDate(now: Long = System.currentTimeMillis()): String {
    return runCatching {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(now))
    }.getOrDefault("")
}

/** Straight-line km between two points (haversine) — pure math, no I/O. */
fun haversineKm(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double,
): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 2 * r * Math.asin(Math.sqrt(a))
}
