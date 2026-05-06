package com.iptv.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.iptv.app.domain.model.ContentType

class Converters {
    @TypeConverter fun typeToString(value: ContentType): String = value.name
    @TypeConverter fun stringToType(value: String): ContentType = ContentType.valueOf(value)
}

@Database(
    entities = [
        FavoriteEntity::class,
        EpisodeProgressEntity::class,
        MovieProgressEntity::class,
        SeriesProgressEntity::class,
        CacheMetaEntity::class,
        CategoryCacheEntity::class,
        LiveChannelCacheEntity::class,
        MovieCacheEntity::class,
        SeriesCacheEntity::class,
        LiveFtsEntity::class,
        MovieFtsEntity::class,
        SeriesFtsEntity::class,
        EpgProgrammeEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun episodeProgressDao(): EpisodeProgressDao
    abstract fun movieProgressDao(): MovieProgressDao
    abstract fun seriesProgressDao(): SeriesProgressDao
    abstract fun cacheMetaDao(): CacheMetaDao
    abstract fun categoryCacheDao(): CategoryCacheDao
    abstract fun liveCacheDao(): LiveCacheDao
    abstract fun movieCacheDao(): MovieCacheDao
    abstract fun seriesCacheDao(): SeriesCacheDao
    abstract fun epgDao(): EpgDao
}
