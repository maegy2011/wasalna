package com.app.wasalna.ui.screens.trips

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.app.wasalna.R
import com.app.wasalna.ui.theme.WasalnaTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "TripShareImage"

/**
 * REDESIGN 10 Oct 2026: share-as-image — renders a trip summary card
 * (destination, distance, time, date, app name) off-screen via ComposeView
 * capture, writes the PNG to cache (FileProvider `shared` cache-path, see
 * res/xml/file_paths.xml), and opens the system share sheet.
 *
 * Rules honored: null-safety (no `!!`), Dispatchers.IO for file I/O,
 * Material3, 8dp grid, RTL automatic via Compose, strings from resources
 * (no hardcoded literals except the Bitmap plumbing).
 */
@Composable
fun TripSummaryCard(
    destination: String,
    distanceKm: String,
    durationMin: String,
    dateText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(640.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = destination,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = distanceKm,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = durationMin,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${stringResource(R.string.trip_card_date_label)}: $dateText",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.trip_card_app_name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders [card] off-screen and shares it as a PNG. Returns true on success.
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
        val bitmap = withContext(Dispatchers.Main) {
            val composeView = ComposeView(context).apply {
                setContent {
                    WasalnaTheme {
                        TripSummaryCard(
                            destination = destination,
                            distanceKm = distanceKm,
                            durationMin = durationMin,
                            dateText = dateText,
                        )
                    }
                }
            }
            val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)
            val w = composeView.measuredWidth.coerceAtLeast(1)
            val h = composeView.measuredHeight.coerceAtLeast(1)
            composeView.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            composeView.draw(Canvas(bmp))
            bmp
        }
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
