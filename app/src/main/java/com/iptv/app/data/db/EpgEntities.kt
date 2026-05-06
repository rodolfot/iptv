package com.iptv.app.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * EPG programmes per Xtream channel id (matches LiveStreamDto.epgChannelId).
 * Pruned to the upcoming-window when refreshed.
 */
@Entity(
    tableName = "epg_programme",
    primaryKeys = ["channelId", "startMs"],
    indices = [Index(value = ["channelId", "startMs", "stopMs"])]
)
data class EpgProgrammeEntity(
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?
)
