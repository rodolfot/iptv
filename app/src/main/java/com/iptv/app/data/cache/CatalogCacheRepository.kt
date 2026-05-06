package com.iptv.app.data.cache

import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.CacheMetaDao
import com.iptv.app.data.db.CacheMetaEntity
import com.iptv.app.data.db.CategoryCacheDao
import com.iptv.app.data.db.CategoryCacheEntity
import com.iptv.app.data.db.LiveCacheDao
import com.iptv.app.data.db.LiveChannelCacheEntity
import com.iptv.app.data.db.MovieCacheDao
import com.iptv.app.data.db.MovieCacheEntity
import com.iptv.app.data.db.SeriesCacheDao
import com.iptv.app.data.db.SeriesCacheEntity
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.model.toModel
import com.iptv.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Stale-while-revalidate cache for the Xtream catalog. TTL drives background refresh. */
@Singleton
class CatalogCacheRepository @Inject constructor(
    private val api: XtreamRepository,
    private val meta: CacheMetaDao,
    private val categories: CategoryCacheDao,
    private val live: LiveCacheDao,
    private val movies: MovieCacheDao,
    private val series: SeriesCacheDao,
    private val settings: SettingsStore
) {
    enum class Scope(val key: String) {
        LIVE_CATEGORIES("live_categories"),
        MOVIE_CATEGORIES("movie_categories"),
        SERIES_CATEGORIES("series_categories"),
        LIVE_STREAMS("live_streams"),
        MOVIE_STREAMS("movie_streams"),
        SERIES_LIST("series_list")
    }

    suspend fun isStale(scope: Scope, ttlMs: Long = DEFAULT_TTL_MS): Boolean {
        val ts = meta.getUpdatedAt(scope.key) ?: return true
        return System.currentTimeMillis() - ts > ttlMs
    }

    private suspend fun touch(scope: Scope) {
        meta.upsert(CacheMetaEntity(scope.key, System.currentTimeMillis()))
    }

    /* ----------------- Categories ----------------- */

    suspend fun refreshLiveCategories(): Result<Unit> = runCatching {
        val extra = settings.flow.first().extraAdultCategoryIds
        val items = api.liveCategories().map { it.toCacheEntity(ContentType.LIVE, extra) }
        categories.replaceAll(ContentType.LIVE, items)
        touch(Scope.LIVE_CATEGORIES)
    }

    suspend fun refreshMovieCategories(): Result<Unit> = runCatching {
        val extra = settings.flow.first().extraAdultCategoryIds
        val items = api.vodCategories().map { it.toCacheEntity(ContentType.MOVIE, extra) }
        categories.replaceAll(ContentType.MOVIE, items)
        touch(Scope.MOVIE_CATEGORIES)
    }

    suspend fun refreshSeriesCategories(): Result<Unit> = runCatching {
        val extra = settings.flow.first().extraAdultCategoryIds
        val items = api.seriesCategories().map { it.toCacheEntity(ContentType.SERIES, extra) }
        categories.replaceAll(ContentType.SERIES, items)
        touch(Scope.SERIES_CATEGORIES)
    }

    /* ----------------- Streams ----------------- */

    suspend fun refreshLiveStreams(): Result<Unit> = runCatching {
        val items = api.liveStreams(null).map {
            LiveChannelCacheEntity(
                streamId = it.streamId,
                num = it.num,
                name = it.name,
                logoUrl = it.streamIcon,
                categoryId = it.categoryId,
                epgChannelId = it.epgChannelId,
                addedTimestamp = it.added?.toLongOrNull() ?: 0L,
                tvArchive = (it.tvArchive ?: 0) > 0
            )
        }
        live.replaceAll(items)
        touch(Scope.LIVE_STREAMS)
    }

    suspend fun refreshMovieStreams(): Result<Unit> = runCatching {
        val items = api.vodStreams(null).map {
            MovieCacheEntity(
                streamId = it.streamId,
                name = it.name,
                posterUrl = it.streamIcon,
                rating = it.rating5 ?: it.rating?.toDoubleOrNull() ?: 0.0,
                containerExtension = it.containerExtension,
                categoryId = it.categoryId,
                addedTimestamp = it.added?.toLongOrNull() ?: 0L,
                releaseDate = it.releaseDate ?: it.releaseDateAlt
            )
        }
        movies.replaceAll(items)
        touch(Scope.MOVIE_STREAMS)
    }

    suspend fun refreshSeriesList(): Result<Unit> = runCatching {
        val items = api.series(null).map {
            SeriesCacheEntity(
                seriesId = it.seriesId,
                name = it.name,
                coverUrl = it.cover,
                rating = it.rating5 ?: it.rating?.toDoubleOrNull() ?: 0.0,
                plot = it.plot,
                cast = it.cast,
                genre = it.genre,
                categoryId = it.categoryId,
                releaseDate = it.releaseDate ?: it.releaseDateSnake,
                lastModifiedTimestamp = it.lastModified?.toLongOrNull() ?: 0L
            )
        }
        series.replaceAll(items)
        touch(Scope.SERIES_LIST)
    }

    /** Refresh everything sequentially (used by the background Worker). */
    suspend fun refreshAll(): List<Throwable> {
        val errors = mutableListOf<Throwable>()
        listOf(
            refreshLiveCategories(),
            refreshMovieCategories(),
            refreshSeriesCategories(),
            refreshLiveStreams(),
            refreshMovieStreams(),
            refreshSeriesList()
        ).forEach { r -> r.exceptionOrNull()?.let(errors::add) }
        return errors
    }

    /* ----------------- Search (FTS) ----------------- */

    private fun ftsQuery(raw: String): String {
        // Basic sanitization: split on non-word, prefix-match each token (>=2 chars).
        val tokens = raw.trim()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }
            .map { it.replace("\"", "") + "*" }
        return if (tokens.isEmpty()) "" else tokens.joinToString(" ")
    }

    suspend fun searchLive(query: String, limit: Int = 80): List<LiveChannelCacheEntity> {
        val q = ftsQuery(query)
        return if (q.isBlank()) emptyList() else live.search(q, limit)
    }

    suspend fun searchMovies(query: String, limit: Int = 80): List<MovieCacheEntity> {
        val q = ftsQuery(query)
        return if (q.isBlank()) emptyList() else movies.search(q, limit)
    }

    suspend fun searchSeries(query: String, limit: Int = 80): List<SeriesCacheEntity> {
        val q = ftsQuery(query)
        return if (q.isBlank()) emptyList() else series.search(q, limit)
    }

    companion object {
        const val DEFAULT_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
    }
}

private fun com.iptv.app.data.api.CategoryDto.toCacheEntity(
    type: ContentType,
    extraAdult: Set<String>
): CategoryCacheEntity {
    val model = this.toModel(type, extraAdult)
    return CategoryCacheEntity(
        type = type,
        id = categoryId,
        name = categoryName,
        parentId = parentId ?: 0,
        isAdult = model.isAdult,
        sortKey = categoryName.lowercase()
    )
}
