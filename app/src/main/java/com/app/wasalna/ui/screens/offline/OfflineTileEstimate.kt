package com.app.wasalna.ui.screens.offline

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.tan

/**
 * Vela-inspired pre-download size estimate (no Vela code used — clean-room
 * math on the standard slippy-map tile scheme).
 *
 * Vela shows the size on the card BEFORE anything downloads
 * (tile count × region tile density, else AREA_TILE_KB = 110) and refuses
 * areas over AREA_MAX_TILES (60,000). We do the same: count the tiles our
 * zoom range would pull for the bounds, multiply by 110 KB/tile, and block
 * anything over [TILE_CAP] with a "zoom in or pick a preset" message.
 *
 * Pure Kotlin, no Android dependencies — unit-testable.
 */
object OfflineTileEstimate {

    /** Conservative average vector-tile size (Vela AREA_TILE_KB). */
    const val KB_PER_TILE = 110L

    /** Refuses packs larger than this (Vela TILE_LIMIT = 50,000). */
    const val TILE_CAP = 50_000L

    /** Counts slippy-map tiles intersecting the bbox over [minZoom]..[maxZoom]. */
    fun countTiles(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        minZoom: Int,
        maxZoom: Int,
    ): Long {
        if (north <= south || east <= west) return 0L
        var total = 0L
        for (z in minZoom..maxZoom) {
            val n = 1 shl z
            val xMin = lonToX(west, n).coerceIn(0, n - 1)
            val xMax = lonToX(east, n).coerceIn(0, n - 1)
            // Note: y grows southward, so north bound gives the smaller index.
            val yMin = latToY(north, n).coerceIn(0, n - 1)
            val yMax = latToY(south, n).coerceIn(0, n - 1)
            if (xMax >= xMin && yMax >= yMin) {
                total += (xMax - xMin + 1L) * (yMax - yMin + 1L)
            }
        }
        return total
    }

    /** Estimated download bytes for a tile count. */
    fun bytesFor(tiles: Long): Long = tiles * KB_PER_TILE * 1024L

    private fun lonToX(lon: Double, n: Int): Int =
        floor((lon + 180.0) / 360.0 * n).toInt()

    private fun latToY(lat: Double, n: Int): Int {
        val clamped = lat.coerceIn(-85.0511, 85.0511)
        val rad = clamped * PI / 180.0
        val y = (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0 * n
        return floor(y).toInt()
    }
}
