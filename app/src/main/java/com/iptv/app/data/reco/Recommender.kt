package com.iptv.app.data.reco

import com.iptv.app.data.db.FavoriteDao
import com.iptv.app.data.db.MovieCacheDao
import com.iptv.app.data.db.MovieCacheEntity
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.SeriesCacheDao
import com.iptv.app.data.db.SeriesCacheEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.domain.model.ContentType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight content-based recommender. No external service, no telemetry — only
 * the local cache and the current profile's signals (favorites + watched/progress).
 *
 * Strategy:
 * 1. Pick a "seed" recently-engaged title.
 * 2. Score every other catalog entry by token overlap on its searchable fields
 *    (genre / cast / category / release decade). Skip the seed itself, anything
 *    the user already favorited, or already finished.
 * 3. Return the top N as a row, labelled with the seed's name.
 *
 * It runs over the in-memory cache snapshots, so it is O(catalog) per refresh —
 * fine for catalogs in the tens of thousands.
 */
@Singleton
class Recommender @Inject constructor(
    private val movieDao: MovieCacheDao,
    private val seriesDao: SeriesCacheDao,
    private val movieProgress: MovieProgressDao,
    private val seriesProgress: SeriesProgressDao,
    private val favoriteDao: FavoriteDao,
    private val currentProfile: CurrentProfile
) {
    /**
     * [seedTitle] é null quando a linha veio do fallback (sem histórico do
     * usuário). A UI usa esse sinal para mostrar um cabeçalho genérico
     * ("Em alta", "Você pode gostar") em vez de mentir "Porque você viu X"
     * — usuário no primeiro acesso nunca viu nada.
     */
    data class MovieRow(val seedTitle: String?, val items: List<MovieCacheEntity>)
    data class SeriesRow(val seedTitle: String?, val items: List<SeriesCacheEntity>)

    private data class MovieSeed(val item: MovieCacheEntity, val fromHistory: Boolean)
    private data class SeriesSeed(val item: SeriesCacheEntity, val fromHistory: Boolean)

    suspend fun moviesFor(limit: Int = 12): MovieRow? {
        val pid = currentProfile.id()
        val all = runCatching { movieDao.observeAll().first() }.getOrDefault(emptyList())
        if (all.size < 2) return null

        val favIds = runCatching { favoriteDao.observeAll(pid).first() }
            .getOrDefault(emptyList())
            .filter { it.type == ContentType.MOVIE }
            .map { it.itemId }
            .toSet()
        val watched = runCatching { movieProgress.observeInProgress(pid, limit = 50).first() }
            .getOrDefault(emptyList())

        val seedInfo = pickSeed(all, favIds, watched.map { it.movieId }.toSet())
            ?: return null
        val seed = seedInfo.item

        val seedTokens = movieTokens(seed)
        if (seedTokens.isEmpty()) return null

        val excluded = favIds + setOf(seed.streamId) + watched.filter { it.watched }.map { it.movieId }
        val scored = all.asSequence()
            .filter { it.streamId !in excluded }
            .map { it to overlap(movieTokens(it), seedTokens) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
        if (scored.isEmpty()) return null
        return MovieRow(
            seedTitle = if (seedInfo.fromHistory) seed.name else null,
            items = scored
        )
    }

    suspend fun seriesFor(limit: Int = 12): SeriesRow? {
        val pid = currentProfile.id()
        val all = runCatching { seriesDao.observeAll().first() }.getOrDefault(emptyList())
        if (all.size < 2) return null

        val favIds = runCatching { favoriteDao.observeAll(pid).first() }
            .getOrDefault(emptyList())
            .filter { it.type == ContentType.SERIES }
            .map { it.itemId }
            .toSet()
        val recent = runCatching { seriesProgress.observeRecent(pid, limit = 20).first() }
            .getOrDefault(emptyList())

        val seedInfo = pickSeriesSeed(all, favIds, recent.map { it.seriesId }.toSet())
            ?: return null
        val seed = seedInfo.item
        val seedTokens = seriesTokens(seed)
        if (seedTokens.isEmpty()) return null

        val excluded = favIds + setOf(seed.seriesId)
        val scored = all.asSequence()
            .filter { it.seriesId !in excluded }
            .map { it to overlap(seriesTokens(it), seedTokens) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
        if (scored.isEmpty()) return null
        return SeriesRow(
            seedTitle = if (seedInfo.fromHistory) seed.name else null,
            items = scored
        )
    }

    private fun pickSeed(
        all: List<MovieCacheEntity>,
        favIds: Set<Int>,
        watchedIds: Set<Int>
    ): MovieSeed? {
        // Prefer the most recent watched/in-progress movie, else the latest
        // favorite (both count as "real history"). Fall back to the highest
        // rated movie so first-run users still see *something*, but flag the
        // row so the UI doesn't claim the user has watched anything yet.
        all.firstOrNull { it.streamId in watchedIds }?.let { return MovieSeed(it, true) }
        all.firstOrNull { it.streamId in favIds }?.let { return MovieSeed(it, true) }
        return all.maxByOrNull { it.rating }?.let { MovieSeed(it, false) }
    }

    private fun pickSeriesSeed(
        all: List<SeriesCacheEntity>,
        favIds: Set<Int>,
        recentIds: Set<Int>
    ): SeriesSeed? {
        all.firstOrNull { it.seriesId in recentIds }?.let { return SeriesSeed(it, true) }
        all.firstOrNull { it.seriesId in favIds }?.let { return SeriesSeed(it, true) }
        return all.maxByOrNull { it.rating }?.let { SeriesSeed(it, false) }
    }

    private fun movieTokens(m: MovieCacheEntity): Set<String> = buildSet {
        m.categoryId?.let { add("cat:$it") }
        m.releaseDate?.let { decadeOf(it)?.let { d -> add("decade:$d") } }
        // Movie cache row only knows category + release date; that's intentionally weak,
        // but combined with the rating tier below it's still enough to cluster similar items.
        ratingTier(m.rating)?.let { add("tier:$it") }
    }

    private fun seriesTokens(s: SeriesCacheEntity): Set<String> = buildSet {
        s.categoryId?.let { add("cat:$it") }
        s.releaseDate?.let { decadeOf(it)?.let { d -> add("decade:$d") } }
        ratingTier(s.rating)?.let { add("tier:$it") }
        s.genre?.split(',', '/', ';')?.forEach {
            val token = it.trim().lowercase()
            if (token.isNotEmpty()) add("genre:$token")
        }
        s.cast?.split(',')?.forEach {
            val token = it.trim().lowercase()
            if (token.length >= 4) add("cast:$token")
        }
    }

    private fun overlap(a: Set<String>, b: Set<String>): Int = a.intersect(b).size

    private fun decadeOf(raw: String): Int? {
        val match = Regex("""\b(19|20)\d{2}\b""").find(raw)?.value?.toIntOrNull() ?: return null
        return (match / 10) * 10
    }

    private fun ratingTier(rating: Double): String? = when {
        rating >= 8.0 -> "high"
        rating >= 6.0 -> "mid"
        rating > 0.0 -> "low"
        else -> null
    }
}
