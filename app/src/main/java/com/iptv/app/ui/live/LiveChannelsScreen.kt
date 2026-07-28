package com.iptv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.data.prefs.AppSettings
import com.iptv.app.data.prefs.LiveViewMode
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.home.HomeViewModel

/**
 * Tela de canais Ao Vivo em TV/Tablet. Suporta 3 layouts (escolhidos nas
 * Configurações de visualização, [LiveViewMode]):
 *  - GRID: categorias à esquerda + grid de canais + PIP de preview no canto
 *    (layout original, inspirado no Smarters Player Lite).
 *  - LIST_WITH_CATEGORIES: categorias | lista de canais | preview.
 *  - LIST_FOCUS: lista de canais | preview ocupando o resto da tela, sem
 *    coluna de categorias (Voltar leva à tela de categorias).
 *
 * Em todos os modos, o preview só começa a tocar quando o usuário confirma
 * com OK/Enter — navegar com o D-pad apenas move o foco, sem disparar stream
 * nenhum. Confirmar de novo o canal já em preview abre em tela cheia.
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
    countByCategory: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val settings by vm.settingsFlow.collectAsState()
    val epgNow by vm.epgNow.collectAsState()
    when (settings.liveViewMode) {
        LiveViewMode.GRID -> LiveGridLayout(
            vm = vm,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            channels = channels,
            isCategoryAdult = isCategoryAdult,
            isParentalUnlocked = isParentalUnlocked,
            loading = loading,
            settings = settings,
            onCategorySelected = onCategorySelected,
            onPlay = onPlay,
            countByCategory = countByCategory,
            modifier = modifier
        )
        LiveViewMode.LIST_WITH_CATEGORIES -> LiveListWithCategoriesLayout(
            vm = vm,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            channels = channels,
            epgNow = epgNow,
            isCategoryAdult = isCategoryAdult,
            isParentalUnlocked = isParentalUnlocked,
            onCategorySelected = onCategorySelected,
            onPlay = onPlay,
            countByCategory = countByCategory,
            modifier = modifier
        )
        LiveViewMode.LIST_FOCUS -> LiveListFocusLayout(
            vm = vm,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            channels = channels,
            epgNow = epgNow,
            isCategoryAdult = isCategoryAdult,
            isParentalUnlocked = isParentalUnlocked,
            onCategorySelected = onCategorySelected,
            onPlay = onPlay,
            countByCategory = countByCategory,
            modifier = modifier
        )
    }
}

@Composable
private fun LiveGridLayout(
    vm: HomeViewModel,
    categories: List<Category>,
    selectedCategoryId: String,
    channels: List<LiveChannel>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    loading: Boolean,
    settings: AppSettings,
    onCategorySelected: (String) -> Unit,
    onPlay: (LiveChannel) -> Unit,
    countByCategory: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val favorites by vm.favorites.collectAsState()
    val snackbar = LocalSnackbar.current
    val favAddedMsg = stringResource(R.string.snack_favorite_added)
    val favRemovedMsg = stringResource(R.string.snack_favorite_removed)

    // Canal confirmado com OK — só ele toca no PIP. Passar o foco por cima
    // dos outros tiles não muda isso (ver onClick do ChannelTile).
    var previewChannel by remember(selectedCategoryId) { mutableStateOf<LiveChannel?>(null) }
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
                countByCategory = countByCategory,
                modifier = Modifier.width(280.dp).fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                // Header com campo de busca + sort + visualização à direita —
                // replica exatamente o header de Filmes/Séries (mesma altura,
                // mesmo alinhamento).
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
                    previewChannelId = previewChannel?.id,
                    firstFocusRequester = firstChannelFocus,
                    onClick = { ch ->
                        val locked = isCategoryAdult && !isParentalUnlocked
                        when {
                            // Conteúdo bloqueado nunca entra em preview — vai
                            // direto pro callback, que é quem mostra o PIN.
                            locked -> onPlay(ch)
                            previewChannel?.id == ch.id -> onPlay(ch)
                            else -> previewChannel = ch
                        }
                    },
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

        // PIP de preview no canto inferior direito — só aparece depois que o
        // usuário confirma um canal com OK (ver onClick do ChannelTile).
        previewChannel?.let { channel ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .width(240.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    stringResource(R.string.live_preview_confirm_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                Box(
                    modifier = Modifier
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
}

/** Categorias | lista de canais | preview — 3 colunas lado a lado. */
@Composable
private fun LiveListWithCategoriesLayout(
    vm: HomeViewModel,
    categories: List<Category>,
    selectedCategoryId: String,
    channels: List<LiveChannel>,
    epgNow: Map<String, com.iptv.app.data.db.EpgProgrammeEntity>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    onCategorySelected: (String) -> Unit,
    onPlay: (LiveChannel) -> Unit,
    countByCategory: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val drawerSelectedRequester = remember { FocusRequester() }
    LaunchedEffect(selectedCategoryId) {
        kotlinx.coroutines.delay(50)
        runCatching { drawerSelectedRequester.requestFocus() }
    }
    Row(modifier = modifier.fillMaxSize()) {
        CategoriesDrawer(
            categories = categories,
            selectedId = selectedCategoryId,
            onSelect = onCategorySelected,
            selectedRequester = drawerSelectedRequester,
            countByCategory = countByCategory,
            modifier = Modifier.width(280.dp).fillMaxHeight()
        )
        ChannelListWithEpg(
            vm = vm,
            channels = channels,
            epgNow = epgNow,
            isCategoryAdult = isCategoryAdult,
            isParentalUnlocked = isParentalUnlocked,
            onPlay = onPlay,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)
        )
    }
}

/**
 * Lista de canais | preview ocupando o resto da tela, sem coluna de
 * categorias. Voltar no controle alterna para um seletor de categoria em
 * tela cheia (reaproveita o [CategoriesDrawer]).
 */
@Composable
private fun LiveListFocusLayout(
    vm: HomeViewModel,
    categories: List<Category>,
    selectedCategoryId: String,
    channels: List<LiveChannel>,
    epgNow: Map<String, com.iptv.app.data.db.EpgProgrammeEntity>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    onCategorySelected: (String) -> Unit,
    onPlay: (LiveChannel) -> Unit,
    countByCategory: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler {
        showCategoryPicker = !showCategoryPicker
    }

    if (showCategoryPicker) {
        val pickerRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(50)
            runCatching { pickerRequester.requestFocus() }
        }
        CategoriesDrawer(
            categories = categories,
            selectedId = selectedCategoryId,
            onSelect = { catId ->
                onCategorySelected(catId)
                showCategoryPicker = false
            },
            selectedRequester = pickerRequester,
            countByCategory = countByCategory,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val categoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name.orEmpty()
    ChannelListWithEpg(
        vm = vm,
        channels = channels,
        epgNow = epgNow,
        isCategoryAdult = isCategoryAdult,
        isParentalUnlocked = isParentalUnlocked,
        onPlay = onPlay,
        header = {
            Text(
                categoryName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        },
        modifier = modifier.fillMaxSize().padding(16.dp)
    )
}

@Composable
private fun CategoriesDrawer(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    selectedRequester: FocusRequester,
    countByCategory: Map<String, Int> = emptyMap(),
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
            stringResource(R.string.section_categories),
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
                    count = countByCategory[cat.id],
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
    previewChannelId: Int?,
    firstFocusRequester: FocusRequester,
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
                isPreviewing = previewChannelId == channel.id,
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier),
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
    isPreviewing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Mesmo formato dos cards de Filmes/Séries: aspect 2:3 com nome em barra
    // preta na base. Marquee quando focado (canais como "Globo SP HD ★").
    com.iptv.app.ui.common.TouchableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (isPreviewing) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
        ) {
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
            // Indica que este é o canal confirmado com OK (o que está no PIP).
            if (isPreviewing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 36.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
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
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    channel.name,
                    color = Color.White,
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
    // O canal já chega aqui confirmado com OK — sem necessidade de debounce
    // adicional (usuário demonstrou intenção explícita ao confirmar).
    LaunchedEffect(channel.id) {
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
