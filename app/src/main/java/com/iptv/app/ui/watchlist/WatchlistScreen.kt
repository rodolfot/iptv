package com.iptv.app.ui.watchlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.data.db.WatchlistEntity
import com.iptv.app.domain.model.ContentType
import com.iptv.app.ui.common.EmptyState
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
        // Sem título de página — a aba "Lista" já selecionada no menu
        // principal deixa claro onde o usuário está.
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_watchlist_title),
                message = stringResource(R.string.empty_watchlist_message),
                icon = Icons.Filled.BookmarkBorder
            )
            return
        }

        // Formato de lista (não cards em linha horizontal): mesmo motivo do
        // Favoritos — linha horizontal de cards só mostra poucos por vez.
        // 3 colunas (canais|filmes|séries), cada uma uma lista vertical
        // compacta que mostra muito mais itens simultaneamente.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (channels.isNotEmpty()) WatchlistColumn(
                label = stringResource(R.string.filter_channels),
                items = channels,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onPick = { entry ->
                    onPlay(PlayerArgs(PlayerKind.LIVE, entry.itemId, entry.name, null))
                }
            )
            if (movies.isNotEmpty()) WatchlistColumn(
                label = stringResource(R.string.filter_movies),
                items = movies,
                modifier = Modifier.weight(1f).fillMaxHeight(),
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
            if (series.isNotEmpty()) WatchlistColumn(
                label = stringResource(R.string.filter_series),
                items = series,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onPick = { entry ->
                    onOpenSeries(entry.itemId, entry.name, entry.logoUrl)
                }
            )
        }
    }
}

@Composable
private fun WatchlistColumn(
    label: String,
    items: List<WatchlistEntity>,
    modifier: Modifier = Modifier,
    onPick: (WatchlistEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Text(
            "$label (${items.size})",
            style = MaterialTheme.typography.titleSmall
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            lazyColumnItems(items, key = { "${it.type}-${it.itemId}" }) { w ->
                val icon = when (w.type) {
                    ContentType.LIVE -> Icons.Filled.LiveTv
                    ContentType.MOVIE -> Icons.Filled.Movie
                    ContentType.SERIES -> Icons.Filled.Tv
                }
                WatchlistListItem(
                    name = w.name,
                    imageUrl = w.logoUrl,
                    fallbackIcon = icon,
                    onClick = { onPick(w) }
                )
            }
        }
    }
}

/** Linha compacta — espelhada da FavoriteListItem, mesma densidade. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchlistListItem(
    name: String,
    imageUrl: String?,
    fallbackIcon: ImageVector,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val barWidth by animateDpAsState(
        targetValue = if (focused) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "wl-bar"
    )
    val bgColor by animateColorAsState(
        targetValue = if (focused) primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 220),
        label = "wl-bg"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(barWidth)
                .background(primary)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        fallbackIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
