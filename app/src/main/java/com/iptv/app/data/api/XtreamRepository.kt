package com.iptv.app.data.api

import com.iptv.app.data.db.DetailCacheDao
import com.iptv.app.data.db.DetailCacheEntity
import com.iptv.app.data.prefs.SettingsStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val DETAIL_TTL_MS = 24L * 60 * 60 * 1000

@Singleton
class XtreamRepository @Inject constructor(
    private val api: XtreamApi,
    private val settings: SettingsStore,
    private val detailCache: DetailCacheDao,
    private val moshi: Moshi
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
        val adapter = moshi.adapter(VodInfoResponse::class.java)
        val cached = detailCache.get("vod", vodId)
        val cachedFresh = cached != null && System.currentTimeMillis() - cached.updatedAt < DETAIL_TTL_MS
        if (cachedFresh) {
            runCatching { adapter.fromJson(cached!!.payload) }.getOrNull()?.let { return it }
        }
        val (h, u, p) = creds()
        return runCatching { api.vodInfo(apiUrl(h), u, p, vodId = vodId) }
            .onSuccess { resp ->
                detailCache.upsert(
                    DetailCacheEntity("vod", vodId, adapter.toJson(resp), System.currentTimeMillis())
                )
            }
            .getOrElse {
                // Fall back to whatever we had cached even if stale, before propagating.
                cached?.let { entity ->
                    runCatching { adapter.fromJson(entity.payload) }.getOrNull()?.let { return it }
                }
                throw it
            }
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
        val adapter = moshi.adapter(SeriesInfoResponse::class.java)
        val cached = detailCache.get("series", seriesId)
        val cachedFresh = cached != null && System.currentTimeMillis() - cached.updatedAt < DETAIL_TTL_MS
        if (cachedFresh) {
            runCatching { adapter.fromJson(cached!!.payload) }.getOrNull()?.let { return it }
        }
        val (h, u, p) = creds()
        return runCatching { api.seriesInfo(apiUrl(h), u, p, seriesId = seriesId) }
            .onSuccess { resp ->
                detailCache.upsert(
                    DetailCacheEntity("series", seriesId, adapter.toJson(resp), System.currentTimeMillis())
                )
            }
            .getOrElse {
                cached?.let { entity ->
                    runCatching { adapter.fromJson(entity.payload) }.getOrNull()?.let { return it }
                }
                throw it
            }
    }

    suspend fun liveStreamUrl(streamId: Int, hls: Boolean = true): String {
        val (h, u, p) = creds()
        val ext = if (hls) "m3u8" else "ts"
        return "$h/live/$u/$p/$streamId.$ext"
    }

    /**
     * Time-shift URL for a channel that supports tv_archive.
     * @param startMs absolute UTC time of the desired playback start
     * @param durationMin minutes of content to request from that point
     */
    suspend fun timeshiftUrl(streamId: Int, startMs: Long, durationMin: Int): String {
        val (h, u, p) = creds()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val start = fmt.format(java.util.Date(startMs))
        return "$h/timeshift/$u/$p/$durationMin/$start/$streamId.ts"
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
