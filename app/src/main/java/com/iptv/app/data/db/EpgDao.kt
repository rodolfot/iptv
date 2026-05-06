package com.iptv.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface EpgDao {
    @Query(
        """
        SELECT * FROM epg_programme
        WHERE channelId = :channelId AND startMs <= :now AND stopMs > :now
        LIMIT 1
        """
    )
    suspend fun getCurrent(channelId: String, now: Long): EpgProgrammeEntity?

    @Query(
        """
        SELECT * FROM epg_programme
        WHERE channelId IN (:channelIds) AND startMs <= :now AND stopMs > :now
        """
    )
    suspend fun getCurrentForChannels(channelIds: List<String>, now: Long): List<EpgProgrammeEntity>

    @Query(
        """
        SELECT * FROM epg_programme
        WHERE channelId = :channelId AND stopMs > :now
        ORDER BY startMs LIMIT :limit
        """
    )
    suspend fun getUpcoming(channelId: String, now: Long, limit: Int = 12): List<EpgProgrammeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpgProgrammeEntity>)

    @Query("DELETE FROM epg_programme WHERE stopMs < :before")
    suspend fun deleteExpired(before: Long)

    @Query("DELETE FROM epg_programme")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(now: Long, items: List<EpgProgrammeEntity>) {
        clear()
        insertAll(items)
    }
}
