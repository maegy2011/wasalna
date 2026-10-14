package com.app.wasalna.ui.screens.map

import android.util.Log
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Temperature raster layer (Oct 2026): OpenWeatherMap `temp_new` tiles
 * (BYOK — the user pastes their free key in Settings; empty = unavailable).
 *
 * Same mechanics as the AQI overlay (RasterSource + RasterLayer below
 * symbols, ONLINE-ONLY, rebuilt from scratch on every usable call so a
 * pasted/rotated key takes effect immediately — never a stale template).
 * Opacity is user-adjustable (layers sheet slider) and applied live.
 *
 * Idempotent; all style calls guarded — never throws, no `!!`.
 */
internal const val TEMP_SOURCE_ID = "wasalna-temp-src"
internal const val TEMP_LAYER_ID = "wasalna-temp"
private const val TAG = "TemperatureOverlay"

const val TEMP_OPACITY_MIN = 0.15f
const val TEMP_OPACITY_MAX = 1f
const val TEMP_OPACITY_DEFAULT = 0.6f

/** OWM temperature tile template — {z}/{x}/{y} + user key (never embedded). */
fun owmTempTemplate(key: String): String =
    "https://tile.openweathermap.org/map/temp_new/{z}/{x}/{y}.png?appid=$key"

internal fun ensureTemperatureLayer(
    style: Style?,
    on: Boolean,
    key: String,
    opacity: Float,
) {
    if (style == null) return
    val usable = on && key.isNotBlank()
    val present = runCatching { style.getLayer(TEMP_LAYER_ID) }.getOrNull() != null
    if (!usable) {
        if (!present) return
        runCatching { style.removeLayer(TEMP_LAYER_ID) }
        runCatching {
            style.getSource(TEMP_SOURCE_ID)?.let { style.removeSource(it) }
        }
        return
    }
    runCatching {
        runCatching { style.removeLayer(TEMP_LAYER_ID) }
        runCatching {
            style.getSource(TEMP_SOURCE_ID)?.let { style.removeSource(it) }
        }
        style.addSource(
            RasterSource(
                TEMP_SOURCE_ID,
                TileSet("2.2.0", owmTempTemplate(key)),
                256,
            ),
        )
        val layer = RasterLayer(TEMP_LAYER_ID, TEMP_SOURCE_ID).withProperties(
            PropertyFactory.rasterOpacity(opacity.coerceIn(TEMP_OPACITY_MIN, TEMP_OPACITY_MAX)),
            PropertyFactory.rasterFadeDuration(900f),
        )
        val firstSymbol = style.layers?.firstOrNull {
            it is org.maplibre.android.style.layers.SymbolLayer
        }?.id
        if (firstSymbol != null) style.addLayerBelow(layer, firstSymbol)
        else style.addLayer(layer)
    }.onFailure {
        Log.w(TAG, "temperature layer failed: ${it.message}")
    }
}

/** Live opacity update — no source rebuild, survives style reloads via state. */
internal fun setTemperatureOpacity(style: Style?, opacity: Float) {
    if (style == null) return
    runCatching {
        style.getLayer(TEMP_LAYER_ID)?.setProperties(
            PropertyFactory.rasterOpacity(opacity.coerceIn(TEMP_OPACITY_MIN, TEMP_OPACITY_MAX)),
        )
    }.onFailure {
        Log.w(TAG, "temperature opacity failed: ${it.message}")
    }
}
