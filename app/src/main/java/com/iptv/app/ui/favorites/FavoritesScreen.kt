package com.iptv.app.ui.favorites

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.PosterCard
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
    val pendingPlayState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PlayerArgs?>(null) }

    fun sortFor(list: List<FavoriteEntity>): List<FavoriteEntity> = when (settings.favoritesSort) {
        SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
        SortOption.ADDED_DATE_DESC -> list.sortedByDescending { it.addedAt }
        SortOption.ADDED_DATE_ASC -> list.sortedBy { it.addedAt }
        else -> list.sortedBy { it.name.lowercase() }
    }

    val channels = sortFor(all.filter { it.type == ContentType.LIVE })
    val movies = sortFor(all.filter { it.type == ContentType.MOVIE })
    val series = sortFor(all.filter { it.type == ContentType.SERIES })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
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

        if (channels.isNotEmpty()) FavoritesSection(
            label = stringResource(R.string.filter_channels),
            items = channels,
            onPick = { entry ->
                onPlay(PlayerArgs(PlayerKind.LIVE, entry.itemId, entry.name, null))
            }
        )
        if (movies.isNotEmpty()) FavoritesSection(
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
        if (series.isNotEmpty()) FavoritesSection(
            label = stringResource(R.string.filter_series),
            items = series,
            onPick = { /* abertura de série não tem rota aqui — TODO */ }
        )
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
private fun FavoritesSection(
    label: String,
    items: List<FavoriteEntity>,
    onPick: (FavoriteEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label (${items.size})",
            style = MaterialTheme.typography.titleSmall
        )
        // Cards compactos (110dp) — usuário pediu fonte menor e mais cabíveis.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lazyRowItems(items, key = { "${it.type}-${it.itemId}" }) { f ->
                val icon = when (f.type) {
                    ContentType.LIVE -> Icons.Filled.LiveTv
                    ContentType.MOVIE -> Icons.Filled.Movie
                    ContentType.SERIES -> Icons.Filled.Tv
                }
                PosterCard(
                    title = f.name,
                    imageUrl = f.logoUrl,
                    fallbackIcon = icon,
                    overrideWidth = 110.dp,
                    compactTitle = true
                ) {
                    onPick(f)
                }
            }
        }
    }
}
