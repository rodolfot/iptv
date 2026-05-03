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
    entities = [FavoriteEntity::class, EpisodeProgressEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun episodeProgressDao(): EpisodeProgressDao
}
