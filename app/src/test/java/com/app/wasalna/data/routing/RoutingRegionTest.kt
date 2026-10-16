package com.app.wasalna.data.routing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Offline plan §6: pure-JVM bbox geometry for downloadable graphs.
 * No Android, no network — runs on every build.
 */
class RoutingRegionTest {

    private val cairo = RoutingRegion(
        id = "cairo-giza",
        name = "Cairo + Giza",
        url = "https://example.invalid/cairo-giza.ghz",
        sizeMb = 42,
        minLat = 29.60,
        maxLat = 30.85,
        minLng = 30.80,
        maxLng = 31.80,
    )

    @Test
    fun contains_insideAndBorders() {
        assertThat(cairo.contains(30.04, 31.23)).isTrue()
        // Borders are inclusive.
        assertThat(cairo.contains(29.60, 30.80)).isTrue()
        assertThat(cairo.contains(30.85, 31.80)).isTrue()
    }

    @Test
    fun contains_outside() {
        assertThat(cairo.contains(31.20, 29.90)).isFalse() // Alexandria
        assertThat(cairo.contains(30.04, 35.00)).isFalse() // east of bbox
        assertThat(cairo.contains(25.00, 31.00)).isFalse() // south
    }

    @Test
    fun coversRoute_needsBothEndpoints() {
        // Tahrir → Giza pyramids: both inside.
        assertThat(cairo.coversRoute(30.04, 31.23, 29.97, 31.13)).isTrue()
        // Cairo → Alexandria: destination outside.
        assertThat(cairo.coversRoute(30.04, 31.23, 31.20, 29.90)).isFalse()
        // Both outside.
        assertThat(cairo.coversRoute(31.20, 29.90, 31.25, 29.95)).isFalse()
    }

    @Test
    fun intersects_tileBboxOverlap() {
        // Overlapping download bbox.
        assertThat(cairo.intersects(29.90, 31.00, 30.20, 31.60)).isTrue()
        // Fully disjoint (Europe).
        assertThat(cairo.intersects(48.00, 2.00, 49.00, 3.00)).isFalse()
        // Edge-touching counts as overlap (inclusive bounds).
        assertThat(cairo.intersects(30.85, 31.80, 31.20, 32.00)).isTrue()
    }
}
