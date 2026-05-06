package com.iptv.app.data.cache

import com.iptv.app.data.db.CategoryCacheEntity
import com.iptv.app.data.db.LiveChannelCacheEntity
import com.iptv.app.data.db.MovieCacheEntity
import com.iptv.app.data.db.SeriesCacheEntity
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.model.Series

fun CategoryCacheEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    parentId = parentId,
    isAdult = isAdult,
    type = type
)

fun LiveChannelCacheEntity.toDomain(): LiveChannel = LiveChannel(
    id = streamId,
    num = num,
    name = name,
    logoUrl = logoUrl,
    categoryId = categoryId,
    epgChannelId = epgChannelId,
    addedTimestamp = addedTimestamp
)

fun MovieCacheEntity.toDomain(): Movie = Movie(
    id = streamId,
    name = name,
    posterUrl = posterUrl,
    rating = rating,
    containerExtension = containerExtension,
    categoryId = categoryId,
    addedTimestamp = addedTimestamp,
    releaseDate = releaseDate
)

fun SeriesCacheEntity.toDomain(): Series = Series(
    id = seriesId,
    name = name,
    coverUrl = coverUrl,
    rating = rating,
    plot = plot,
    cast = cast,
    genre = genre,
    categoryId = categoryId,
    releaseDate = releaseDate,
    lastModifiedTimestamp = lastModifiedTimestamp
)
