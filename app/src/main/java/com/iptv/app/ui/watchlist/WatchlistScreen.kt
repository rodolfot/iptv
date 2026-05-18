package com.iptv.app.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.R
import com.iptv.app.data.db.WatchlistEntity
import com.iptv.app.domain.model.ContentType
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

    val channels = items.filter { it.type == ContentType.LIVE }
    val movies = items.filter { it.type == ContentType.MOVIE }
    val series = items.filter { it.type == ContentType.SERIES }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
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

        if (channels.isNotEmpty()) WatchlistSection(
            label = stringResource(R.string.filter_channels),
            items = channels,
            onPick = { entry ->
                onPlay(PlayerArgs(PlayerKind.LIVE, entry.itemId, entry.name, null))
            }
        )
        if (movies.isNotEmpty()) WatchlistSection(
            label = stringResource(R.string.filter_movies),
            items = movies,
            onPick = { entry ->
                onPlay(
                    PlayerArgs(
                        kind = PlayerKind.MOVIE,
                        streamId = entry.itemId,
                        title = entry.name,
                        containerExtension = entry.containerExtension,
                        posterUrl = entry.logoUrl,
                        categoryId = entry.categoryId
                    )
                )
            }
        )
        if (series.isNotEmpty()) WatchlistSection(
            label = stringResource(R.string.filter_series),
            items = series,
            onPick = { entry ->
                onOpenSeries(entry.itemId, entry.name, entry.logoUrl)
            }
        )
    }
}

@Composable
private fun WatchlistSection(
    label: String,
    items: List<WatchlistEntity>,
    onPick: (WatchlistEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label (${items.size})",
            style = MaterialTheme.typography.titleSmall
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyRowItems(items, key = { "${it.type}-${it.itemId}" }) { w ->
                val icon = when (w.type) {
                    ContentType.LIVE -> Icons.Filled.LiveTv
                    ContentType.MOVIE -> Icons.Filled.Movie
                    ContentType.SERIES -> Icons.Filled.Tv
                }
                PosterCard(
                    title = w.name,
                    imageUrl = w.logoUrl,
                    fallbackIcon = icon,
                    overrideWidth = 110.dp,
                    compactTitle = true
                ) {
                    onPick(w)
                }
            }
        }
    }
}
