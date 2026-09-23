package com.iptv.app.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.ui.common.DrawerCategoryItem
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.FormFactor
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.live.ChannelTile
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

/** Ordem das seções de Favoritos (gaveta na TV, chips no celular). */
private val SECTIONS = listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)

/**
 * Favoritos no mesmo padrão visual de Filmes/Séries/Ao Vivo: gaveta fixa à
 * esquerda com as seções (Canais, Filmes, Séries + contagem) e grade de cards
 * de 95dp à direita, com a busca no topo. No celular, as seções viram chips
 * acima da grade.
 *
 * Antes eram três listas lado a lado com miniaturas de 36dp e um painel de
 * prévia ocupando 40% da largura — destoava das demais telas e a prévia
 * abria stream só de passar o foco.
 *
 * OK abre o item (canal toca, filme abre o detalhe, série abre a série);
 * segurar OK remove dos favoritos.
 */
@Composable
fun FavoritesScreen(
    vm: HomeViewModel,
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit
) {
    val all by vm.favorites.collectAsState()
    val dim = rememberTvDim()
    val snackbar = LocalSnackbar.current
    val removedMsg = stringResource(R.string.snack_favorite_removed)

    if (all.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
        ) {
            // Sem título de página — a aba "Favoritos" já selecionada no menu
            // principal deixa claro onde o usuário está.
            EmptyState(
                title = stringResource(R.string.empty_favorites_title),
                message = stringResource(R.string.empty_favorites_message),
                icon = Icons.Filled.Favorite
            )
        }
        return
    }

    // Sempre alfabético (A→Z) — usuário pediu ordenação fixa.
    val byType = remember(all) {
        SECTIONS.associateWith { type ->
            all.filter { it.type == type }.sortedBy { it.name.lowercase() }
        }
    }
    var selectedType by rememberSaveable { mutableStateOf(ContentType.LIVE) }
    // Seção sem itens (ex.: último favorito removido): cai na primeira que tem.
    val activeType = selectedType.takeIf { byType[it].orEmpty().isNotEmpty() }
        ?: SECTIONS.first { byType[it].orEmpty().isNotEmpty() }
    var localFilter by rememberSaveable { mutableStateOf("") }
    val needle = localFilter.trim().lowercase()
    val visible = byType[activeType].orEmpty()
        .filter { needle.isBlank() || it.name.lowercase().contains(needle) }

    val openItem: (FavoriteEntity) -> Unit = { f ->
        when (f.type) {
            ContentType.LIVE -> onPlay(PlayerArgs(PlayerKind.LIVE, f.itemId, f.name, null))
            ContentType.MOVIE -> onPlay(
                PlayerArgs(
                    kind = PlayerKind.MOVIE,
                    streamId = f.itemId,
                    title = f.name,
                    containerExtension = f.containerExtension,
                    posterUrl = f.logoUrl,
                    categoryId = f.categoryId
                )
            )
            ContentType.SERIES -> onOpenSeries(f.itemId, f.name, f.logoUrl)
        }
    }
    val removeItem: (FavoriteEntity) -> Unit = { f ->
        vm.toggleFavorite(f)
        snackbar?.show(removedMsg)
    }

    if (dim.formFactor == FormFactor.Phone) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                SECTIONS.filter { byType[it].orEmpty().isNotEmpty() }.forEach { type ->
                    TouchableButton(
                        selected = type == activeType,
                        compact = true,
                        onClick = { selectedType = type }
                    ) {
                        Text("${sectionLabel(type)} (${byType[type].orEmpty().size})")
                    }
                }
            }
            LocalFilterField(
                value = localFilter,
                onValueChange = { localFilter = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            FavoritesGrid(
                items = visible,
                columns = GridCells.Adaptive(150.dp),
                spacing = dim.CardSpacing,
                onOpen = openItem,
                onRemove = removeItem,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val drawerSelectedRequester = remember { FocusRequester() }
    // Foco inicial na seção selecionada da gaveta, como em Filmes/Séries.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        runCatching { drawerSelectedRequester.requestFocus() }
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.tab_favorites),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                lazyListItems(SECTIONS) { type ->
                    val isSelected = type == activeType
                    DrawerCategoryItem(
                        name = sectionLabel(type),
                        isSelected = isSelected,
                        isAdult = false,
                        count = byType[type].orEmpty().size,
                        onClick = { selectedType = type },
                        modifier = if (isSelected) {
                            Modifier.focusRequester(drawerSelectedRequester)
                        } else Modifier
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            LocalFilterField(
                value = localFilter,
                onValueChange = { localFilter = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            // Mesmo grid de Filmes/Séries/Ao Vivo: 95dp Adaptive + gap 6.
            FavoritesGrid(
                items = visible,
                columns = GridCells.Adaptive(95.dp),
                spacing = 6.dp,
                onOpen = openItem,
                onRemove = removeItem,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FavoritesGrid(
    items: List<FavoriteEntity>,
    columns: GridCells,
    spacing: androidx.compose.ui.unit.Dp,
    onOpen: (FavoriteEntity) -> Unit,
    onRemove: (FavoriteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
    ) {
        items(items, key = { "${it.type}-${it.itemId}" }) { f ->
            when (f.type) {
                // Canal usa o mesmo tile da grade do Ao Vivo (logo inteira,
                // sem corte, nome na barra inferior).
                ContentType.LIVE -> ChannelTile(
                    channel = LiveChannel(
                        id = f.itemId,
                        num = null,
                        name = f.name,
                        logoUrl = f.logoUrl,
                        categoryId = f.categoryId,
                        epgChannelId = null,
                        addedTimestamp = 0L
                    ),
                    locked = false,
                    isFavorite = false,
                    isPreviewing = false,
                    onClick = { onOpen(f) },
                    onLongClick = { onRemove(f) }
                )
                ContentType.MOVIE, ContentType.SERIES -> PosterCard(
                    title = f.name,
                    imageUrl = f.logoUrl,
                    fallbackIcon = if (f.type == ContentType.MOVIE) Icons.Filled.Movie else Icons.Filled.Tv,
                    fillWidth = true,
                    onLongClick = { onRemove(f) }
                ) { onOpen(f) }
            }
        }
    }
}

@Composable
private fun sectionLabel(type: ContentType): String = stringResource(
    when (type) {
        ContentType.LIVE -> R.string.filter_channels
        ContentType.MOVIE -> R.string.filter_movies
        ContentType.SERIES -> R.string.filter_series
    }
)
