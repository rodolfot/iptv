package com.iptv.app.domain.model

import com.iptv.app.data.api.CategoryDto
import com.iptv.app.data.api.EpisodeDto
import com.iptv.app.data.api.LiveStreamDto
import com.iptv.app.data.api.SeasonDto
import com.iptv.app.data.api.SeriesDto
import com.iptv.app.data.api.VodStreamDto

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(
    val id: String,
    val name: String,
    val parentId: Int = 0,
    val isAdult: Boolean = false,
    val type: ContentType
)

data class LiveChannel(
    val id: Int,
    val num: Int?,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val addedTimestamp: Long,
    val tvArchive: Boolean = false
)

data class Movie(
    val id: Int,
    val name: String,
    val posterUrl: String?,
    val rating: Double,
    val containerExtension: String?,
    val categoryId: String?,
    val addedTimestamp: Long,
    val releaseDate: String?
)

data class Series(
    val id: Int,
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

data class Season(
    val seasonNumber: Int,
    val name: String,
    val coverUrl: String?,
    val episodeCount: Int
)

data class Episode(
    val id: String,
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNum: Int,
    val title: String,
    val containerExtension: String?,
    val plot: String?,
    val durationSecs: Int?,
    val poster: String?
)

private val ADULT_REGEX = Regex(
    "adult|xxx|\\+18|18\\+|porn|porno|erotic|adulto|sex(?!o(s|tett))",
    RegexOption.IGNORE_CASE
)

fun CategoryDto.toModel(type: ContentType, extraAdultIds: Set<String>): Category =
    Category(
        id = categoryId,
        name = categoryName,
        parentId = parentId ?: 0,
        isAdult = ADULT_REGEX.containsMatchIn(categoryName) || extraAdultIds.contains(categoryId),
        type = type
    )

fun LiveStreamDto.toModel(): LiveChannel = LiveChannel(
    id = streamId,
    num = num,
    name = name,
    logoUrl = streamIcon,
    categoryId = categoryId,
    epgChannelId = epgChannelId,
    addedTimestamp = added?.toLongOrNull() ?: 0L,
    tvArchive = (tvArchive ?: 0) > 0
)

fun VodStreamDto.toModel(): Movie = Movie(
    id = streamId,
    name = name,
    posterUrl = streamIcon,
    rating = rating5 ?: rating?.toDoubleOrNull() ?: 0.0,
    containerExtension = containerExtension,
    categoryId = categoryId,
    addedTimestamp = added?.toLongOrNull() ?: 0L,
    releaseDate = releaseDate ?: releaseDateAlt
)

fun SeriesDto.toModel(): Series = Series(
    id = seriesId,
    name = name,
    coverUrl = cover,
    rating = rating5 ?: rating?.toDoubleOrNull() ?: 0.0,
    plot = plot,
    cast = cast,
    genre = genre,
    categoryId = categoryId,
    releaseDate = releaseDate ?: releaseDateSnake,
    lastModifiedTimestamp = lastModified?.toLongOrNull() ?: 0L
)

fun SeasonDto.toModel(): Season = Season(
    seasonNumber = seasonNumber ?: 0,
    name = name ?: "Temporada ${seasonNumber ?: 0}",
    coverUrl = coverBig ?: cover,
    episodeCount = episodeCount ?: 0
)

fun EpisodeDto.toModel(seriesId: Int, fallbackSeason: Int): Episode = Episode(
    id = id,
    seriesId = seriesId,
    seasonNumber = season ?: fallbackSeason,
    episodeNum = episodeNum ?: 0,
    title = title ?: "Episódio ${episodeNum ?: 0}",
    containerExtension = containerExtension,
    plot = info?.plot,
    durationSecs = info?.durationSecs,
    poster = info?.movieImage
)
