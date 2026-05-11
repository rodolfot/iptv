package com.iptv.app.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R
import com.iptv.app.domain.model.ContentType
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchlistScreen(
    vm: HomeViewModel,
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit
) {
    val items by vm.watchlist.collectAsState()
    val dim = rememberTvDim()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dim.ScreenPadding, vertical = 12.dp)) {
        Text(
            stringResource(R.string.watchlist_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_watchlist_title),
                message = stringResource(R.string.empty_watchlist_message),
                icon = Icons.Filled.BookmarkBorder
            )
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(dim.SeriesGridColumns),
            horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
        ) {
            items(items) { entry ->
                val icon = when (entry.type) {
                    ContentType.MOVIE -> Icons.Filled.Movie
                    ContentType.SERIES -> Icons.Filled.Tv
                    ContentType.LIVE -> Icons.Filled.Tv
                }
                PosterCard(
                    title = entry.name,
                    imageUrl = entry.logoUrl,
                    fallbackIcon = icon
                ) {
                    when (entry.type) {
                        ContentType.MOVIE -> onPlay(
                            PlayerArgs(
                                kind = PlayerKind.MOVIE,
                                streamId = entry.itemId,
                                title = entry.name,
                                containerExtension = entry.containerExtension,
                                posterUrl = entry.logoUrl,
                                categoryId = entry.categoryId
                            )
                        )
                        ContentType.SERIES -> onOpenSeries(entry.itemId, entry.name, entry.logoUrl)
                        ContentType.LIVE -> onPlay(
                            PlayerArgs(
                                kind = PlayerKind.LIVE,
                                streamId = entry.itemId,
                                title = entry.name,
                                containerExtension = null
                            )
                        )
                    }
                }
            }
        }
    }
}
