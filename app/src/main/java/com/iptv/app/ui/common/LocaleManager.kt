package com.iptv.app.ui.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app locale switching. Uses the AppCompat support library so the same
 * code path covers Android 13+ (which routes through the platform LocaleManager
 * automatically) and older releases.
 *
 * BCP-47 tags expected: "pt-BR", "en", "es", "it", "fr", "de". Pass null or
 * empty to fall back to the system locale.
 */
object LocaleManager {

    /** Apply [tag] now. Recreates activities; safe to call from app start. */
    fun apply(tag: String?) {
        val locales = if (tag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** All locales we ship translations for, in display order. */
    val available: List<LocaleOption> = listOf(
        LocaleOption(null, "🌐 Sistema"),
        LocaleOption("pt-BR", "Português (Brasil)"),
        LocaleOption("en", "English"),
        LocaleOption("es", "Español"),
        LocaleOption("it", "Italiano"),
        LocaleOption("fr", "Français"),
        LocaleOption("de", "Deutsch")
    )

    data class LocaleOption(val tag: String?, val display: String)
}
