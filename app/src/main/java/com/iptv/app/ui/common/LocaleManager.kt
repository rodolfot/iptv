package com.iptv.app.ui.common

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Per-app locale switching. Uses the AppCompat support library so the same
 * code path covers Android 13+ (which routes through the platform LocaleManager
 * automatically) and older releases.
 *
 * BCP-47 tags expected: "pt-BR", "en", "es", "it", "fr", "de". Pass null or
 * empty to fall back to the system locale.
 */
object LocaleManager {

    /**
     * Apply [tag] now. Recreates activities — only call when the user explicitly
     * picks a language. On null/blank we deliberately do nothing instead of
     * calling `setApplicationLocales(empty)` because that triggers a needless
     * activity recreate on launch even when the user never picked a locale.
     */
    fun apply(tag: String?) {
        if (tag.isNullOrBlank()) {
            // System default — no override needed. Returning early avoids the
            // recreate-on-boot loop that was killing the Onboarding screen.
            return
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Explicit "follow system" — used by the Settings → System option. */
    fun resetToSystem() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    /**
     * Aplica o idioma e força a Activity a recriar **somente se** o locale
     * efetivo mudou. AppCompat só dispara recreate automático em
     * AppCompatActivity — como usamos ComponentActivity, recriamos manualmente
     * para a UI refletir o novo idioma imediatamente.
     */
    fun applyAndRecreate(activity: Activity, tag: String?) {
        val currentLocales: LocaleListCompat = ConfigurationCompat.getLocales(
            activity.resources.configuration
        )
        val currentTag = if (currentLocales.isEmpty) null
            else currentLocales[0]?.toLanguageTag()
        val target = tag?.takeIf { it.isNotBlank() }
        val targetEffective = target ?: Locale.getDefault().toLanguageTag()
        if (currentTag == target || currentTag == targetEffective) {
            // Nada mudou; evita um recreate à toa que zera scroll/foco.
            if (target == null) resetToSystem() else apply(target)
            return
        }
        if (target == null) resetToSystem() else apply(target)
        activity.recreate()
    }

    /**
     * All locales we ship translations for, in display order. Tag values match
     * the `values-*` resource folders we ship. Brazilian Portuguese lives in
     * the default `values/` folder, so we use bare `pt` here — `pt-BR` would
     * resolve to `values-pt-rBR/` which doesn't exist, dropping us back to the
     * English fallback.
     */
    val available: List<LocaleOption> = listOf(
        LocaleOption(null, "🌐 Sistema"),
        LocaleOption("pt", "Português (Brasil)"),
        LocaleOption("en", "English"),
        LocaleOption("es", "Español"),
        LocaleOption("it", "Italiano"),
        LocaleOption("fr", "Français"),
        LocaleOption("de", "Deutsch")
    )

    data class LocaleOption(val tag: String?, val display: String)
}
