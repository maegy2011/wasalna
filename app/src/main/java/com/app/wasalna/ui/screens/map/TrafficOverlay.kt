package com.app.wasalna.ui.screens.map

import com.app.wasalna.data.traffic.TrafficRepository
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Live-traffic raster overlay, ported from Vela
 * (app.vela.ui.map.VelaMapView#ensureTraffic + TRAFFIC_TILES, studied 9 Oct 2026).
 *
 * Vela mechanism: Google's public keyless `/maps/vt` raster tile carrying the
 * `traffic` layer + incidents sublayers, added as a MapLibre RasterSource and
 * drawn as a RasterLayer BELOW labels/symbols but ABOVE buildings (so 3D
 * footprints don't bury congestion colors), at 0.6 opacity with a 900ms fade
 * against tile-swap flicker. Cadence is engine-native: MapLibre refetches
 * visible XYZ tiles on camera move + expiry; the viewport IS the request.
 *
 * Privacy: tile x/y/z reveals the viewed region to Google — toggle stays
 * default OFF with an Arabic disclosure next to it (MapScreen + Settings).
 */
internal const val TRAFFIC_SOURCE_ID = "wasalna-traffic-src"
internal const val TRAFFIC_LAYER_ID = "wasalna-traffic"

/** Tile template lives in TrafficRepository (single source of truth). */
private val TRAFFIC_TILE_TEMPLATE: String
    get() = TrafficRepository.TILE_TEMPLATE

/**
 * Add or remove the traffic raster. Idempotent; all style calls guarded with
 * runCatching so a torn-down style can never crash the map. No !! anywhere.
 */
internal fun ensureTraffic(style: Style?, on: Boolean) {
    if (style == null) return
    val present = runCatching { style.getLayer(TRAFFIC_LAYER_ID) }.getOrNull() != null
    if (on && !present) {
        runCatching {
            if (style.getSource(TRAFFIC_SOURCE_ID) == null) {
                style.addSource(
                    RasterSource(TRAFFIC_SOURCE_ID, TileSet("2.2.0", TRAFFIC_TILE_TEMPLATE), 256),
                )
            }
            val layer = RasterLayer(TRAFFIC_LAYER_ID, TRAFFIC_SOURCE_ID).withProperties(
                PropertyFactory.rasterOpacity(0.6f),
                PropertyFactory.rasterFadeDuration(900f),
            )
            val buildingTop = style.getLayer("building-3d") ?: style.getLayer("building")
            val firstSymbol = style.layers?.firstOrNull {
                it is org.maplibre.android.style.layers.SymbolLayer
            }?.id
            when {
                buildingTop != null -> style.addLayerAbove(layer, buildingTop.id)
                firstSymbol != null -> style.addLayerBelow(layer, firstSymbol)
                else -> style.addLayer(layer)
            }
        }
    } else if (!on && present) {
        runCatching { style.removeLayer(TRAFFIC_LAYER_ID) }
        runCatching {
            style.getSource(TRAFFIC_SOURCE_ID)?.let { style.removeSource(it) }
        }
    }
}
