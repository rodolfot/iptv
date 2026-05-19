package com.iptv.app.ui.favorites

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
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
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.domain.model.ContentType
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.parental.ParentalPinDialog
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

@Composable
fun FavoritesScreen(
    vm: HomeViewModel,
    parental: ParentalSession,
    onPlay: (PlayerArgs) -> Unit
) {
    val all by vm.favorites.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val dim = rememberTvDim()
    val pendingPlayState = remember { mutableStateOf<PlayerArgs?>(null) }

    // Sempre alfabético (A→Z) — usuário pediu ordenação fixa.
    fun sortFor(list: List<FavoriteEntity>): List<FavoriteEntity> =
        list.sortedBy { it.name.lowercase() }

    val channels = sortFor(all.filter { it.type == ContentType.LIVE })
    val movies = sortFor(all.filter { it.type == ContentType.MOVIE })
    val series = sortFor(all.filter { it.type == ContentType.SERIES })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.tab_favorites), style = MaterialTheme.typography.titleLarge)

        if (all.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_favorites_title),
                message = stringResource(R.string.empty_favorites_message),
                icon = Icons.Filled.Favorite
            )
            return
        }

        // 3 colunas: Canais | Filmes | Séries. Cada coluna é uma lista
        // vertical onde os itens são linhas compactas com thumbnail de 36dp +
        // nome ao lado. Substitui os cards 2:3 que ocupavam muito espaço.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (channels.isNotEmpty()) FavoritesColumn(
                label = stringResource(R.string.filter_channels),
                items = channels,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onPick = { entry ->
                    onPlay(PlayerArgs(PlayerKind.LIVE, entry.itemId, entry.name, null))
                }
            )
            if (movies.isNotEmpty()) FavoritesColumn(
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
            if (series.isNotEmpty()) FavoritesColumn(
                label = stringResource(R.string.filter_series),
                items = series,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onPick = { /* abertura de série não tem rota aqui — TODO */ }
            )
        }
    }

    pendingPlayState.value?.let { args ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingPlayState.value = null
                onPlay(args)
            },
            onCancel = { pendingPlayState.value = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }
}

@Composable
private fun FavoritesColumn(
    label: String,
    items: List<FavoriteEntity>,
    modifier: Modifier = Modifier,
    onPick: (FavoriteEntity) -> Unit
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
            lazyColumnItems(items, key = { "${it.type}-${it.itemId}" }) { f ->
                val icon = when (f.type) {
                    ContentType.LIVE -> Icons.Filled.LiveTv
                    ContentType.MOVIE -> Icons.Filled.Movie
                    ContentType.SERIES -> Icons.Filled.Tv
                }
                FavoriteListItem(
                    name = f.name,
                    imageUrl = f.logoUrl,
                    fallbackIcon = icon,
                    onClick = { onPick(f) }
                )
            }
        }
    }
}

/**
 * Linha compacta: thumb de 36dp à esquerda + nome à direita. Foco animado
 * com barra azul lateral. Substitui o card 2:3 que ocupava muito espaço.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteListItem(
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
        label = "fav-bar"
    )
    val bgColor by animateColorAsState(
        targetValue = if (focused) primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 220),
        label = "fav-bg"
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
