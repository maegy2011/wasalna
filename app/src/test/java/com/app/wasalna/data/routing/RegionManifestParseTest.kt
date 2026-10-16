package com.app.wasalna.data.routing

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

/**
 * Offline plan R1/R5/R6: server manifest parsing.
 * Old manifests omit sha256/version — both must default to null
 * (checks skipped, never a false badge). Pure JVM.
 */
class RegionManifestParseTest {

    private val gson = Gson()

    @Test
    fun parsesFullEntry() {
        val json = """
            {
              "version": 3,
              "gazetteerBaseUrl": "https://example.invalid/gazetteer",
              "regions": [{
                "id": "cairo-giza",
                "name": "Cairo + Giza",
                "url": "https://example.invalid/cairo-giza.ghz",
                "sizeMb": 42,
                "minLat": 29.6, "maxLat": 30.85,
                "minLng": 30.8, "maxLng": 31.8,
                "sha256": "abc123",
                "version": 7
              }]
            }
        """.trimIndent()
        val manifest = gson.fromJson(json, RegionManifest::class.java)
        assertThat(manifest.version).isEqualTo(3)
        assertThat(manifest.regions).hasSize(1)
        val region = manifest.regions[0]
        assertThat(region.sha256).isEqualTo("abc123")
        assertThat(region.version).isEqualTo(7)
        assertThat(manifest.regionById("cairo-giza")).isEqualTo(region)
        assertThat(manifest.regionById("missing")).isNull()
    }

    @Test
    fun legacyManifestWithoutChecksumOrVersion() {
        val json = """
            {
              "version": 1,
              "gazetteerBaseUrl": "https://example.invalid/gazetteer",
              "regions": [{
                "id": "cairo-giza",
                "name": "Cairo + Giza",
                "url": "https://example.invalid/cairo-giza.ghz",
                "sizeMb": 42,
                "minLat": 29.6, "maxLat": 30.85,
                "minLng": 30.8, "maxLng": 31.8
              }]
            }
        """.trimIndent()
        val manifest = gson.fromJson(json, RegionManifest::class.java)
        val region = manifest.regions[0]
        assertThat(region.sha256).isNull()
        assertThat(region.version).isNull()
        // Geometry still works — legacy graphs route unchanged.
        assertThat(region.contains(30.04, 31.23)).isTrue()
    }
}
