package com.iptv.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.iptv.app.domain.model.ContentType
import kotlinx.coroutines.flow.Flow

/** Projeção `SELECT categoryId, COUNT(*) FROM <cache> GROUP BY categoryId`. */
data class CategoryCount(
    val categoryId: String,
    val count: Int,
)

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

    /** Marca de conteúdo adulto de uma categoria — usado para manter conteúdo
     *  protegido fora dos históricos (canais recentes / continuar assistindo). */
    @Query("SELECT isAdult FROM category_cache WHERE type = :type AND id = :id LIMIT 1")
    suspend fun isAdult(type: ContentType, id: String): Boolean?

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

    @Query("SELECT * FROM live_cache WHERE streamId = :streamId LIMIT 1")
    suspend fun getById(streamId: Int): LiveChannelCacheEntity?

    /** Canais da mesma categoria, na mesma ordem do drawer — usado para o
     *  zapping (botões esquerda/direita do controle) no player Ao Vivo. */
    @Query("SELECT * FROM live_cache WHERE categoryId = :categoryId ORDER BY name COLLATE NOCASE")
    suspend fun getByCategory(categoryId: String): List<LiveChannelCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LiveChannelCacheEntity>)

    @Query("DELETE FROM live_cache")
    suspend fun clear()

    /** Contagem por categoria — usado no drawer pra mostrar "Categoria (N)". */
    @Query("SELECT categoryId, COUNT(*) as count FROM live_cache WHERE categoryId IS NOT NULL GROUP BY categoryId")
    fun observeCountByCategory(): Flow<List<CategoryCount>>

    @Query("DELETE FROM live_fts")
    suspend fun clearFts()

    @Query("INSERT INTO live_fts(rowid, name) VALUES(:streamId, :name)")
    suspend fun insertFts(streamId: Int, name: String)

    @Query("DELETE FROM live_fts WHERE rowid = :streamId")
    suspend fun deleteFts(streamId: Int)

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
        val deduped = items.associateBy { it.streamId }.values
        insertAll(deduped.toList())
        deduped.forEach { insertFts(it.streamId, it.name) }
    }

    /** Upsert per-category — não limpa a tabela. Usado pelo Worker que
     *  popula categoria por categoria em background.
     *  Apaga rowid do FTS antes do insert (sem isso, SQLITE_CONSTRAINT 19). */
    @Transaction
    suspend fun upsertAll(items: List<LiveChannelCacheEntity>) {
        val deduped = items.associateBy { it.streamId }.values
        insertAll(deduped.toList())
        deduped.forEach {
            deleteFts(it.streamId)
            insertFts(it.streamId, it.name)
        }
    }

    @Query("SELECT streamId FROM live_cache WHERE categoryId = :categoryId")
    suspend fun idsInCategory(categoryId: String): List<Int>

    @Query("DELETE FROM live_cache WHERE categoryId = :categoryId")
    suspend fun deleteCategory(categoryId: String)

    /**
     * Troca o conteúdo de UMA categoria pelo que o provedor devolveu agora.
     * Só o upsert deixava para trás canais que saíram da categoria — com a
     * numeração antiga, eles apareciam fora de ordem no meio da lista.
     */
    @Transaction
    suspend fun replaceCategory(categoryId: String, items: List<LiveChannelCacheEntity>) {
        idsInCategory(categoryId).forEach { deleteFts(it) }
        deleteCategory(categoryId)
        upsertAll(items)
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

    @Query("SELECT categoryId, COUNT(*) as count FROM movie_cache WHERE categoryId IS NOT NULL GROUP BY categoryId")
    fun observeCountByCategory(): Flow<List<CategoryCount>>

    @Query("DELETE FROM movie_fts")
    suspend fun clearFts()

    @Query("INSERT INTO movie_fts(rowid, name, extra) VALUES(:streamId, :name, :extra)")
    suspend fun insertFts(streamId: Int, name: String, extra: String)

    // FTS5 (e FTS4) não aceita "INSERT OR REPLACE" diretamente — precisamos
    // apagar o rowid antes de re-inserir. Usado pelo upsert per-category.
    @Query("DELETE FROM movie_fts WHERE rowid = :streamId")
    suspend fun deleteFts(streamId: Int)

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
        val deduped = items.associateBy { it.streamId }.values
        insertAll(deduped.toList())
        deduped.forEach { m ->
            insertFts(
                streamId = m.streamId,
                name = m.name,
                extra = listOfNotNull(m.releaseDate).joinToString(" ")
            )
        }
    }

    /**
     * Upsert sem limpar o resto da tabela — usado em fetches direcionados
     * por categoria, que não devem apagar o que já está em cache de outras
     * categorias.
     *
     * IMPORTANTE: a tabela FTS é virtual e `INSERT` puro com rowid existente
     * dispara SQLITE_CONSTRAINT (code 19) que rolla back a transaction toda
     * — fazendo o filme nem chegar ao `movie_cache`. Apagamos o rowid antes
     * do re-insert (não há `INSERT OR REPLACE` em FTS).
     */
    @Transaction
    suspend fun upsertAll(items: List<MovieCacheEntity>) {
        val deduped = items.associateBy { it.streamId }.values
        insertAll(deduped.toList())
        deduped.forEach { m ->
            deleteFts(m.streamId)
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

    /** Invalida todo o detalhe — chamado ao refresh do catálogo, para que
     *  o usuário receba dados frescos (ex.: novas temporadas) na próxima
     *  entrada em qualquer série/filme. */
    @Query("DELETE FROM detail_cache")
    suspend fun clearAll()
}

@Dao
interface SeriesCacheDao {
    @Query("SELECT * FROM series_cache WHERE :categoryId IS NULL OR categoryId = :categoryId ORDER BY lastModifiedTimestamp DESC")
    fun observe(categoryId: String?): Flow<List<SeriesCacheEntity>>

    @Query("SELECT * FROM series_cache ORDER BY lastModifiedTimestamp DESC")
    fun observeAll(): Flow<List<SeriesCacheEntity>>

    /** Categoria de uma série — para checar se é conteúdo adulto ao salvar
     *  progresso (a tabela de progresso de série não guarda categoryId). */
    @Query("SELECT categoryId FROM series_cache WHERE seriesId = :seriesId LIMIT 1")
    suspend fun categoryIdOf(seriesId: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesCacheEntity>)

    @Query("DELETE FROM series_cache")
    suspend fun clear()

    @Query("SELECT categoryId, COUNT(*) as count FROM series_cache WHERE categoryId IS NOT NULL GROUP BY categoryId")
    fun observeCountByCategory(): Flow<List<CategoryCount>>

    @Query("DELETE FROM series_fts")
    suspend fun clearFts()

    @Query("INSERT INTO series_fts(rowid, name, extra) VALUES(:seriesId, :name, :extra)")
    suspend fun insertFts(seriesId: Int, name: String, extra: String)

    @Query("DELETE FROM series_fts WHERE rowid = :seriesId")
    suspend fun deleteFts(seriesId: Int)

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
        val deduped = items.associateBy { it.seriesId }.values
        insertAll(deduped.toList())
        deduped.forEach { s ->
            insertFts(
                seriesId = s.seriesId,
                name = s.name,
                extra = listOfNotNull(s.genre, s.cast, s.plot, s.releaseDate).joinToString(" ")
            )
        }
    }

    /** Upsert per-category — não limpa a tabela. */
    /** Apaga rowid do FTS antes do insert (sem isso, SQLITE_CONSTRAINT 19). */
    @Transaction
    suspend fun upsertAll(items: List<SeriesCacheEntity>) {
        val deduped = items.associateBy { it.seriesId }.values
        insertAll(deduped.toList())
        deduped.forEach { s ->
            deleteFts(s.seriesId)
            insertFts(
                seriesId = s.seriesId,
                name = s.name,
                extra = listOfNotNull(s.genre, s.cast, s.plot, s.releaseDate).joinToString(" ")
            )
        }
    }
}
