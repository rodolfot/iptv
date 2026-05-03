package com.iptv.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iptv.app.domain.model.ContentType

@Entity(tableName = "favorites", primaryKeys = ["type", "itemId"])
data class FavoriteEntity(
    val type: ContentType,
    val itemId: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val containerExtension: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "episode_progress")
data class EpisodeProgressEntity(
    @PrimaryKey val episodeId: String,
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNum: Int,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)
