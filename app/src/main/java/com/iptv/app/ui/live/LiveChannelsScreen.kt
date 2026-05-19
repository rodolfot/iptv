@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil.compose.AsyncImage
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.home.HomeViewModel

/**
 * Layout inspirado no Smarters Player Lite:
 *  - Coluna esquerda: lista de categorias (sempre visível).
 *  - Área central/direita: grid de canais com logo grande + nome embaixo.
 *  - Canto inferior direito: PIP de preview do canal focado.
 *
 * OK no canal → abre player full-screen.
 * Long-press → toggle favorito.
 */
@Composable
fun LiveChannelsScreen(
    vm: HomeViewModel,
    categories: List<Category>,
    selectedCategoryId: String,
    channels: List<LiveChannel>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    /** Loading do fetch atual — controla o spinner inline. */
    loading: Boolean = false,
    onCategorySelected: (String) -> Unit,
    onPlay: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites by vm.favorites.collectAsState()
    val snackbar = LocalSnackbar.current
    val favAddedMsg = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.snack_favorite_added)
    val favRemovedMsg = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.snack_favorite_removed)

    var focusedChannel by remember(selectedCategoryId) { mutableStateOf<LiveChannel?>(null) }
    val firstChannelFocus = remember { FocusRequester() }
    val drawerSelectedRequester = remember { FocusRequester() }
    // Sem key: a busca persiste ao voltar de um canal e ao trocar de categoria.
    var localFilter by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf("")
    }

    // Foco inicial na 1ª categoria selecionada do drawer ao entrar — antes
    // o foco caía direto no primeiro canal do grid.
    LaunchedEffect(selectedCategoryId) {
        kotlinx.coroutines.delay(50)
        runCatching { drawerSelectedRequester.requestFocus() }
    }

    val needle = localFilter.trim().lowercase()
    val filteredChannels = if (needle.isBlank()) channels
    else channels.filter { it.name.lowercase().contains(needle) }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Painel esquerdo: lista de categorias permanente.
            CategoriesDrawer(
                categories = categories,
                selectedId = selectedCategoryId,
                onSelect = onCategorySelected,
                selectedRequester = drawerSelectedRequester,
                modifier = Modifier.width(280.dp).fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                // Header com campo de busca + sort à direita — replica
                // exatamente o header de Filmes/Séries (mesma altura, mesmo
                // alinhamento). Sem o sort, o header ficava mais curto e
                // desalinhava as categorias com o grid.
                val settings by vm.settingsFlow.collectAsState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                ) {
                    com.iptv.app.ui.common.LocalFilterField(
                        value = localFilter,
                        onValueChange = { localFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                    com.iptv.app.ui.common.SortMenuButton(
                        current = settings.liveSort,
                        options = com.iptv.app.domain.sort.SortOption.LIVE_OPTIONS,
                        onSelect = { vm.setSort(com.iptv.app.data.prefs.SortScope.LIVE, it) }
                    )
                }

                if (loading && filteredChannels.isEmpty()) {
                    com.iptv.app.ui.common.InlineLoading()
                    return@Column
                }
                // Grid de canais com logos grandes.
                ChannelsGrid(
                    channels = filteredChannels,
                    isCategoryAdult = isCategoryAdult,
                    isParentalUnlocked = isParentalUnlocked,
                    favoriteIds = favorites
                        .filter { it.type == com.iptv.app.domain.model.ContentType.LIVE }
                        .map { it.itemId }
                        .toSet(),
                    firstFocusRequester = firstChannelFocus,
                    onFocusedChannelChanged = { focusedChannel = it },
                    onClick = onPlay,
                    onLongClick = { ch ->
                        val isFav = favorites.any {
                            it.type == com.iptv.app.domain.model.ContentType.LIVE && it.itemId == ch.id
                        }
                        vm.toggleFavorite(
                            com.iptv.app.data.db.FavoriteEntity(
                                profileId = "",
                                type = com.iptv.app.domain.model.ContentType.LIVE,
                                itemId = ch.id,
                                name = ch.name,
                                logoUrl = ch.logoUrl,
                                categoryId = ch.categoryId,
                                containerExtension = null
                            )
                        )
                        snackbar?.show(if (isFav) favRemovedMsg else favAddedMsg)
                    },
                    scrollKey = "live-grid:$selectedCategoryId",
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } // end Column
        }

        // PIP de preview no canto inferior direito.
        focusedChannel?.let { channel ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .width(240.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                ChannelPreviewPip(channel = channel, vm = vm)
            }
        }
    }
}

@Composable
private fun CategoriesDrawer(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    selectedRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) {
        val idx = categories.indexOfFirst { it.id == selectedId }
        if (idx >= 0) runCatching { listState.scrollToItem(idx) }
    }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
    ) {
        // Drawer fixo — sem botão fechar. Igual ao Filmes/Séries. Ao Vivo
        // não tem "voltar pro grid de categorias"; a navegação é só entre
        // as categorias e os canais.
        Text(
            androidx.compose.ui.res.stringResource(com.iptv.app.R.string.section_categories),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            lazyListItems(categories) { cat ->
                val isSelected = cat.id == selectedId
                com.iptv.app.ui.common.DrawerCategoryItem(
                    name = cat.name,
                    isSelected = isSelected,
                    isAdult = cat.isAdult,
                    onClick = { onSelect(cat.id) },
                    modifier = if (isSelected) Modifier.focusRequester(selectedRequester) else Modifier
                )
            }
        }
    }
}

@Composable
private fun ChannelsGrid(
    channels: List<LiveChannel>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    favoriteIds: Set<Int>,
    firstFocusRequester: FocusRequester,
    onFocusedChannelChanged: (LiveChannel?) -> Unit,
    onClick: (LiveChannel) -> Unit,
    onLongClick: (LiveChannel) -> Unit,
    scrollKey: String,
    modifier: Modifier = Modifier
) {
    val locked = isCategoryAdult && !isParentalUnlocked
    // Preserva scroll por categoria — usuário volta do player Live e cai onde
    // estava na lista, não no topo.
    var savedFirstIndex by androidx.compose.runtime.saveable.rememberSaveable(scrollKey) {
        androidx.compose.runtime.mutableStateOf(0)
    }
    var savedFirstOffset by androidx.compose.runtime.saveable.rememberSaveable(scrollKey) {
        androidx.compose.runtime.mutableStateOf(0)
    }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = savedFirstIndex,
        initialFirstVisibleItemScrollOffset = savedFirstOffset,
    )
    androidx.compose.runtime.LaunchedEffect(gridState) {
        androidx.compose.runtime.snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (idx, off) ->
            savedFirstIndex = idx
            savedFirstOffset = off
        }
    }
    // Mesmo grid de Filmes/Séries: 95dp Adaptive + gap 6 + contentPadding
    // só vertical (o padding lateral é absorvido pelo Column pai).
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(95.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        items(items = channels, key = { it.id }) { channel ->
            val index = channels.indexOf(channel)
            ChannelTile(
                channel = channel,
                locked = locked,
                isFavorite = channel.id in favoriteIds,
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocusedChannelChanged(channel) },
                onClick = { onClick(channel) },
                onLongClick = { onLongClick(channel) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(
    channel: LiveChannel,
    locked: Boolean,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Mesmo formato dos cards de Filmes/Séries: aspect 2:3 com nome em barra
    // preta na base. Marquee quando focado (canais como "Globo SP HD ★").
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // Logo centralizada na metade superior. Mantenho padding generoso
            // — logos vêm em formatos heterogêneos e cortar fica feio.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Filled.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                }
            }
            if (isFavorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
            // Barra preta com nome — espelha a do PosterCard.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    channel.name,
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    else Modifier
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ChannelPreviewPip(channel: LiveChannel, vm: HomeViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exo = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            volume = 1f
            playWhenReady = true
        }
    }
    DisposableEffect(exo) {
        val listener = object : androidx.media3.common.Player.Listener {
            private var attempts = 0
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (attempts >= 5) return
                attempts++
                exo.prepare()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) attempts = 0
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    LaunchedEffect(channel.id) {
        kotlinx.coroutines.delay(600)
        val url = vm.previewUrl(channel.id) ?: return@LaunchedEffect
        exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            channel.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
