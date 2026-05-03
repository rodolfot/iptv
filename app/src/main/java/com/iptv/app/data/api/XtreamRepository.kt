package com.iptv.app.data.api

import com.iptv.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRepository @Inject constructor(
    private val api: XtreamApi,
    private val settings: SettingsStore
) {

    private suspend fun creds(): Triple<String, String, String> {
        val s = settings.flow.first()
        return Triple(s.host.trimEnd('/'), s.username, s.password)
    }

    private fun apiUrl(host: String) = "$host/player_api.php"

    suspend fun login(host: String, user: String, pass: String): LoginResponse {
        val cleanHost = host.trimEnd('/')
        return api.login(apiUrl(cleanHost), user, pass)
    }

    suspend fun liveCategories(): List<CategoryDto> {
        val (h, u, p) = creds()
        return api.liveCategories(apiUrl(h), u, p)
    }

    suspend fun liveStreams(categoryId: String?): List<LiveStreamDto> {
        val (h, u, p) = creds()
        return api.liveStreams(apiUrl(h), u, p, categoryId = categoryId)
    }

    suspend fun vodCategories(): List<CategoryDto> {
        val (h, u, p) = creds()
        return api.vodCategories(apiUrl(h), u, p)
    }

    suspend fun vodStreams(categoryId: String?): List<VodStreamDto> {
        val (h, u, p) = creds()
        return api.vodStreams(apiUrl(h), u, p, categoryId = categoryId)
    }

    suspend fun vodInfo(vodId: Int): VodInfoResponse {
        val (h, u, p) = creds()
        return api.vodInfo(apiUrl(h), u, p, vodId = vodId)
    }

    suspend fun seriesCategories(): List<CategoryDto> {
        val (h, u, p) = creds()
        return api.seriesCategories(apiUrl(h), u, p)
    }

    suspend fun series(categoryId: String?): List<SeriesDto> {
        val (h, u, p) = creds()
        return api.series(apiUrl(h), u, p, categoryId = categoryId)
    }

    suspend fun seriesInfo(seriesId: Int): SeriesInfoResponse {
        val (h, u, p) = creds()
        return api.seriesInfo(apiUrl(h), u, p, seriesId = seriesId)
    }

    suspend fun liveStreamUrl(streamId: Int, hls: Boolean = true): String {
        val (h, u, p) = creds()
        val ext = if (hls) "m3u8" else "ts"
        return "$h/live/$u/$p/$streamId.$ext"
    }

    suspend fun movieStreamUrl(streamId: Int, containerExtension: String?): String {
        val (h, u, p) = creds()
        val ext = (containerExtension ?: "mp4").trim('.')
        return "$h/movie/$u/$p/$streamId.$ext"
    }

    suspend fun episodeStreamUrl(episodeId: String, containerExtension: String?): String {
        val (h, u, p) = creds()
        val ext = (containerExtension ?: "mp4").trim('.')
        return "$h/series/$u/$p/$episodeId.$ext"
    }

    suspend fun epgUrl(): String {
        val (h, u, p) = creds()
        return "$h/xmltv.php?username=$u&password=$p"
    }
}
