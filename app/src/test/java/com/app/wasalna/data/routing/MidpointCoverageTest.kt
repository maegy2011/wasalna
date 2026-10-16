package com.app.wasalna.data.routing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Offline plan R2: midpoint guard in [OfflineRouteSelector].
 *
 * Note on geometry: bboxes are convex, so a segment between two interior
 * points can never leave the box — the check is a cheap safety net and
 * the engine query stays the final arbiter for true road gaps. These
 * tests pin the contract: interior legs pass, excursions fail.
 */
class MidpointCoverageTest {

    private val region = RoutingRegion(
        id = "test-region",
        name = "Test",
        url = "https://example.invalid/test.ghz",
        sizeMb = 10,
        minLat = 30.00,
        maxLat = 31.00,
        minLng = 31.00,
        maxLng = 32.00,
    )

    @Test
    fun midpoints_interiorSegment_passes() {
        assertThat(
            regionCoversMidpoints(region, 30.20, 31.20, 30.80, 31.80),
        ).isTrue()
    }

    @Test
    fun midpoints_segmentLeavingBox_fails() {
        // Destination far outside: 25/50/75% samples leave the bbox.
        assertThat(
            regionCoversMidpoints(region, 30.50, 31.50, 35.00, 35.00),
        ).isFalse()
    }

    @Test
    fun via_allLegsInside_passes() {
        val pts = listOf(
            30.10 to 31.10,
            30.50 to 31.50,
            30.90 to 31.90,
        )
        assertThat(regionCoversAllMidpoints(region, pts)).isTrue()
    }

    @Test
    fun via_middleStopOutside_fails() {
        val pts = listOf(
            30.10 to 31.10,
            35.00 to 35.00, // excursion: both adjacent legs leave the box
            30.90 to 31.90,
        )
        assertThat(regionCoversAllMidpoints(region, pts)).isFalse()
    }

    @Test
    fun via_tooShort_rejected() {
        assertThat(regionCoversAllMidpoints(region, emptyList())).isFalse()
        assertThat(
            regionCoversAllMidpoints(region, listOf(30.10 to 31.10)),
        ).isFalse()
    }
}
