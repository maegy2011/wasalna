package com.app.wasalna.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language (Arabic/English/System) without adding new libraries.
 *
 * Choice of mechanism (documented per requirements):
 * - API 33+ (Tiramisu): framework android.app.LocaleManager.setApplicationLocales.
 *   No dependency needed, the system persists the choice and recreates
 *   activities automatically.
 * - API 26–32 (minSdk 26): legacy path — Locale.setDefault +
 *   Resources.updateConfiguration + activity.recreate(). On process start
 *   WasalnaApp re-applies the persisted code before first composition.
 *   androidx.appcompat (AppCompatDelegate.setApplicationLocales) was
 *   deliberately NOT added — it would pull a heavy new dependency chain
 *   for what the framework + a small helper already cover.
 */
object AppLocaleHelper {

    const val CODE_SYSTEM = ""
    const val CODE_ARABIC = "ar"
    const val CODE_ENGLISH = "en"

    fun isSupported(code: String): Boolean =
        code == CODE_SYSTEM || code == CODE_ARABIC || code == CODE_ENGLISH

    fun normalize(code: String): String =
        if (isSupported(code)) code else CODE_SYSTEM

    /** Applies [code] now. On API <33 the caller must recreate the activity. */
    fun apply(context: Context, code: String) {
        val normalized = normalize(code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales = if (normalized.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalized)
            }
        } else {
            applyLegacy(context, normalized)
        }
    }

    /** Legacy path (API 26–32): updates app resources in place. */
    fun applyLegacy(context: Context, code: String) {
        val locale: Locale = if (code.isBlank()) {
            systemLocale()
        } else {
            Locale.forLanguageTag(code)
        }
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    private fun systemLocale(): Locale {
        return runCatching {
            Resources.getSystem().configuration.getLocales().get(0)
        }.getOrNull() ?: Locale.ENGLISH
    }
}
