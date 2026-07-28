package com.iptv.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iptv.app.domain.sort.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val refreshInterval: RefreshInterval = RefreshInterval.HOURS_12,
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
    val searchHistory: List<String> = emptyList(),
    /** BCP-47 tag (e.g. "pt-BR", "en", "es"). Null = follow system locale. */
    val appLocale: String? = null,
    /**
     * Manual override do form factor escolhido pelo usuário no onboarding.
     * Null = auto-detect (uiMode + smallestScreenWidthDp).
     */
    val deviceProfile: DeviceProfile? = null,
    /**
     * Quando true, a Home mostra o horário atual num pequeno chip no canto
     * superior direito por 5 segundos no início de cada hora cheia.
     * Default false: o usuário pediu opt-in pra não poluir a UI.
     */
    val showHourlyClock: Boolean = false,

    // --- Player / EPG avançado (v2) ---
    /** Formato padrão do stream Ao Vivo (TS é mais compatível; HLS adapta bitrate). */
    val streamFormat: StreamFormat = StreamFormat.TS,
    /** Overrides de formato por canal (streamId -> formato). Vazio = usa o padrão. */
    val streamFormatOverrides: Map<Int, StreamFormat> = emptyMap(),
    /** Seleção de decoder: automático, forçar hardware ou forçar software. */
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    /** Modo rádio: reproduz só o áudio (desliga o vídeo) para economizar banda/CPU. */
    val audioOnly: Boolean = false,
    /** Escala do texto de legenda em % (75–200). */
    val subtitleScalePercent: Int = 100,
    /** Preset visual da legenda. */
    val subtitleStyle: SubtitleStyle = SubtitleStyle.DEFAULT,
    /** Pacote do player externo preferido (null = usar player interno). */
    val externalPlayerPackage: String? = null,
    /** URL XMLTV externa para EPG (null = usa o xmltv.php do provedor Xtream). */
    val epgUrlOverride: String? = null,
    /** Correção de fuso/offset aplicada aos horários da EPG, em minutos. */
    val epgOffsetMinutes: Int = 0,
    /** Layout de navegação do Ao Vivo em TV/Tablet (grade, lista com categorias
     *  ou lista com preview em destaque). */
    val liveViewMode: LiveViewMode = LiveViewMode.GRID
)

enum class StreamFormat { TS, HLS }

enum class DecoderMode { AUTO, HARDWARE, SOFTWARE }

enum class SubtitleStyle { DEFAULT, WHITE_ON_BLACK, YELLOW, OUTLINE }

enum class DeviceProfile { TV, TABLET, PHONE }

/**
 * Layout de navegação do Ao Vivo em TV/Tablet:
 *  - GRID: categorias à esquerda + grid de canais + PIP de preview no canto
 *    (layout original, inspirado no Smarters Player Lite).
 *  - LIST_WITH_CATEGORIES: 3 colunas — categorias | lista de canais | preview.
 *  - LIST_FOCUS: 2 áreas — lista de canais | preview ocupando o resto da tela,
 *    sem coluna de categorias (Voltar leva à tela de categorias).
 */
enum class LiveViewMode { GRID, LIST_WITH_CATEGORIES, LIST_FOCUS }

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
        val SEARCH_HISTORY = stringPreferencesKey("search_history_v1")
        val SKIPPED_UPDATE = stringPreferencesKey("skipped_update_version")
        val LAST_NOTIFIED_UPDATE = stringPreferencesKey("last_notified_update_version")
        val APP_LOCALE = stringPreferencesKey("app_locale")
        val DEVICE_PROFILE = stringPreferencesKey("device_profile")
        val SHOW_HOURLY_CLOCK = booleanPreferencesKey("show_hourly_clock")
        val STREAM_FORMAT = stringPreferencesKey("stream_format")
        val STREAM_FORMAT_OVERRIDES = stringPreferencesKey("stream_format_overrides")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val AUDIO_ONLY = booleanPreferencesKey("audio_only")
        val SUBTITLE_SCALE = stringPreferencesKey("subtitle_scale")
        val SUBTITLE_STYLE = stringPreferencesKey("subtitle_style")
        val EXTERNAL_PLAYER = stringPreferencesKey("external_player_pkg")
        val EPG_URL_OVERRIDE = stringPreferencesKey("epg_url_override")
        val EPG_OFFSET_MIN = stringPreferencesKey("epg_offset_min")
        val LIVE_VIEW_MODE = stringPreferencesKey("live_view_mode")
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
            refreshInterval = RefreshInterval.fromName(p[Keys.REFRESH_INTERVAL]) ?: RefreshInterval.HOURS_12,
            profiles = Profile.listFromJson(secure.getProfilesJson()),
            activeProfileId = secure.getActiveProfileId(),
            searchHistory = decodeHistory(p[Keys.SEARCH_HISTORY]),
            appLocale = p[Keys.APP_LOCALE],
            deviceProfile = p[Keys.DEVICE_PROFILE]?.let {
                runCatching { DeviceProfile.valueOf(it) }.getOrNull()
            },
            showHourlyClock = p[Keys.SHOW_HOURLY_CLOCK] == true,
            streamFormat = enumOrDefault(p[Keys.STREAM_FORMAT], StreamFormat.TS),
            streamFormatOverrides = decodeFormatOverrides(p[Keys.STREAM_FORMAT_OVERRIDES]),
            decoderMode = enumOrDefault(p[Keys.DECODER_MODE], DecoderMode.AUTO),
            audioOnly = p[Keys.AUDIO_ONLY] == true,
            subtitleScalePercent = p[Keys.SUBTITLE_SCALE]?.toIntOrNull()?.coerceIn(75, 200) ?: 100,
            subtitleStyle = enumOrDefault(p[Keys.SUBTITLE_STYLE], SubtitleStyle.DEFAULT),
            externalPlayerPackage = p[Keys.EXTERNAL_PLAYER]?.takeIf { it.isNotBlank() },
            epgUrlOverride = p[Keys.EPG_URL_OVERRIDE]?.takeIf { it.isNotBlank() },
            epgOffsetMinutes = p[Keys.EPG_OFFSET_MIN]?.toIntOrNull() ?: 0,
            liveViewMode = enumOrDefault(p[Keys.LIVE_VIEW_MODE], LiveViewMode.GRID)
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun decodeFormatOverrides(raw: String?): Map<Int, StreamFormat> {
        if (raw.isNullOrBlank()) return emptyMap()
        // Formato "id:T,id:H" — T = TS, H = HLS.
        return raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val fmt = if (parts.getOrNull(1) == "H") StreamFormat.HLS else StreamFormat.TS
            id to fmt
        }.toMap()
    }

    private fun encodeFormatOverrides(map: Map<Int, StreamFormat>): String =
        map.entries.joinToString(",") { "${it.key}:${if (it.value == StreamFormat.HLS) "H" else "T"}" }

    suspend fun setShowHourlyClock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_HOURLY_CLOCK] = enabled }
    }

    suspend fun setStreamFormat(format: StreamFormat) {
        context.dataStore.edit { it[Keys.STREAM_FORMAT] = format.name }
    }

    /** Define/limpa o override de formato de um canal específico. */
    suspend fun setChannelStreamFormat(streamId: Int, format: StreamFormat?) {
        context.dataStore.edit { p ->
            val current = decodeFormatOverrides(p[Keys.STREAM_FORMAT_OVERRIDES]).toMutableMap()
            if (format == null) current.remove(streamId) else current[streamId] = format
            p[Keys.STREAM_FORMAT_OVERRIDES] = encodeFormatOverrides(current)
        }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        context.dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    suspend fun setAudioOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_ONLY] = enabled }
    }

    suspend fun setSubtitleScalePercent(percent: Int) {
        context.dataStore.edit { it[Keys.SUBTITLE_SCALE] = percent.coerceIn(75, 200).toString() }
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        context.dataStore.edit { it[Keys.SUBTITLE_STYLE] = style.name }
    }

    suspend fun setExternalPlayer(pkg: String?) {
        context.dataStore.edit {
            if (pkg.isNullOrBlank()) it.remove(Keys.EXTERNAL_PLAYER) else it[Keys.EXTERNAL_PLAYER] = pkg
        }
    }

    suspend fun setEpgUrlOverride(url: String?) {
        context.dataStore.edit {
            if (url.isNullOrBlank()) it.remove(Keys.EPG_URL_OVERRIDE) else it[Keys.EPG_URL_OVERRIDE] = url.trim()
        }
    }

    suspend fun setEpgOffsetMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.EPG_OFFSET_MIN] = minutes.toString() }
    }

    suspend fun setLiveViewMode(mode: LiveViewMode) {
        context.dataStore.edit { it[Keys.LIVE_VIEW_MODE] = mode.name }
    }

    suspend fun setDeviceProfile(profile: DeviceProfile?) {
        context.dataStore.edit {
            if (profile == null) it.remove(Keys.DEVICE_PROFILE)
            else it[Keys.DEVICE_PROFILE] = profile.name
        }
    }

    suspend fun setAppLocale(tag: String?) {
        context.dataStore.edit {
            if (tag.isNullOrBlank()) it.remove(Keys.APP_LOCALE) else it[Keys.APP_LOCALE] = tag
        }
        // Espelha no SharedPreferences síncrono lido pelo IptvApp.onCreate —
        // o DataStore não tem leitura síncrona, então sem isso o locale
        // só "pegaria" depois que a Activity já tivesse resolvido strings.
        context.getSharedPreferences(
            com.iptv.app.IptvApp.LOCALE_PREFS,
            android.content.Context.MODE_PRIVATE
        ).edit().apply {
            if (tag.isNullOrBlank()) remove(com.iptv.app.IptvApp.LOCALE_KEY)
            else putString(com.iptv.app.IptvApp.LOCALE_KEY, tag)
            apply()
        }
    }

    suspend fun skipUpdate(version: String) {
        context.dataStore.edit { it[Keys.SKIPPED_UPDATE] = version }
    }

    suspend fun skippedUpdate(): String? =
        context.dataStore.data.map { it[Keys.SKIPPED_UPDATE] }.first()

    /** Última versão para a qual o worker de auto-update já baixou o APK e
     *  notificou — evita renotificar a cada ciclo pela mesma release. */
    suspend fun lastNotifiedUpdateVersion(): String? =
        context.dataStore.data.map { it[Keys.LAST_NOTIFIED_UPDATE] }.first()

    suspend fun setLastNotifiedUpdateVersion(version: String) {
        context.dataStore.edit { it[Keys.LAST_NOTIFIED_UPDATE] = version }
    }

    suspend fun pushSearchHistory(term: String) {
        val cleaned = term.trim()
        if (cleaned.length < 2) return
        context.dataStore.edit { p ->
            val current = decodeHistory(p[Keys.SEARCH_HISTORY]).toMutableList()
            current.removeAll { it.equals(cleaned, ignoreCase = true) }
            current.add(0, cleaned)
            p[Keys.SEARCH_HISTORY] = encodeHistory(current.take(MAX_SEARCH_HISTORY))
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
    }

    private fun decodeHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        // Stored as newline-separated strings to keep arbitrary characters (commas,
        // spaces, accents) safe without an extra JSON dependency.
        return raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun encodeHistory(items: List<String>): String = items.joinToString("\n")

    suspend fun addProfile(profile: Profile) {
        val current = Profile.listFromJson(secure.getProfilesJson()).toMutableList()
        current.removeAll { it.id == profile.id }
        current.add(profile)
        secure.saveProfilesJson(Profile.listToJson(current))
        bumpSecure()
    }

    suspend fun updateProfile(profile: Profile) = addProfile(profile)

    suspend fun deleteProfile(id: String) {
        val current = Profile.listFromJson(secure.getProfilesJson())
            .filterNot { it.id == id }
        secure.saveProfilesJson(Profile.listToJson(current))
        if (secure.getActiveProfileId() == id) {
            current.firstOrNull()?.let { activateProfile(it.id) }
        }
        bumpSecure()
    }

    suspend fun activateProfile(id: String) {
        val current = Profile.listFromJson(secure.getProfilesJson()).toMutableList()
        val target = current.firstOrNull { it.id == id } ?: return
        secure.saveCredentials(target.host, target.username, target.password)
        target.pin?.let { secure.setPin(it) }
        secure.setActiveProfileId(id)
        val touched = target.copy(lastUsedAt = System.currentTimeMillis())
        current[current.indexOf(target)] = touched
        secure.saveProfilesJson(Profile.listToJson(current))
        bumpSecure()
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
        upsertActiveProfileFromCurrent(host, user, pass)
        bumpSecure()
    }

    /** Reflects the just-saved credentials into the active profile (or creates the first one). */
    private fun upsertActiveProfileFromCurrent(host: String, user: String, pass: String) {
        val current = Profile.listFromJson(secure.getProfilesJson()).toMutableList()
        val activeId = secure.getActiveProfileId()
        val existing = current.firstOrNull { it.id == activeId }
        if (existing != null) {
            val idx = current.indexOf(existing)
            current[idx] = existing.copy(host = host, username = user, password = pass)
        } else {
            val derivedName = user.ifBlank { host }
            val newProfile = Profile(
                id = Profile.newId(),
                name = derivedName,
                host = host,
                username = user,
                password = pass,
                pin = secure.getPin()
            )
            current.add(newProfile)
            secure.setActiveProfileId(newProfile.id)
        }
        secure.saveProfilesJson(Profile.listToJson(current))
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

private const val MAX_SEARCH_HISTORY = 10
