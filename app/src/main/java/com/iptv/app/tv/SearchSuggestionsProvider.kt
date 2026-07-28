package com.iptv.app.tv

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.iptv.app.R
import com.iptv.app.data.cache.CatalogCacheRepository
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

/**
 * Alimenta a busca global da Android TV com sugestões do catálogo (canais e
 * filmes do cache FTS). Cada sugestão carrega um deep link `tartatv://play?...`,
 * então selecioná-la abre o TartaTV direto no player. Como ContentProvider não
 * passa pela injeção do Hilt no construtor, obtemos as dependências via
 * [EntryPointAccessors].
 */
class SearchSuggestionsProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun catalog(): CatalogCacheRepository
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val query = uri.lastPathSegment ?: return null
        // Sem texto digitado o último segmento é o próprio path padrão.
        if (query == SearchManager.SUGGEST_URI_PATH_QUERY || query.trim().length < 2) return null

        val repo = EntryPointAccessors.fromApplication(
            ctx.applicationContext, Deps::class.java
        ).catalog()

        val cols = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION
        )
        val cursor = MatrixCursor(cols)
        val liveLabel = ctx.getString(R.string.tab_live)
        val movieLabel = ctx.getString(R.string.tab_movies)
        runCatching {
            runBlocking {
                var id = 0L
                repo.searchLive(query, 8).forEach { ch ->
                    val link = PlayerArgs.toDeepLink(
                        PlayerArgs(
                            kind = PlayerKind.LIVE,
                            streamId = ch.streamId,
                            title = ch.name,
                            containerExtension = null,
                            categoryId = ch.categoryId
                        )
                    )
                    cursor.addRow(arrayOf(id++, ch.name, liveLabel, ch.logoUrl, link, Intent.ACTION_VIEW))
                }
                repo.searchMovies(query, 8).forEach { m ->
                    val link = PlayerArgs.toDeepLink(
                        PlayerArgs(
                            kind = PlayerKind.MOVIE,
                            streamId = m.streamId,
                            title = m.name,
                            containerExtension = m.containerExtension,
                            posterUrl = m.posterUrl,
                            categoryId = m.categoryId
                        )
                    )
                    cursor.addRow(arrayOf(id++, m.name, movieLabel, m.posterUrl, link, Intent.ACTION_VIEW))
                }
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = SearchManager.SUGGEST_MIME_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
