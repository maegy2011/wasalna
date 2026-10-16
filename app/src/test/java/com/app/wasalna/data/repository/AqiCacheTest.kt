package com.app.wasalna.data.repository

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Offline plan A1: 6 h AQI value cache (file-backed, ~5 km cells).
 * Robolectric provides Context/cacheDir; no network is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AqiCacheTest {

    private lateinit var repo: AqiRepository
    private lateinit var cacheFile: File

    // Tahrir Square — cell key is (lat*20):(lng*20).
    private val lat = 30.0444
    private val lng = 31.2357
    private val cellKey = "${(lat * 20).toInt()}:${(lng * 20).toInt()}"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repo = AqiRepository(context)
        cacheFile = File(context.cacheDir, "aqi_cache.json")
        cacheFile.delete()
    }

    private fun writeEntry(aqi: Int, atMs: Long) {
        cacheFile.writeText(
            JSONObject()
                .put(cellKey, JSONObject().put("aqi", aqi).put("at", atMs))
                .toString(),
        )
    }

    @Test
    fun missWhenEmpty() {
        assertThat(repo.cachedAqi(lat, lng)).isNull()
        assertThat(repo.hasCachedAqi(lat, lng)).isFalse()
    }

    @Test
    fun hitWhenFresh() {
        writeEntry(aqi = 68, atMs = System.currentTimeMillis())
        assertThat(repo.cachedAqi(lat, lng)).isEqualTo(68)
        assertThat(repo.hasCachedAqi(lat, lng)).isTrue()
    }

    @Test
    fun staleValueWithheldButPresenceKnown() {
        writeEntry(aqi = 68, atMs = System.currentTimeMillis() - AqiRepository.CACHE_TTL_MS - 1)
        // Too old to display…
        assertThat(repo.cachedAqi(lat, lng)).isNull()
        // …but the UI can still distinguish "stale" from "never fetched".
        assertThat(repo.hasCachedAqi(lat, lng)).isTrue()
    }

    @Test
    fun neighboringCellsAreIndependent() {
        writeEntry(aqi = 68, atMs = System.currentTimeMillis())
        // ~50 km away: different cell, no leak.
        assertThat(repo.cachedAqi(lat + 0.5, lng + 0.5)).isNull()
    }
}
