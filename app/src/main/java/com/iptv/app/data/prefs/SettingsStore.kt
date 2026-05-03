package com.iptv.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iptv.app.domain.sort.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val parentalPin: String = "3258",
    val isLoggedIn: Boolean = false,
    val liveSort: SortOption = SortOption.NAME_ASC,
    val moviesSort: SortOption = SortOption.ADDED_DATE_DESC,
    val seriesSort: SortOption = SortOption.ADDED_DATE_DESC,
    val favoritesSort: SortOption = SortOption.NAME_ASC,
    val extraAdultCategoryIds: Set<String> = emptySet()
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HOST = stringPreferencesKey("host")
        val USER = stringPreferencesKey("user")
        val PASS = stringPreferencesKey("pass")
        val PIN = stringPreferencesKey("pin")
        val LOGGED = booleanPreferencesKey("logged")
        val LIVE_SORT = stringPreferencesKey("live_sort")
        val MOVIES_SORT = stringPreferencesKey("movies_sort")
        val SERIES_SORT = stringPreferencesKey("series_sort")
        val FAV_SORT = stringPreferencesKey("fav_sort")
        val EXTRA_ADULT = stringPreferencesKey("extra_adult")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            host = p[Keys.HOST] ?: "",
            username = p[Keys.USER] ?: "",
            password = p[Keys.PASS] ?: "",
            parentalPin = p[Keys.PIN] ?: "3258",
            isLoggedIn = p[Keys.LOGGED] ?: false,
            liveSort = SortOption.fromName(p[Keys.LIVE_SORT]) ?: SortOption.NAME_ASC,
            moviesSort = SortOption.fromName(p[Keys.MOVIES_SORT]) ?: SortOption.ADDED_DATE_DESC,
            seriesSort = SortOption.fromName(p[Keys.SERIES_SORT]) ?: SortOption.ADDED_DATE_DESC,
            favoritesSort = SortOption.fromName(p[Keys.FAV_SORT]) ?: SortOption.NAME_ASC,
            extraAdultCategoryIds = (p[Keys.EXTRA_ADULT] ?: "").split(",").filter { it.isNotBlank() }.toSet()
        )
    }

    suspend fun saveCredentials(host: String, user: String, pass: String) {
        context.dataStore.edit {
            it[Keys.HOST] = host
            it[Keys.USER] = user
            it[Keys.PASS] = pass
            it[Keys.LOGGED] = true
        }
    }

    suspend fun setLoggedOut() {
        context.dataStore.edit {
            it[Keys.LOGGED] = false
        }
    }

    suspend fun setPin(pin: String) {
        context.dataStore.edit { it[Keys.PIN] = pin }
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

    suspend fun toggleAdultCategory(categoryId: String, adult: Boolean) {
        context.dataStore.edit { p ->
            val current = (p[Keys.EXTRA_ADULT] ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
            if (adult) current.add(categoryId) else current.remove(categoryId)
            p[Keys.EXTRA_ADULT] = current.joinToString(",")
        }
    }
}

enum class SortScope { LIVE, MOVIES, SERIES, FAVORITES }
