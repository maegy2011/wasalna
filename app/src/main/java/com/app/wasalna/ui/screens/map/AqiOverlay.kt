package com.app.wasalna.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.wasalna.R
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Air-quality raster layer (Oct 2026): WAQI US-EPA AQI tiles (BYOK —
 * the user pastes their free token in Settings; empty = unavailable).
 *
 * Same mechanics as the traffic overlay (RasterSource + RasterLayer at
 * 0.55 opacity, below symbols). ONLINE-ONLY: never rendered in offline
 * mode (the caller gates on isOfflineMode like traffic).
 *
 * Idempotent; all style calls guarded — never throws, no `!!`.
 */
internal const val AQI_SOURCE_ID = "wasalna-aqi-src"
internal const val AQI_LAYER_ID = "wasalna-aqi"

/** WAQI tile template — {z}/{x}/{y} + user token (never embedded). */
fun aqiTileTemplate(token: String): String =
    "https://tiles.aqicn.org/tiles/usepa-aqi/{z}/{x}/{y}.png?token=$token"

/**
 * US-EPA AQI bands (AQI layer upgrade, user order Oct 2026): drives the
 * legend + the destination chip color. Thresholds are the official
 * US-EPA breakpoints.
 */
data class AqiLevel(
    val max: Int,
    val color: Color,
    val labelRes: Int,
    val range: String,
)

private val AQI_LEVELS = listOf(
    AqiLevel(50, Color(0xFF00E400), R.string.aqi_good, "0–50"),
    AqiLevel(100, Color(0xFFFFFF00), R.string.aqi_moderate, "51–100"),
    AqiLevel(150, Color(0xFFFF7E00), R.string.aqi_usg, "101–150"),
    AqiLevel(200, Color(0xFFFF0000), R.string.aqi_unhealthy, "151–200"),
    AqiLevel(300, Color(0xFF8F3F97), R.string.aqi_very_unhealthy, "201–300"),
    AqiLevel(Int.MAX_VALUE, Color(0xFF7E0023), R.string.aqi_hazardous, "300+"),
)

fun aqiLevelFor(aqi: Int): AqiLevel =
    AQI_LEVELS.first { aqi <= it.max }

/** Compact legend card — shown in the layers sheet under the AQI row. */
@Composable
fun AqiLegendCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.aqi_legend_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AQI_LEVELS.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(level.color),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(level.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = level.range,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // A4: WAQI license attribution (required by aqicn.org terms).
            Text(
                text = stringResource(R.string.aqi_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Destination AQI dot (user order Oct 2026 — no text on the map):
 * the band color only. The value + band label live in the layers
 * sheet legend ([AqiLegendCard]), not over the map.
 */
@Composable
fun AqiDot(aqi: Int, modifier: Modifier = Modifier) {
    val level = aqiLevelFor(aqi)
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(level.color),
    )
}

internal fun ensureAqiLayer(style: Style?, on: Boolean, token: String) {
    if (style == null) return
    val usable = on && token.isNotBlank()
    val present = runCatching { style.getLayer(AQI_LAYER_ID) }.getOrNull() != null
    // Stale-token fix (user order Oct 2026): the source template embeds
    // the token, so ANY usable call rebuilds layer + source from scratch.
    // Otherwise a pasted/rotated key never takes effect and the layer
    // keeps fetching with the dead token (invisible layer). Calls only
    // happen on toggle/token/style change — never per frame — so the
    // rebuild is cheap. Off state still just drops everything.
    if (!usable) {
        if (!present) return
        runCatching { style.removeLayer(AQI_LAYER_ID) }
        runCatching {
            style.getSource(AQI_SOURCE_ID)?.let { style.removeSource(it) }
        }
        return
    }
    runCatching {
        runCatching { style.removeLayer(AQI_LAYER_ID) }
        runCatching {
            style.getSource(AQI_SOURCE_ID)?.let { style.removeSource(it) }
        }
        style.addSource(
            RasterSource(
                AQI_SOURCE_ID,
                TileSet("2.2.0", aqiTileTemplate(token)),
                256,
            ),
        )
        val layer = RasterLayer(AQI_LAYER_ID, AQI_SOURCE_ID).withProperties(
            PropertyFactory.rasterOpacity(0.55f),
            PropertyFactory.rasterFadeDuration(900f),
        )
        val firstSymbol = style.layers?.firstOrNull {
            it is org.maplibre.android.style.layers.SymbolLayer
        }?.id
        if (firstSymbol != null) style.addLayerBelow(layer, firstSymbol)
        else style.addLayer(layer)
    }
}
