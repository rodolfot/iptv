package com.iptv.app.data.db

import androidx.room.Entity
import com.iptv.app.domain.model.ContentType

/**
 * Sentinel profile id used for entries that pre-date multi-profile isolation
 * (migrated rows) and for installations that never created a named profile.
 */
const val DEFAULT_PROFILE_ID = "default"

@Entity(tableName = "favorites", primaryKeys = ["profileId", "type", "itemId"])
data class FavoriteEntity(
    val profileId: String = DEFAULT_PROFILE_ID,
    val type: ContentType,
    val itemId: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val containerExtension: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * "Watch later" list. Same shape as FavoriteEntity but with different intent:
 * Favorite means "I liked this", Watchlist means "I plan to watch this". An
 * item can appear in both lists independently.
 */
@Entity(tableName = "watchlist", primaryKeys = ["profileId", "type", "itemId"])
data class WatchlistEntity(
    val profileId: String = DEFAULT_PROFILE_ID,
    val type: ContentType,
    val itemId: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val containerExtension: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "episode_progress", primaryKeys = ["profileId", "episodeId"])
data class EpisodeProgressEntity(
    val profileId: String = DEFAULT_PROFILE_ID,
    val episodeId: String,
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNum: Int,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "movie_progress", primaryKeys = ["profileId", "movieId"])
data class MovieProgressEntity(
    val profileId: String = DEFAULT_PROFILE_ID,
    val movieId: Int,
    val title: String,
    val posterUrl: String?,
    val containerExtension: String?,
    val categoryId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Histórico de canais assistidos. Insere/atualiza um row por canal a cada
 * vez que o player Live é aberto; "Início" lê os N mais recentes.
 */
@Entity(tableName = "live_history", primaryKeys = ["profileId", "channelId"])
data class LiveHistoryEntity(
    val profileId: String = DEFAULT_PROFILE_ID,
    val channelId: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "series_progress", primaryKeys = ["profileId", "seriesId"])
data class SeriesProgressEntity(
    val profileId: String = DEFAULT_PROFILE_ID,
    val seriesId: Int,
    val title: String,
    val coverUrl: String?,
    val lastEpisodeId: String,
    val lastSeasonNumber: Int,
    val lastEpisodeNum: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
