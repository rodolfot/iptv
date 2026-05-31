package com.iptv.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iptv.app.domain.model.ContentType
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(profileId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND type = :type ORDER BY addedAt DESC")
    fun observeByType(profileId: String, type: ContentType): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND type = :type AND itemId = :id)")
    fun isFavorite(profileId: String, type: ContentType, id: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND type = :type AND itemId = :id")
    suspend fun delete(profileId: String, type: ContentType, id: Int)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(profileId: String): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE profileId = :profileId AND type = :type AND itemId = :id)")
    fun isInWatchlist(profileId: String, type: ContentType, id: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND type = :type AND itemId = :id")
    suspend fun delete(profileId: String, type: ContentType, id: Int)
}

@Dao
interface EpisodeProgressDao {
    @Query("SELECT * FROM episode_progress WHERE profileId = :profileId AND episodeId = :id LIMIT 1")
    suspend fun getById(profileId: String, id: String): EpisodeProgressEntity?

    @Query("SELECT * FROM episode_progress WHERE profileId = :profileId AND seriesId = :seriesId")
    suspend fun getBySeries(profileId: String, seriesId: Int): List<EpisodeProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EpisodeProgressEntity)

    @Query("DELETE FROM episode_progress WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String)

    @Query("DELETE FROM episode_progress WHERE profileId = :profileId AND seriesId = :seriesId")
    suspend fun clearForSeries(profileId: String, seriesId: Int)
}

@Dao
interface MovieProgressDao {
    @Query("SELECT * FROM movie_progress WHERE profileId = :profileId AND movieId = :id LIMIT 1")
    suspend fun getById(profileId: String, id: Int): MovieProgressEntity?

    @Query("SELECT * FROM movie_progress WHERE profileId = :profileId AND watched = 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeInProgress(profileId: String, limit: Int = 20): Flow<List<MovieProgressEntity>>

    /**
     * Todos os registros de progresso do perfil — assistidos e em progresso —
     * para alimentar os badges nos cards (✓ assistido / ⏳ meio assistido).
     * Coleção tipicamente pequena (centenas de itens no max).
     */
    @Query("SELECT * FROM movie_progress WHERE profileId = :profileId")
    fun observeAll(profileId: String): Flow<List<MovieProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MovieProgressEntity)

    @Query("DELETE FROM movie_progress WHERE profileId = :profileId AND movieId = :id")
    suspend fun delete(profileId: String, id: Int)

    @Query("DELETE FROM movie_progress WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String)
}

@Dao
interface SeriesProgressDao {
    @Query("SELECT * FROM series_progress WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(profileId: String, limit: Int = 20): Flow<List<SeriesProgressEntity>>

    @Query("SELECT * FROM series_progress WHERE profileId = :profileId AND seriesId = :id LIMIT 1")
    suspend fun getById(profileId: String, id: Int): SeriesProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SeriesProgressEntity)

    @Query("DELETE FROM series_progress WHERE profileId = :profileId AND seriesId = :id")
    suspend fun delete(profileId: String, id: Int)

    @Query("DELETE FROM series_progress WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String)
}

@Dao
interface LiveHistoryDao {
    @Query("SELECT * FROM live_history WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(profileId: String, limit: Int = 10): Flow<List<LiveHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LiveHistoryEntity)

    /**
     * Mantém só os N mais recentes. Chamado depois de cada upsert para evitar
     * que o zapping deixe a tabela com centenas de linhas. Usa subquery porque
     * Room/SQLite não permite `DELETE ... ORDER BY LIMIT` em todas as versões.
     */
    @Query(
        "DELETE FROM live_history WHERE profileId = :profileId AND channelId NOT IN (" +
            "SELECT channelId FROM live_history WHERE profileId = :profileId " +
            "ORDER BY updatedAt DESC LIMIT :keep)"
    )
    suspend fun trim(profileId: String, keep: Int = 10)

    @Query("DELETE FROM live_history WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String)
}
