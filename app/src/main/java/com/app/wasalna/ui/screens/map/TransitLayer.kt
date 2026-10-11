package com.app.wasalna.ui.screens.map

import com.app.wasalna.domain.model.CairoTransit
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Public-transit vector layer (Oct 2026): Cairo metro lines + stations
 * from the EMBEDDED roster (TransitData — same source as the planner).
 *
 * - Lines: one LineString per metro line through its ORDERED coord
 *   stations (schematic connectors between real stations — labeled as
 *   such nowhere on the map, but the geometry between adjacent real
 *   stations follows the street grid closely enough for a layer).
 * - Stations: white circles with a dark ring (visible on any basemap).
 * - Colors: official Cairo metro colors (L1 red, L2 orange, L3 green).
 * - OFFLINE-SAFE: embedded data only, zero network — renders offline.
 * - Monorail/LRT have no per-station coords (approximate guidance only)
 *   and are excluded — never a faked stop.
 *
 * Idempotent; all style calls guarded — never throws, no `!!`.
 */
internal const val TRANSIT_SOURCE_ID = "wasalna-transit-src"
internal const val TRANSIT_LINE_LAYER_ID = "wasalna-transit-lines"
internal const val TRANSIT_STATION_LAYER_ID = "wasalna-transit-stations"

private const val L1_COLOR = "#E53935"
private const val L2_COLOR = "#FB8C00"
private const val L3_COLOR = "#43A047"

private fun lineColor(lineId: String): String = when {
    lineId.startsWith("M1") -> L1_COLOR
    lineId.startsWith("M2") -> L2_COLOR
    else -> L3_COLOR
}

private fun buildTransitGeoJson(): String {
    val features = StringBuilder()
    // Line connectors (ordered coord stations only).
    for (line in CairoTransit.METRO_ROUTES) {
        val pts = line.stations.filter { it.lat != null && it.lng != null }
        if (pts.size < 2) continue
        val coords = pts.joinToString(",") { "[${it.lng},${it.lat}]" }
        features.append(
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\"," +
                "\"coordinates\":[$coords]},\"properties\":{\"color\":" +
                "\"${lineColor(line.id)}\"}},",
        )
    }
    // Station dots (distinct across lines sharing interchanges).
    val seen = mutableSetOf<String>()
    for (line in CairoTransit.METRO_ROUTES) {
        for (st in line.stations) {
            val la = st.lat ?: continue
            val ln = st.lng ?: continue
            if (!seen.add(st.id)) continue
            val name = st.nameAr.ifBlank { st.nameEn }
                .replace("\"", "")
            features.append(
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\"," +
                    "\"coordinates\":[${ln},${la}]},\"properties\":" +
                    "{\"name\":\"$name\"}},",
            )
        }
    }
    val body = if (features.isEmpty()) "" else features.dropLast(1).toString()
    return "{\"type\":\"FeatureCollection\",\"features\":[$body]}"
}

internal fun ensureTransitLayer(style: Style?, on: Boolean) {
    if (style == null) return
    val present = runCatching { style.getLayer(TRANSIT_LINE_LAYER_ID) }.getOrNull() != null
    if (on && !present) {
        runCatching {
            if (style.getSource(TRANSIT_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(TRANSIT_SOURCE_ID, buildTransitGeoJson()))
            }
            // Lines below stations; both below symbols, above the route.
            val lines = LineLayer(TRANSIT_LINE_LAYER_ID, TRANSIT_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(
                    org.maplibre.android.style.expressions.Expression.get("color"),
                ),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.85f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            // Filter to LineString features only (stations are Points).
            lines.setFilter(
                org.maplibre.android.style.expressions.Expression.eq(
                    org.maplibre.android.style.expressions.Expression.geometryType(),
                    "LineString",
                ),
            )
            val dots = CircleLayer(TRANSIT_STATION_LAYER_ID, TRANSIT_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#424242"),
            )
            dots.setFilter(
                org.maplibre.android.style.expressions.Expression.eq(
                    org.maplibre.android.style.expressions.Expression.geometryType(),
                    "Point",
                ),
            )
            val firstSymbol = style.layers?.firstOrNull {
                it is org.maplibre.android.style.layers.SymbolLayer
            }?.id
            if (firstSymbol != null) {
                style.addLayerBelow(lines, firstSymbol)
                style.addLayerBelow(dots, firstSymbol)
            } else {
                style.addLayer(lines)
                style.addLayer(dots)
            }
        }
    } else if (!on && present) {
        runCatching { style.removeLayer(TRANSIT_STATION_LAYER_ID) }
        runCatching { style.removeLayer(TRANSIT_LINE_LAYER_ID) }
        runCatching {
            style.getSource(TRANSIT_SOURCE_ID)?.let { style.removeSource(it) }
        }
    }
}
