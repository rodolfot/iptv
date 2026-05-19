package com.iptv.app.data.cache

import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.m3u.M3uRepository
import com.iptv.app.data.prefs.ProviderType
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
    private val m3u: M3uRepository,
    private val meta: CacheMetaDao,
    private val categories: CategoryCacheDao,
    private val live: LiveCacheDao,
    private val movies: MovieCacheDao,
    private val series: SeriesCacheDao,
    private val detail: com.iptv.app.data.db.DetailCacheDao,
    private val settings: SettingsStore
) {

    /** Active profile's provider — decides Xtream vs. M3U dispatch. */
    private suspend fun activeProvider(): ProviderType {
        val s = settings.flow.first()
        return s.profiles.firstOrNull { it.id == s.activeProfileId }?.provider
            ?: ProviderType.XTREAM
    }
    enum class Scope(val key: String) {
        LIVE_CATEGORIES("live_categories"),
        MOVIE_CATEGORIES("movie_categories"),
        SERIES_CATEGORIES("series_categories"),
        LIVE_STREAMS("live_streams"),
        MOVIE_STREAMS("movie_streams"),
        SERIES_LIST("series_list")
    }

    suspend fun isStale(scope: Scope, ttlMs: Long? = null): Boolean {
        val ts = meta.getUpdatedAt(scope.key) ?: return true
        val effective = ttlMs ?: settings.flow.first().refreshInterval.ttlMs
        return System.currentTimeMillis() - ts > effective
    }

    /** Latest update timestamp across all scopes, or null if never refreshed. */
    suspend fun lastUpdatedAt(): Long? = Scope.values()
        .mapNotNull { meta.getUpdatedAt(it.key) }
        .maxOrNull()

    private suspend fun touch(scope: Scope) {
        meta.upsert(CacheMetaEntity(scope.key, System.currentTimeMillis()))
    }

    /* ----------------- Categories ----------------- */

    suspend fun refreshLiveCategories(): Result<Unit> = runCatching {
        if (activeProvider() == ProviderType.M3U) {
            // M3U batches everything in a single playlist fetch.
            m3u.refresh().getOrThrow()
            touch(Scope.LIVE_CATEGORIES)
            touch(Scope.LIVE_STREAMS)
            return@runCatching
        }
        val extra = settings.flow.first().extraAdultCategoryIds
        val items = api.liveCategories().map { it.toCacheEntity(ContentType.LIVE, extra) }
        categories.replaceAll(ContentType.LIVE, items)
        touch(Scope.LIVE_CATEGORIES)
    }

    suspend fun refreshMovieCategories(): Result<Unit> = runCatching {
        if (activeProvider() == ProviderType.M3U) {
            // M3U has no movie categories — leave the table empty, no error.
            touch(Scope.MOVIE_CATEGORIES)
            return@runCatching
        }
        val extra = settings.flow.first().extraAdultCategoryIds
        val items = api.vodCategories().map { it.toCacheEntity(ContentType.MOVIE, extra) }
        categories.replaceAll(ContentType.MOVIE, items)
        touch(Scope.MOVIE_CATEGORIES)
    }

    suspend fun refreshSeriesCategories(): Result<Unit> = runCatching {
        if (activeProvider() == ProviderType.M3U) {
            touch(Scope.SERIES_CATEGORIES)
            return@runCatching
        }
        val extra = settings.flow.first().extraAdultCategoryIds
        val items = api.seriesCategories().map { it.toCacheEntity(ContentType.SERIES, extra) }
        categories.replaceAll(ContentType.SERIES, items)
        touch(Scope.SERIES_CATEGORIES)
    }

    /* ----------------- Streams ----------------- */

    suspend fun refreshLiveStreams(): Result<Unit> = runCatching {
        if (activeProvider() == ProviderType.M3U) {
            m3u.refresh().getOrThrow()
            touch(Scope.LIVE_CATEGORIES)
            touch(Scope.LIVE_STREAMS)
            return@runCatching
        }
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
        if (activeProvider() == ProviderType.M3U) {
            touch(Scope.MOVIE_STREAMS)
            return@runCatching
        }
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

    /**
     * Fetch direcionado: baixa filmes só da categoria informada, faz upsert
     * no cache e devolve quantos itens vieram. Usado quando o usuário entra
     * numa categoria vazia — em vez de baixar o catálogo inteiro (que pode
     * demorar minutos em listas gigantes), pegamos só essa categoria. O
     * refresh completo continua acontecendo em background pela rotina
     * normal de `isStale + refreshMovieStreams`.
     */
    suspend fun refreshMovieStreamsForCategory(categoryId: String): Result<Int> = runCatching {
        if (activeProvider() == ProviderType.M3U) return@runCatching 0
        val items = api.vodStreams(categoryId).map {
            MovieCacheEntity(
                streamId = it.streamId,
                name = it.name,
                posterUrl = it.streamIcon,
                rating = it.rating5 ?: it.rating?.toDoubleOrNull() ?: 0.0,
                containerExtension = it.containerExtension,
                categoryId = it.categoryId ?: categoryId,
                addedTimestamp = it.added?.toLongOrNull() ?: 0L,
                releaseDate = it.releaseDate ?: it.releaseDateAlt
            )
        }
        movies.upsertAll(items)
        items.size
    }

    /** Fetch direcionado de canais Live por categoria. Equivalente ao
     *  movie per-category — usado pelo Worker que popula em background. */
    suspend fun refreshLiveStreamsForCategory(categoryId: String): Result<Int> = runCatching {
        if (activeProvider() == ProviderType.M3U) return@runCatching 0
        val items = api.liveStreams(categoryId).map {
            LiveChannelCacheEntity(
                streamId = it.streamId,
                num = it.num,
                name = it.name,
                logoUrl = it.streamIcon,
                categoryId = it.categoryId ?: categoryId,
                epgChannelId = it.epgChannelId,
                addedTimestamp = it.added?.toLongOrNull() ?: 0L,
                tvArchive = (it.tvArchive ?: 0) > 0
            )
        }
        live.upsertAll(items)
        items.size
    }

    /** Fetch direcionado de séries por categoria. */
    suspend fun refreshSeriesListForCategory(categoryId: String): Result<Int> = runCatching {
        if (activeProvider() == ProviderType.M3U) return@runCatching 0
        val items = api.series(categoryId).map {
            SeriesCacheEntity(
                seriesId = it.seriesId,
                name = it.name,
                coverUrl = it.cover,
                rating = it.rating5 ?: it.rating?.toDoubleOrNull() ?: 0.0,
                plot = it.plot,
                cast = it.cast,
                genre = it.genre,
                categoryId = it.categoryId ?: categoryId,
                releaseDate = it.releaseDate ?: it.releaseDateSnake,
                lastModifiedTimestamp = it.lastModified?.toLongOrNull() ?: 0L
            )
        }
        series.upsertAll(items)
        items.size
    }

    suspend fun refreshSeriesList(): Result<Unit> = runCatching {
        if (activeProvider() == ProviderType.M3U) {
            touch(Scope.SERIES_LIST)
            return@runCatching
        }
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

    /** Refresh everything sequentially — chamada antiga, mantida pra
     *  compatibilidade mas evite usar em catálogos grandes (50k+ filmes
     *  numa única request dão timeout). Prefira `refreshAllByCategory`. */
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

    data class BootstrapProgress(
        val phase: String,
        val current: Int,
        val total: Int,
    )

    /**
     * Refresh em duas fases:
     *  1) Categorias (live + movies + series) — geralmente <100 KB total.
     *  2) Streams por categoria, uma a uma — cada chamada é rápida (1-3s)
     *     mesmo para categorias grandes (Marvel/DC: 182 filmes / 62 KB).
     *
     * Vantagem sobre `refreshAll`: nunca dá timeout, mesmo em catálogos
     * com 50k+ filmes (que estouravam o read-timeout do OkHttp). E o
     * usuário pode navegar em categorias já carregadas antes do fim.
     *
     * `onProgress` é invocado a cada item processado pra alimentar a UI.
     */
    suspend fun refreshAllByCategory(
        onProgress: suspend (BootstrapProgress) -> Unit = {}
    ): List<Throwable> {
        val errors = mutableListOf<Throwable>()

        onProgress(BootstrapProgress("categories", 0, 3))
        refreshLiveCategories().exceptionOrNull()?.let(errors::add)
        onProgress(BootstrapProgress("categories", 1, 3))
        refreshMovieCategories().exceptionOrNull()?.let(errors::add)
        onProgress(BootstrapProgress("categories", 2, 3))
        refreshSeriesCategories().exceptionOrNull()?.let(errors::add)
        onProgress(BootstrapProgress("categories", 3, 3))

        // Live channels
        val liveCats = categories.get(ContentType.LIVE).map { it.id }
        liveCats.forEachIndexed { idx, catId ->
            runCatching { refreshLiveStreamsForCategory(catId) }
                .exceptionOrNull()?.let(errors::add)
            onProgress(BootstrapProgress("live", idx + 1, liveCats.size))
        }
        touch(Scope.LIVE_STREAMS)

        // Movies
        val movieCats = categories.get(ContentType.MOVIE).map { it.id }
        movieCats.forEachIndexed { idx, catId ->
            runCatching { refreshMovieStreamsForCategory(catId) }
                .exceptionOrNull()?.let(errors::add)
            onProgress(BootstrapProgress("movies", idx + 1, movieCats.size))
        }
        touch(Scope.MOVIE_STREAMS)

        // Series
        val seriesCats = categories.get(ContentType.SERIES).map { it.id }
        seriesCats.forEachIndexed { idx, catId ->
            runCatching { refreshSeriesListForCategory(catId) }
                .exceptionOrNull()?.let(errors::add)
            onProgress(BootstrapProgress("series", idx + 1, seriesCats.size))
        }
        touch(Scope.SERIES_LIST)

        // Invalida o cache de detalhes (series_info, vod_info) — ele tem TTL
        // próprio de 24h e fica "pegado" mesmo quando o provedor publica
        // novas temporadas/episódios. Limpar aqui garante que a próxima
        // entrada em qualquer série/filme busque dados frescos do servidor.
        runCatching { detail.clearAll() }.exceptionOrNull()?.let(errors::add)

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
