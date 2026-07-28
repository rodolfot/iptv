package com.iptv.app.ui.favorites

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
import androidx.compose.runtime.produceState
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
import com.iptv.app.ui.common.PreviewPlayer
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

    // Item atualmente sob foco — pode ser canal, filme ou série. Série não
    // mostra preview (usuário pediu pra economizar banda + não faz sentido
    // visualizar episódio aleatório).
    var focusedFavorite by remember { mutableStateOf<FavoriteEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sem título de página — a aba "Favoritos" já selecionada no menu
        // principal deixa claro onde o usuário está.
        if (all.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_favorites_title),
                message = stringResource(R.string.empty_favorites_message),
                icon = Icons.Filled.Favorite
            )
            return
        }

        // Formato de lista (não cards em linha horizontal): com 30+ favoritos
        // por categoria, uma linha horizontal de cards só mostra ~5 por vez e
        // esconde o resto atrás de scroll lateral. Lista vertical compacta
        // mostra muito mais itens simultaneamente — 3 colunas (canais|filmes|
        // séries) à esquerda, painel de preview à direita.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.weight(3f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (channels.isNotEmpty()) FavoritesColumn(
                    label = stringResource(R.string.filter_channels),
                    items = channels,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onFocusItem = { focusedFavorite = it },
                    onPick = { entry ->
                        onPlay(PlayerArgs(PlayerKind.LIVE, entry.itemId, entry.name, null))
                    }
                )
                if (movies.isNotEmpty()) FavoritesColumn(
                    label = stringResource(R.string.filter_movies),
                    items = movies,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onFocusItem = { focusedFavorite = it },
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
                    onFocusItem = { focusedFavorite = it },
                    onPick = { /* abertura de série não tem rota aqui — TODO */ }
                )
            }

            FavoritesPreviewPanel(
                focused = focusedFavorite,
                vm = vm,
                modifier = Modifier.weight(2f).fillMaxHeight()
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
    onFocusItem: (FavoriteEntity) -> Unit,
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
                    onFocus = { onFocusItem(f) },
                    onClick = { onPick(f) }
                )
            }
        }
    }
}

/**
 * Linha compacta: thumb de 36dp à esquerda + nome à direita. Foco animado
 * com barra azul lateral — prioriza densidade (ver o máximo de itens de uma
 * vez) sobre o tamanho do card, já que listas de favoritos podem ter
 * dezenas de itens por categoria.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteListItem(
    name: String,
    imageUrl: String?,
    fallbackIcon: ImageVector,
    onFocus: () -> Unit,
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
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
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

/**
 * Painel direito de Favoritos. Para LIVE e MOVIE renderiza o PreviewPlayer
 * (debounce + retry + áudio); para SERIES mostra só o pôster grande, já que
 * tocar um episódio aleatório não faz sentido pro usuário.
 */
@Composable
private fun FavoritesPreviewPanel(
    focused: FavoriteEntity?,
    vm: HomeViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (focused == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.favorites_preview_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }
        when (focused.type) {
            ContentType.SERIES -> {
                // Sem player — pôster grande + título.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        if (!focused.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = focused.logoUrl,
                                contentDescription = focused.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(
                                Icons.Filled.Tv,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(48.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                    Text(
                        focused.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ContentType.LIVE -> {
                val url by produceState<String?>(initialValue = null, focused.itemId) {
                    value = vm.previewUrl(focused.itemId)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewPlayer(streamUrl = url)
                    Text(
                        focused.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ContentType.MOVIE -> {
                val url by produceState<String?>(initialValue = null, focused.itemId) {
                    value = vm.moviePreviewUrl(focused.itemId, focused.containerExtension)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewPlayer(streamUrl = url)
                    Text(
                        focused.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
