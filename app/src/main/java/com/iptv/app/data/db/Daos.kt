package com.iptv.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iptv.app.domain.model.ContentType
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE type = :type ORDER BY addedAt DESC")
    fun observeByType(type: ContentType): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE type = :type AND itemId = :id)")
    fun isFavorite(type: ContentType, id: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE type = :type AND itemId = :id")
    suspend fun delete(type: ContentType, id: Int)
}

@Dao
interface EpisodeProgressDao {
    @Query("SELECT * FROM episode_progress WHERE episodeId = :id LIMIT 1")
    suspend fun getById(id: String): EpisodeProgressEntity?

    @Query("SELECT * FROM episode_progress WHERE seriesId = :seriesId")
    suspend fun getBySeries(seriesId: Int): List<EpisodeProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EpisodeProgressEntity)

    @Query("DELETE FROM episode_progress")
    suspend fun clearAll()

    @Query("DELETE FROM episode_progress WHERE seriesId = :seriesId")
    suspend fun clearForSeries(seriesId: Int)
}

@Dao
interface MovieProgressDao {
    @Query("SELECT * FROM movie_progress WHERE movieId = :id LIMIT 1")
    suspend fun getById(id: Int): MovieProgressEntity?

    @Query("SELECT * FROM movie_progress WHERE watched = 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeInProgress(limit: Int = 20): Flow<List<MovieProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MovieProgressEntity)

    @Query("DELETE FROM movie_progress WHERE movieId = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM movie_progress")
    suspend fun clearAll()
}

@Dao
interface SeriesProgressDao {
    @Query("SELECT * FROM series_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SeriesProgressEntity>>

    @Query("SELECT * FROM series_progress WHERE seriesId = :id LIMIT 1")
    suspend fun getById(id: Int): SeriesProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SeriesProgressEntity)

    @Query("DELETE FROM series_progress WHERE seriesId = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM series_progress")
    suspend fun clearAll()
}
