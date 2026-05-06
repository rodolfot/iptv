package com.iptv.app.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.iptv.app.domain.model.ContentType

/** Last-successful-fetch timestamps for each cache scope, used for TTL. */
@Entity(tableName = "cache_meta")
data class CacheMetaEntity(
    @PrimaryKey val scope: String,
    val updatedAt: Long
)

@Entity(tableName = "category_cache", primaryKeys = ["type", "id"])
data class CategoryCacheEntity(
    val type: ContentType,
    val id: String,
    val name: String,
    val parentId: Int,
    val isAdult: Boolean,
    val sortKey: String
)

@Entity(tableName = "live_cache")
data class LiveChannelCacheEntity(
    @PrimaryKey val streamId: Int,
    val num: Int?,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val addedTimestamp: Long
)

@Entity(tableName = "movie_cache")
data class MovieCacheEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val posterUrl: String?,
    val rating: Double,
    val containerExtension: String?,
    val categoryId: String?,
    val addedTimestamp: Long,
    val releaseDate: String?
)

@Entity(tableName = "series_cache")
data class SeriesCacheEntity(
    @PrimaryKey val seriesId: Int,
    val name: String,
    val coverUrl: String?,
    val rating: Double,
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val categoryId: String?,
    val releaseDate: String?,
    val lastModifiedTimestamp: Long
)

/* ----------------- FTS virtual tables (search) ----------------- */
/* rowid is mapped to streamId/seriesId so we can join back to the cache rows. */

@Fts4
@Entity(tableName = "live_fts")
data class LiveFtsEntity(
    val name: String
)

@Fts4
@Entity(tableName = "movie_fts")
data class MovieFtsEntity(
    val name: String,
    val extra: String
)

@Fts4
@Entity(tableName = "series_fts")
data class SeriesFtsEntity(
    val name: String,
    val extra: String
)
