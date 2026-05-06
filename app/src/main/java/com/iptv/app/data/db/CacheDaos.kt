package com.iptv.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.iptv.app.domain.model.ContentType
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheMetaDao {
    @Query("SELECT updatedAt FROM cache_meta WHERE scope = :scope LIMIT 1")
    suspend fun getUpdatedAt(scope: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: CacheMetaEntity)
}

@Dao
interface CategoryCacheDao {
    @Query("SELECT * FROM category_cache WHERE type = :type ORDER BY sortKey")
    fun observe(type: ContentType): Flow<List<CategoryCacheEntity>>

    @Query("SELECT * FROM category_cache WHERE type = :type ORDER BY sortKey")
    suspend fun get(type: ContentType): List<CategoryCacheEntity>

    @Query("DELETE FROM category_cache WHERE type = :type")
    suspend fun clear(type: ContentType)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryCacheEntity>)

    @Transaction
    suspend fun replaceAll(type: ContentType, items: List<CategoryCacheEntity>) {
        clear(type)
        insertAll(items)
    }
}

@Dao
interface LiveCacheDao {
    @Query("SELECT * FROM live_cache WHERE :categoryId IS NULL OR categoryId = :categoryId ORDER BY name COLLATE NOCASE")
    fun observe(categoryId: String?): Flow<List<LiveChannelCacheEntity>>

    @Query("SELECT * FROM live_cache ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LiveChannelCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LiveChannelCacheEntity>)

    @Query("DELETE FROM live_cache")
    suspend fun clear()

    @Query("DELETE FROM live_fts")
    suspend fun clearFts()

    @Query("INSERT INTO live_fts(rowid, name) VALUES(:streamId, :name)")
    suspend fun insertFts(streamId: Int, name: String)

    @Query(
        """
        SELECT lc.* FROM live_cache lc
        JOIN live_fts ON live_fts.rowid = lc.streamId
        WHERE live_fts MATCH :query
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<LiveChannelCacheEntity>

    @Transaction
    suspend fun replaceAll(items: List<LiveChannelCacheEntity>) {
        clear()
        clearFts()
        insertAll(items)
        items.forEach { insertFts(it.streamId, it.name) }
    }
}

@Dao
interface MovieCacheDao {
    @Query("SELECT * FROM movie_cache WHERE :categoryId IS NULL OR categoryId = :categoryId ORDER BY addedTimestamp DESC")
    fun observe(categoryId: String?): Flow<List<MovieCacheEntity>>

    @Query("SELECT * FROM movie_cache ORDER BY addedTimestamp DESC")
    fun observeAll(): Flow<List<MovieCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MovieCacheEntity>)

    @Query("DELETE FROM movie_cache")
    suspend fun clear()

    @Query("DELETE FROM movie_fts")
    suspend fun clearFts()

    @Query("INSERT INTO movie_fts(rowid, name, extra) VALUES(:streamId, :name, :extra)")
    suspend fun insertFts(streamId: Int, name: String, extra: String)

    @Query(
        """
        SELECT mc.* FROM movie_cache mc
        JOIN movie_fts ON movie_fts.rowid = mc.streamId
        WHERE movie_fts MATCH :query
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<MovieCacheEntity>

    @Transaction
    suspend fun replaceAll(items: List<MovieCacheEntity>) {
        clear()
        clearFts()
        insertAll(items)
        items.forEach { m ->
            insertFts(
                streamId = m.streamId,
                name = m.name,
                extra = listOfNotNull(m.releaseDate).joinToString(" ")
            )
        }
    }
}

@Dao
interface DetailCacheDao {
    @Query("SELECT * FROM detail_cache WHERE kind = :kind AND id = :id LIMIT 1")
    suspend fun get(kind: String, id: Int): DetailCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DetailCacheEntity)

    @Query("DELETE FROM detail_cache WHERE updatedAt < :before")
    suspend fun deleteExpired(before: Long)
}

@Dao
interface SeriesCacheDao {
    @Query("SELECT * FROM series_cache WHERE :categoryId IS NULL OR categoryId = :categoryId ORDER BY lastModifiedTimestamp DESC")
    fun observe(categoryId: String?): Flow<List<SeriesCacheEntity>>

    @Query("SELECT * FROM series_cache ORDER BY lastModifiedTimestamp DESC")
    fun observeAll(): Flow<List<SeriesCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesCacheEntity>)

    @Query("DELETE FROM series_cache")
    suspend fun clear()

    @Query("DELETE FROM series_fts")
    suspend fun clearFts()

    @Query("INSERT INTO series_fts(rowid, name, extra) VALUES(:seriesId, :name, :extra)")
    suspend fun insertFts(seriesId: Int, name: String, extra: String)

    @Query(
        """
        SELECT sc.* FROM series_cache sc
        JOIN series_fts ON series_fts.rowid = sc.seriesId
        WHERE series_fts MATCH :query
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<SeriesCacheEntity>

    @Transaction
    suspend fun replaceAll(items: List<SeriesCacheEntity>) {
        clear()
        clearFts()
        insertAll(items)
        items.forEach { s ->
            insertFts(
                seriesId = s.seriesId,
                name = s.name,
                extra = listOfNotNull(s.genre, s.cast, s.plot, s.releaseDate).joinToString(" ")
            )
        }
    }
}
