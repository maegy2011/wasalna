package com.app.wasalna.data.search

import androidx.test.core.app.ApplicationProvider
import com.app.wasalna.data.routing.RegionManifestRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Offline plan §6 + P4: Arabic search normalization.
 * Robolectric provides the Context; matching itself is pure string work.
 *
 * NOTE: lam-alef ligatures below use backslash-u escapes deliberately —
 * presentation-form glyphs are visually unverifiable in source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GazetteerNormalizeTest {

    private lateinit var gazetteer: OfflineGazetteer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        gazetteer = OfflineGazetteer(context, RegionManifestRepository(context))
    }

    @Test
    fun stripsTashkeelAndTatweel() {
        assertThat(gazetteer.normalize("شَارِع التَّحْرِير")).isEqualTo("شارع التحرير")
    }

    @Test
    fun unifiesAlefVariants() {
        assertThat(gazetteer.normalize("أحمد")).isEqualTo(gazetteer.normalize("احمد"))
        assertThat(gazetteer.normalize("آثار")).isEqualTo(gazetteer.normalize("اثار"))
    }

    @Test
    fun unifiesTaaMarbutaAndAlefMaqsura() {
        // ة → ه and ى → ي on both sides, so spellings meet.
        assertThat(gazetteer.normalize("مدرسة")).isEqualTo(gazetteer.normalize("مدرسه"))
        assertThat(gazetteer.normalize("مستشفى")).isEqualTo(gazetteer.normalize("مستشفي"))
    }

    /** Presentation-form ligature built from its code point — no pasted glyphs. */
    private fun ligature(code: Int) = code.toChar().toString()

    @Test
    fun decomposesLamAlefLigatures() {
        // U+FEFB/FEFC (bare lam-alef) → typed lam + alef, then stable.
        assertThat(gazetteer.normalize(ligature(0xFEFB))).isEqualTo("لا")
        assertThat(gazetteer.normalize(ligature(0xFEFC))).isEqualTo("لا")
        // U+FEF7/FEF8 (hamza-above) → alef unifies to bare alef.
        assertThat(gazetteer.normalize(ligature(0xFEF7))).isEqualTo("لا")
        assertThat(gazetteer.normalize(ligature(0xFEF8))).isEqualTo("لا")
        // U+FEF5/FEF6 (madda) → bare alef.
        assertThat(gazetteer.normalize(ligature(0xFEF5))).isEqualTo("لا")
        assertThat(gazetteer.normalize(ligature(0xFEF6))).isEqualTo("لا")
        // U+FEF9/FEFA (hamza-below) → bare alef.
        assertThat(gazetteer.normalize(ligature(0xFEF9))).isEqualTo("لا")
        assertThat(gazetteer.normalize(ligature(0xFEFA))).isEqualTo("لا")
        // Typed forms agree through the same pipeline.
        assertThat(gazetteer.normalize("لأ")).isEqualTo(gazetteer.normalize("لا"))
    }

    @Test
    fun collapsesWhitespaceAndLowercasesLatin() {
        assertThat(gazetteer.normalize("  Tahrir   Square  ")).isEqualTo("tahrir square")
        assertThat(gazetteer.normalize("ميدان  التحرير")).isEqualTo("ميدان التحرير")
    }
}
