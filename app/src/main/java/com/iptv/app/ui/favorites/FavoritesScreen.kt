package com.iptv.app.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    var filter by rememberSaveable { mutableStateOf<ContentType?>(null) }
    var pendingPlay by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PlayerArgs?>(null) }

    val items = all
        .filter { filter == null || it.type == filter }
        .let { list ->
            when (settings.favoritesSort) {
                SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                SortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                SortOption.ADDED_DATE_DESC -> list.sortedByDescending { it.addedAt }
                SortOption.ADDED_DATE_ASC -> list.sortedBy { it.addedAt }
                else -> list.sortedBy { it.name.lowercase() }
            }
        }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dim.ScreenPadding, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Text(stringResource(R.string.tab_favorites), style = MaterialTheme.typography.headlineSmall)
            Row(modifier = Modifier.padding(start = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TouchableButton(onClick = { filter = null }) { Text(stringResource(R.string.filter_all)) }
                TouchableButton(onClick = { filter = ContentType.LIVE }) { Text(stringResource(R.string.filter_channels)) }
                TouchableButton(onClick = { filter = ContentType.MOVIE }) { Text(stringResource(R.string.filter_movies)) }
                TouchableButton(onClick = { filter = ContentType.SERIES }) { Text(stringResource(R.string.filter_series)) }
            }
        }
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_favorites_title),
                message = stringResource(R.string.empty_favorites_message),
                icon = Icons.Filled.Favorite
            )
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(dim.SeriesGridColumns),
            horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
        ) {
            items(items) { f ->
                val icon = when (f.type) {
                    ContentType.LIVE -> Icons.Filled.LiveTv
                    ContentType.MOVIE -> Icons.Filled.Movie
                    ContentType.SERIES -> Icons.Filled.Tv
                }
                PosterCard(
                    title = f.name,
                    imageUrl = f.logoUrl,
                    fallbackIcon = icon
                ) {
                    val args = when (f.type) {
                        ContentType.LIVE -> PlayerArgs(PlayerKind.LIVE, f.itemId, f.name, null)
                        ContentType.MOVIE -> PlayerArgs(
                            PlayerKind.MOVIE,
                            f.itemId,
                            f.name,
                            f.containerExtension,
                            posterUrl = f.logoUrl,
                            categoryId = f.categoryId
                        )
                        ContentType.SERIES -> null
                    }
                    if (args == null) {
                        // For now we just play first episode by passing seriesId; user can navigate Series tab for full UI
                        return@PosterCard
                    }
                    val mayBeAdult = false
                    if (mayBeAdult && !parental.isUnlocked()) pendingPlay = args
                    else onPlay(args)
                }
            }
        }
    }

    pendingPlay?.let { args ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingPlay = null
                onPlay(args)
            },
            onCancel = { pendingPlay = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }
}
