package com.iptv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.iptv.app.ui.common.CatalogLoadingScreen
import com.iptv.app.ui.common.FormFactor
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.continueWatching.ContinueWatchingScreen
import com.iptv.app.ui.favorites.FavoritesScreen
import com.iptv.app.ui.live.ChannelDetailScreen
import com.iptv.app.ui.live.LiveSection
import com.iptv.app.ui.movies.MovieDetailScreen
import com.iptv.app.ui.movies.MoviesSection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.LocalPlaybackHolder
import com.iptv.app.ui.player.MiniPlayer
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.search.SearchScreen
import com.iptv.app.ui.series.SeriesDetailScreen
import com.iptv.app.ui.series.SeriesSection
import com.iptv.app.ui.settings.SettingsScreen
import com.iptv.app.ui.update.UpdatePromptHost
import com.iptv.app.ui.watchlist.WatchlistScreen

private data class TabSpec(val label: Int, val key: String)

private val ALL_TABS = listOf(
    TabSpec(com.iptv.app.R.string.tab_home, "home"),
    TabSpec(com.iptv.app.R.string.tab_search, "search"),
    TabSpec(com.iptv.app.R.string.tab_live, "live"),
    TabSpec(com.iptv.app.R.string.tab_movies, "movies"),
    TabSpec(com.iptv.app.R.string.tab_series, "series"),
    TabSpec(com.iptv.app.R.string.tab_favorites, "favorites"),
    TabSpec(com.iptv.app.R.string.tab_watchlist, "watchlist"),
    TabSpec(com.iptv.app.R.string.tab_settings, "settings")
)

/** Search and Settings are hidden in Kids mode — children shouldn't be able to
 *  edit credentials or jump to arbitrary content via free-text search. */
private val KIDS_HIDDEN = setOf("search", "settings", "watchlist")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlay: (PlayerArgs) -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val kidsMode by vm.kidsMode.collectAsState()
    val headerBackController = remember { com.iptv.app.ui.common.HeaderBackController() }
    val visibleTabs = remember(kidsMode) {
        if (kidsMode) ALL_TABS.filter { it.key !in KIDS_HIDDEN } else ALL_TABS
    }
    var selectedKey by rememberSaveable { mutableStateOf(visibleTabs.first().key) }
    // If the user just entered Kids mode and was on a hidden tab, snap back to home.
    androidx.compose.runtime.LaunchedEffect(kidsMode) {
        if (visibleTabs.none { it.key == selectedKey }) {
            selectedKey = visibleTabs.first().key
        }
    }
    val parental = remember { ParentalSession() }
    var openSeries by remember { mutableStateOf<Triple<Int, String, String?>?>(null) }
    var openMovie by remember { mutableStateOf<PlayerArgs?>(null) }
    var openChannel by remember { mutableStateOf<com.iptv.app.domain.model.LiveChannel?>(null) }
    // Elevado para sobreviver à entrada/saída do MovieDetailScreen e do player.
    // Antes ficava dentro de MoviesSection com rememberSaveable, mas o `when` de
    // detail/aba desmontava o composable e o estado se perdia ao voltar do
    // player — o usuário caía no grid de categorias.
    var moviesSelectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesSelectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    val initialLoading by vm.initialLoading.collectAsState()
    val playbackHolder = LocalPlaybackHolder.current
    val miniPlayerFormFactor = com.iptv.app.ui.common.rememberTvDim().formFactor
    // TV/Tablet have no D-pad-reachable dock for the mini-player, and audio
    // bleeding into the menus is more annoying than convenient — only phones
    // get the strip.
    val showMiniPlayer = playbackHolder?.minimized?.value == true &&
        playbackHolder.player != null &&
        miniPlayerFormFactor == com.iptv.app.ui.common.FormFactor.Phone

    // Exit confirmation: o back na raiz da Home (sem detalhe aberto e sem
    // back-stack interno de seção) pede confirmação antes de fechar o app.
    // Confirmando, libera o player — sem isso o áudio às vezes continua
    // tocando porque o onDestroy do MainActivity nem sempre é disparado
    // (Android pode segurar o processo ao recolher para o launcher).
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    val canHandleBack = openMovie == null &&
        openChannel == null &&
        openSeries == null &&
        headerBackController.sectionBack.value == null
    BackHandler(enabled = canHandleBack) { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(com.iptv.app.R.string.exit_title)) },
            text = { Text(stringResource(com.iptv.app.R.string.exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    playbackHolder?.release()
                    (context as? Activity)?.finish()
                }) { Text(stringResource(com.iptv.app.R.string.exit_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(com.iptv.app.R.string.exit_no))
                }
            }
        )
    }

    LaunchedEffect(Unit) { vm.bootstrapCatalog() }

    if (initialLoading) {
        CatalogLoadingScreen()
        return
    }

    // Movies open the detail screen first; other kinds (live, episode) play immediately.
    val handlePlay: (PlayerArgs) -> Unit = { args ->
        if (args.kind == com.iptv.app.ui.player.PlayerKind.MOVIE && args.startPositionMs == 0L) {
            openMovie = args
        } else {
            onPlay(args)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.iptv.app.ui.common.LocalHeaderBack provides headerBackController
    ) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UpdatePromptHost()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            // When a detail screen is open, surface its Back action up here in
            // the top bar (next to the logo) so the action lives in a stable
            // place instead of floating loose inside each detail.
            // SeriesDetail has its own nested back (season -> series root), so
            // it keeps its in-screen back button; we only hoist Back to the
            // header for the flat detail screens.
            // Detail screens take precedence over section-internal back state
            // — closing the detail must always pop the detail first.
            val sectionBack = headerBackController.sectionBack.value
            val headerBack: (() -> Unit)? = when {
                openMovie != null -> ({ openMovie = null })
                openChannel != null -> ({ openChannel = null })
                sectionBack != null -> sectionBack
                else -> null
            }
            TopBar(
                tabs = visibleTabs,
                selectedKey = selectedKey,
                onSelected = {
                    // Tapping a tab must take the user out of any open detail
                    // — otherwise switching from "Movies → ${movie}" to Series
                    // keeps the movie detail visible because the detail layer
                    // wins over the tab-content layer in the `when` below.
                    openMovie = null
                    openChannel = null
                    openSeries = null
                    selectedKey = it
                },
                onBack = headerBack
            )
            when {
                openMovie != null -> MovieDetailScreen(
                    args = openMovie!!,
                    // Não limpa o detalhe ao mandar pro player: assim, voltar
                    // do player retorna pro detalhe (mesmo onde o usuário
                    // clicou Assistir). Segundo Voltar fecha o detalhe e cai
                    // na lista/origem. Antes limpávamos, e ao voltar caía
                    // direto na origem — inconsistente com a UX de TV.
                    onPlay = onPlay,
                    onBack = { openMovie = null }
                )
                openChannel != null -> ChannelDetailScreen(
                    channel = openChannel!!,
                    onBack = { openChannel = null },
                    onPlay = onPlay
                )
                openSeries != null -> {
                    val (id, title, cover) = openSeries!!
                    SeriesDetailScreen(
                        seriesId = id,
                        title = title,
                        coverUrl = cover,
                        onBack = { openSeries = null },
                        // Mesmo padrão do filme: ao voltar do player o usuário
                        // cai no detalhe da série; segundo Voltar fecha o
                        // detalhe e vai pra lista/origem.
                        onPlay = onPlay
                    )
                }
                else -> when (selectedKey) {
                    "home" -> ContinueWatchingScreen(
                        onPlay = onPlay,
                        // Cards recomendados de filmes abrem a tela de
                        // detalhe (sinopse + botão Assistir). Continue
                        // Watching continua tocando direto.
                        onOpenMovie = handlePlay,
                        onOpenSeries = { id, title, cover -> openSeries = Triple(id, title, cover) }
                    )
                    "search" -> SearchScreen(
                        onPlay = handlePlay,
                        onOpenSeries = { id, title, cover -> openSeries = Triple(id, title, cover) },
                        onOpenChannel = { ch -> openChannel = ch }
                    )
                    "live" -> LiveSection(
                        vm = vm,
                        parental = parental,
                        onPlay = onPlay
                    )
                    "movies" -> MoviesSection(
                        vm = vm,
                        parental = parental,
                        onPlay = handlePlay,
                        onPlayDirect = onPlay,
                        selectedCat = moviesSelectedCat,
                        onSelectedCatChange = { moviesSelectedCat = it }
                    )
                    "series" -> SeriesSection(
                        vm = vm,
                        onPlay = onPlay,
                        selectedCat = seriesSelectedCat,
                        onSelectedCatChange = { seriesSelectedCat = it }
                    )
                    "favorites" -> FavoritesScreen(vm = vm, parental = parental, onPlay = handlePlay)
                    "watchlist" -> WatchlistScreen(
                        vm = vm,
                        onPlay = handlePlay,
                        onOpenSeries = { id, title, cover -> openSeries = Triple(id, title, cover) }
                    )
                    "settings" -> SettingsScreen(vm = vm, onLogout = onLogout)
                }
            }
        }
        if (showMiniPlayer && playbackHolder != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // MiniPlayer lives outside the Column that owns the
                    // safeDrawing insets, so it must claim them itself —
                    // otherwise it slides under the gesture bar / notch.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
            ) {
                MiniPlayer(
                    holder = playbackHolder,
                    onExpand = {
                        playbackHolder.args.value?.let { onPlay(it) }
                    }
                )
            }
        }
    }
    } // CompositionLocalProvider(LocalHeaderBack)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(
    tabs: List<TabSpec>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val dim = rememberTvDim()
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    if (dim.formFactor == FormFactor.Phone) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                // `safeDrawing.only(Top)` covers the camera cutout / Dynamic Island,
                // not just the basic status bar (`WindowInsets.statusBars` leaves
                // notches uncovered on some manufacturers' edge-to-edge layouts).
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dim.ScreenPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) {
                    // Centralised back button: the detail screens hide their
                    // own button and surface it here so the user always finds
                    // the action in the same spot.
                    com.iptv.app.ui.common.TouchableButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(com.iptv.app.R.string.back)
                        )
                    }
                }
                Image(
                    painter = painterResource(com.iptv.app.R.drawable.app_banner),
                    contentDescription = stringResource(com.iptv.app.R.string.app_name),
                    modifier = Modifier.height(28.dp)
                )
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dim.ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabs) { _, spec ->
                    PhoneTab(
                        label = stringResource(spec.label),
                        selected = spec.key == selectedKey,
                        onClick = { onSelected(spec.key) }
                    )
                }
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dim.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onBack != null) {
            com.iptv.app.ui.common.TouchableButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(com.iptv.app.R.string.back)
                )
            }
        }
        Text(
            stringResource(com.iptv.app.R.string.app_name),
            style = MaterialTheme.typography.titleLarge
        )
        // Dois indicadores distintos:
        //  - Selecionado (após OK): pílula azul preenchida (mostra qual menu
        //    está realmente ativo, mesmo quando o foco está em outro lugar).
        //  - Focado (navegando com D-pad, ainda não confirmou): contorno
        //    azul vazado, fundo transparente.
        // Antes só o foco era destacado e a seleção real ficava invisível.
        var focusedIndex by remember(selectedIndex) { mutableStateOf(selectedIndex) }
        // Quando o TabRow recebe foco vindo do conteúdo abaixo (D-pad pra
        // cima), o sistema cai no último Tab focado anteriormente — não no
        // selecionado. Forçamos o foco a voltar para o Tab ativo via
        // FocusRequester, reentrando no menu correto.
        val tabRequesters = remember(tabs.size) {
            List(tabs.size) { androidx.compose.ui.focus.FocusRequester() }
        }
        var tabRowHadFocus by remember { mutableStateOf(false) }
        val primary = androidx.tv.material3.MaterialTheme.colorScheme.primary
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier
                .padding(start = 16.dp)
                .onFocusChanged { state ->
                    if (state.hasFocus && !tabRowHadFocus) {
                        // Reentrando: alinha o foco visual no selecionado.
                        focusedIndex = selectedIndex
                        runCatching { tabRequesters[selectedIndex].requestFocus() }
                    }
                    tabRowHadFocus = state.hasFocus
                },
            indicator = { tabPositions, doesTabRowHaveFocus ->
                // Camada 1: pílula preenchida no item REALMENTE selecionado.
                tabPositions.getOrNull(selectedIndex)?.let { pos ->
                    androidx.tv.material3.TabRowDefaults.PillIndicator(
                        currentTabPosition = pos,
                        activeColor = primary,
                        inactiveColor = primary.copy(alpha = 0.6f),
                        doesTabRowHaveFocus = doesTabRowHaveFocus
                    )
                }
                // Camada 2: contorno no item em FOCO quando difere do
                // selecionado. Quando coincidem a pílula já dá o feedback.
                if (doesTabRowHaveFocus && focusedIndex != selectedIndex) {
                    tabPositions.getOrNull(focusedIndex)?.let { pos ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart)
                                .offset(x = pos.left, y = pos.top)
                                .width(pos.width)
                                .height(pos.height)
                                .border(
                                    width = 2.dp,
                                    color = primary,
                                    shape = RoundedCornerShape(50)
                                )
                        )
                    }
                }
            }
        ) {
            tabs.forEachIndexed { index, spec ->
                val isSelected = spec.key == selectedKey
                val isFocusedOnly = focusedIndex == index && !isSelected
                Tab(
                    selected = isSelected,
                    onFocus = { focusedIndex = index },
                    onClick = { onSelected(spec.key) },
                    modifier = Modifier.focusRequester(tabRequesters[index]),
                    // Contraste de texto por estado:
                    //  - selecionado (pílula azul preenchida) → branco;
                    //  - apenas focado (contorno) → azul;
                    //  - inativo → cinza.
                    colors = androidx.tv.material3.TabDefaults.pillIndicatorTabColors(
                        contentColor = if (isFocusedOnly) primary
                            else androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveContentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContentColor = androidx.tv.material3.MaterialTheme.colorScheme.onPrimary,
                        focusedContentColor = if (isSelected)
                            androidx.tv.material3.MaterialTheme.colorScheme.onPrimary
                            else primary,
                        focusedSelectedContentColor = androidx.tv.material3.MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        stringResource(spec.label),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
