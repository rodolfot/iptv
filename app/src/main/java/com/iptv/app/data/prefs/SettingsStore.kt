package com.iptv.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iptv.app.domain.sort.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val parentalPin: String? = null,
    val isPinSet: Boolean = false,
    val isLoggedIn: Boolean = false,
    val termsAccepted: Boolean = false,
    val liveSort: SortOption = SortOption.NAME_ASC,
    val moviesSort: SortOption = SortOption.ADDED_DATE_DESC,
    val seriesSort: SortOption = SortOption.ADDED_DATE_DESC,
    val favoritesSort: SortOption = SortOption.NAME_ASC,
    val extraAdultCategoryIds: Set<String> = emptySet(),
    val refreshInterval: RefreshInterval = RefreshInterval.HOURS_12
)

enum class RefreshInterval(val ttlMs: Long, val labelRes: Int) {
    HOURS_1(1L * 60 * 60 * 1000, com.iptv.app.R.string.refresh_interval_1h),
    HOURS_4(4L * 60 * 60 * 1000, com.iptv.app.R.string.refresh_interval_4h),
    HOURS_12(12L * 60 * 60 * 1000, com.iptv.app.R.string.refresh_interval_12h),
    DAYS_1(24L * 60 * 60 * 1000, com.iptv.app.R.string.refresh_interval_1d),
    DAYS_4(4L * 24 * 60 * 60 * 1000, com.iptv.app.R.string.refresh_interval_4d),
    DAYS_7(7L * 24 * 60 * 60 * 1000, com.iptv.app.R.string.refresh_interval_7d),
    DAYS_30(30L * 24 * 60 * 60 * 1000, com.iptv.app.R.string.refresh_interval_30d);

    companion object {
        fun fromName(value: String?): RefreshInterval? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secure: SecureStore
) {
    private object Keys {
        // Legacy plaintext keys (kept for one-shot migration only)
        val LEGACY_HOST = stringPreferencesKey("host")
        val LEGACY_USER = stringPreferencesKey("user")
        val LEGACY_PASS = stringPreferencesKey("pass")
        val LEGACY_PIN = stringPreferencesKey("pin")
        val LEGACY_LOGGED = booleanPreferencesKey("logged")

        val MIGRATED = booleanPreferencesKey("secure_migrated_v1")
        val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted_v1")
        val LIVE_SORT = stringPreferencesKey("live_sort")
        val MOVIES_SORT = stringPreferencesKey("movies_sort")
        val SERIES_SORT = stringPreferencesKey("series_sort")
        val FAV_SORT = stringPreferencesKey("fav_sort")
        val EXTRA_ADULT = stringPreferencesKey("extra_adult")
        val REFRESH_INTERVAL = stringPreferencesKey("refresh_interval")
    }

    // Reactive trigger so changes in SecureStore (synchronous) propagate to flow consumers.
    private val secureRevision = MutableStateFlow(0L)

    val flow: Flow<AppSettings> = combine(
        context.dataStore.data,
        secureRevision
    ) { p, _ ->
        migrateIfNeeded(p)
        AppSettings(
            host = secure.getHost(),
            username = secure.getUser(),
            password = secure.getPass(),
            parentalPin = secure.getPin(),
            isPinSet = secure.isPinSet(),
            isLoggedIn = secure.isLoggedIn() && secure.getHost().isNotBlank(),
            termsAccepted = p[Keys.TERMS_ACCEPTED] == true,
            liveSort = SortOption.fromName(p[Keys.LIVE_SORT]) ?: SortOption.NAME_ASC,
            moviesSort = SortOption.fromName(p[Keys.MOVIES_SORT]) ?: SortOption.ADDED_DATE_DESC,
            seriesSort = SortOption.fromName(p[Keys.SERIES_SORT]) ?: SortOption.ADDED_DATE_DESC,
            favoritesSort = SortOption.fromName(p[Keys.FAV_SORT]) ?: SortOption.NAME_ASC,
            extraAdultCategoryIds = (p[Keys.EXTRA_ADULT] ?: "")
                .split(",").filter { it.isNotBlank() }.toSet(),
            refreshInterval = RefreshInterval.fromName(p[Keys.REFRESH_INTERVAL]) ?: RefreshInterval.HOURS_12
        )
    }

    private suspend fun migrateIfNeeded(snapshot: androidx.datastore.preferences.core.Preferences) {
        if (snapshot[Keys.MIGRATED] == true) return
        val legacyHost = snapshot[Keys.LEGACY_HOST]
        val legacyUser = snapshot[Keys.LEGACY_USER]
        val legacyPass = snapshot[Keys.LEGACY_PASS]
        val legacyPin = snapshot[Keys.LEGACY_PIN]
        val legacyLogged = snapshot[Keys.LEGACY_LOGGED] == true
        if (!legacyHost.isNullOrBlank() && !legacyUser.isNullOrBlank() && !legacyPass.isNullOrBlank()) {
            secure.saveCredentials(legacyHost, legacyUser, legacyPass)
            if (!legacyLogged) secure.setLoggedOut()
        }
        // Only persist a PIN if user had explicitly set one (legacy default was "3258"
        // which we no longer treat as a real PIN).
        if (!legacyPin.isNullOrBlank() && legacyPin != "3258") {
            secure.setPin(legacyPin)
        }
        context.dataStore.edit { p ->
            p.remove(Keys.LEGACY_HOST)
            p.remove(Keys.LEGACY_USER)
            p.remove(Keys.LEGACY_PASS)
            p.remove(Keys.LEGACY_PIN)
            p.remove(Keys.LEGACY_LOGGED)
            p[Keys.MIGRATED] = true
        }
    }

    suspend fun saveCredentials(host: String, user: String, pass: String) {
        secure.saveCredentials(host, user, pass)
        bumpSecure()
    }

    suspend fun setLoggedOut() {
        secure.setLoggedOut()
        bumpSecure()
    }

    suspend fun setPin(pin: String) {
        secure.setPin(pin)
        bumpSecure()
    }

    suspend fun acceptTerms() {
        context.dataStore.edit { it[Keys.TERMS_ACCEPTED] = true }
    }

    suspend fun setSort(scope: SortScope, option: SortOption) {
        context.dataStore.edit {
            val key = when (scope) {
                SortScope.LIVE -> Keys.LIVE_SORT
                SortScope.MOVIES -> Keys.MOVIES_SORT
                SortScope.SERIES -> Keys.SERIES_SORT
                SortScope.FAVORITES -> Keys.FAV_SORT
            }
            it[key] = option.name
        }
    }

    suspend fun setRefreshInterval(interval: RefreshInterval) {
        context.dataStore.edit { it[Keys.REFRESH_INTERVAL] = interval.name }
    }

    suspend fun toggleAdultCategory(categoryId: String, adult: Boolean) {
        context.dataStore.edit { p ->
            val current = (p[Keys.EXTRA_ADULT] ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
            if (adult) current.add(categoryId) else current.remove(categoryId)
            p[Keys.EXTRA_ADULT] = current.joinToString(",")
        }
    }

    private fun bumpSecure() {
        secureRevision.value = secureRevision.value + 1
    }
}

enum class SortScope { LIVE, MOVIES, SERIES, FAVORITES }
