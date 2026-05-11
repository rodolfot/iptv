package com.iptv.app.data.m3u

import com.iptv.app.data.db.CategoryCacheDao
import com.iptv.app.data.db.CategoryCacheEntity
import com.iptv.app.data.db.LiveCacheDao
import com.iptv.app.data.db.LiveChannelCacheEntity
import com.iptv.app.data.db.MovieCacheDao
import com.iptv.app.data.db.SeriesCacheDao
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.domain.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ingests an `#EXTM3U` playlist into the same Room cache the Xtream pipeline
 * uses, so the rest of the UI stays oblivious to which provider is active.
 *
 * Scope is intentionally narrow:
 *  * Only live channels (M3U has no first-class movie/series split).
 *  * `host` on the active [com.iptv.app.data.prefs.Profile] is the playlist URL.
 *  * Categories come from `group-title` — items without one fall under "Uncategorized".
 *  * Movies/series tables are cleared so the corresponding tabs show empty
 *    states instead of leftover Xtream data when the user switches profiles.
 */
@Singleton
class M3uRepository @Inject constructor(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
    private val categories: CategoryCacheDao,
    private val live: LiveCacheDao,
    private val movies: MovieCacheDao,
    private val series: SeriesCacheDao
) {
    suspend fun refresh(): Result<Unit> = runCatching {
        val url = settings.flow.first().host
        check(url.isNotBlank()) { "M3U URL is empty" }
        val body = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                resp.body?.string() ?: error("Empty body")
            }
        }
        val tracks = M3uParser.parse(body)
        val groupsInOrder = tracks.mapNotNull { it.groupTitle }.distinct()
        val uncategorized = "uncategorized"
        val groupsWithFallback = if (tracks.any { it.groupTitle.isNullOrBlank() })
            groupsInOrder + uncategorized else groupsInOrder

        val categoryEntities = groupsWithFallback.mapIndexed { idx, group ->
            CategoryCacheEntity(
                type = ContentType.LIVE,
                id = group,
                name = if (group == uncategorized) "Uncategorized" else group,
                parentId = 0,
                isAdult = false,
                sortKey = "%05d".format(idx)
            )
        }
        categories.replaceAll(ContentType.LIVE, categoryEntities)

        val channelEntities = tracks.mapIndexed { idx, track ->
            LiveChannelCacheEntity(
                // M3U has no streamId — synthesize a stable hash from the URL so
                // favorites/progress keys survive across refreshes of the same
                // playlist.
                streamId = track.url.hashCode(),
                num = idx + 1,
                name = track.name,
                logoUrl = track.logoUrl,
                categoryId = track.groupTitle?.takeIf { it.isNotBlank() } ?: uncategorized,
                epgChannelId = track.epgChannelId,
                addedTimestamp = System.currentTimeMillis(),
                tvArchive = false,
                streamUrl = track.url
            )
        }
        live.replaceAll(channelEntities)

        // M3U has no movies/series → wipe leftovers from a previous Xtream session
        // on the same install so the tabs render the empty state cleanly.
        categories.replaceAll(ContentType.MOVIE, emptyList())
        categories.replaceAll(ContentType.SERIES, emptyList())
        movies.replaceAll(emptyList())
        series.replaceAll(emptyList())
    }
}
