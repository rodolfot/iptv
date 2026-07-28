package com.iptv.app.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.domain.model.ContentType
import com.iptv.app.ui.common.ChannelCard
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

@Composable
fun WatchlistScreen(
    vm: HomeViewModel,
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit
) {
    val items by vm.watchlist.collectAsState()
    val dim = rememberTvDim()

    // Mesma ordenação alfabética que Favoritos.
    val channels = items.filter { it.type == ContentType.LIVE }.sortedBy { it.name.lowercase() }
    val movies = items.filter { it.type == ContentType.MOVIE }.sortedBy { it.name.lowercase() }
    val series = items.filter { it.type == ContentType.SERIES }.sortedBy { it.name.lowercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.watchlist_title), style = MaterialTheme.typography.titleLarge)

        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_watchlist_title),
                message = stringResource(R.string.empty_watchlist_message),
                icon = Icons.Filled.BookmarkBorder
            )
            return
        }

        // Seções empilhadas em linhas horizontais — mesmo padrão visual de
        // Filmes/Séries/Ao Vivo, em vez das 3 colunas estreitas de antes.
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (channels.isNotEmpty()) {
                WatchlistRowSection(stringResource(R.string.filter_channels), channels.size) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                        items(channels, key = { "live-${it.itemId}" }) { w ->
                            ChannelCard(title = w.name, number = null, logoUrl = w.logoUrl) {
                                onPlay(PlayerArgs(PlayerKind.LIVE, w.itemId, w.name, null))
                            }
                        }
                    }
                }
            }
            if (movies.isNotEmpty()) {
                WatchlistRowSection(stringResource(R.string.filter_movies), movies.size) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                        items(movies, key = { "movie-${it.itemId}" }) { w ->
                            PosterCard(
                                title = w.name,
                                imageUrl = w.logoUrl,
                                fallbackIcon = Icons.Filled.Movie
                            ) {
                                onPlay(
                                    PlayerArgs(
                                        kind = PlayerKind.MOVIE,
                                        streamId = w.itemId,
                                        title = w.name,
                                        containerExtension = w.containerExtension,
                                        posterUrl = w.logoUrl,
                                        categoryId = w.categoryId
                                    )
                                )
                            }
                        }
                    }
                }
            }
            if (series.isNotEmpty()) {
                WatchlistRowSection(stringResource(R.string.filter_series), series.size) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                        items(series, key = { "series-${it.itemId}" }) { w ->
                            PosterCard(
                                title = w.name,
                                imageUrl = w.logoUrl,
                                fallbackIcon = Icons.Filled.Tv,
                                onClick = { onOpenSeries(w.itemId, w.name, w.logoUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cabeçalho "Título (N)" seguido do conteúdo — mesmo padrão de Favoritos. */
@Composable
private fun WatchlistRowSection(label: String, count: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label ($count)", style = MaterialTheme.typography.titleSmall)
        content()
    }
}
