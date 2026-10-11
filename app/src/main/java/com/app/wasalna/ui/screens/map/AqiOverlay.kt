package com.app.wasalna.ui.screens.map

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

internal fun ensureAqiLayer(style: Style?, on: Boolean, token: String) {
    if (style == null) return
    val usable = on && token.isNotBlank()
    val present = runCatching { style.getLayer(AQI_LAYER_ID) }.getOrNull() != null
    // Token removed while on → drop the layer (stale tiles must not linger).
    if (!usable && present) {
        runCatching { style.removeLayer(AQI_LAYER_ID) }
        runCatching {
            style.getSource(AQI_SOURCE_ID)?.let { style.removeSource(it) }
        }
        return
    }
    if (usable && !present) {
        runCatching {
            if (style.getSource(AQI_SOURCE_ID) == null) {
                style.addSource(
                    RasterSource(
                        AQI_SOURCE_ID,
                        TileSet("2.2.0", aqiTileTemplate(token)),
                        256,
                    ),
                )
            }
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
}
