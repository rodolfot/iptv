package com.iptv.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.data.cache.CatalogCacheRepository
import com.iptv.app.data.cache.toDomain
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.model.Series
import com.iptv.app.ui.common.ChannelCard
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResults(
    val channels: List<LiveChannel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList()
)

data class SearchUiState(
    val loadingCatalog: Boolean = false,
    val catalogReady: Boolean = false,
    val error: String? = null,
    val results: SearchResults = SearchResults()
)

enum class SearchFilter { ALL, LIVE, MOVIE, SERIES }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val cache: CatalogCacheRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()

    val history = settings.flow
        .map { it.searchHistory }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var queryJob: Job? = null

    fun rememberSearch(term: String) {
        viewModelScope.launch { settings.pushSearchHistory(term) }
    }

    fun clearHistory() {
        viewModelScope.launch { settings.clearSearchHistory() }
    }

    fun loadCatalog() {
        if (_state.value.catalogReady || _state.value.loadingCatalog) return
        _state.value = _state.value.copy(loadingCatalog = true, error = null)
        viewModelScope.launch {
            val errors = mutableListOf<Throwable>()
            val needsLive = cache.isStale(CatalogCacheRepository.Scope.LIVE_STREAMS)
            val needsMovies = cache.isStale(CatalogCacheRepository.Scope.MOVIE_STREAMS)
            val needsSeries = cache.isStale(CatalogCacheRepository.Scope.SERIES_LIST)
            if (needsLive) cache.refreshLiveStreams().exceptionOrNull()?.let(errors::add)
            if (needsMovies) cache.refreshMovieStreams().exceptionOrNull()?.let(errors::add)
            if (needsSeries) cache.refreshSeriesList().exceptionOrNull()?.let(errors::add)
            // Even with errors, if there is cached data we let search proceed.
            _state.value = _state.value.copy(
                loadingCatalog = false,
                catalogReady = true,
                error = errors.firstOrNull()?.message
            )
        }
    }

    fun retry() {
        _state.value = SearchUiState()
        loadCatalog()
    }

    fun onQueryChanged(raw: String, filter: SearchFilter) {
        queryJob?.cancel()
        val q = raw.trim()
        if (q.length < 2) {
            _state.value = _state.value.copy(results = SearchResults())
            return
        }
        queryJob = viewModelScope.launch {
            delay(200)
            val channels = if (filter == SearchFilter.ALL || filter == SearchFilter.LIVE)
                cache.searchLive(q).map { it.toDomain() }
            else emptyList()
            val movies = if (filter == SearchFilter.ALL || filter == SearchFilter.MOVIE)
                cache.searchMovies(q).map { it.toDomain() }
            else emptyList()
            val series = if (filter == SearchFilter.ALL || filter == SearchFilter.SERIES)
                cache.searchSeries(q).map { it.toDomain() }
            else emptyList()
            val results = SearchResults(channels, movies, series)
            _state.value = _state.value.copy(results = results)
            // NÃO salvamos no histórico aqui. Antes, persistir a cada
            // letra digitada com 600ms de debounce ainda gravava todos
            // os prefixos curtos ("tr", "tra", "tran", "tran"...) porque
            // o cancel da coroutine não desfaz writes já feitos. Agora a
            // tela chama rememberSearch quando o usuário "submete" o
            // termo (sai do modo de edição do campo).
        }
    }
}

@Composable
fun SearchScreen(
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit,
    onOpenChannel: (LiveChannel) -> Unit = { },
    // Estado elevado para HomeScreen — rememberSaveable local não sobrevive
    // à saída/retorno via openSeries (o slot do `when` é destruído). Mantendo
    // aqui, voltar de uma série ou do player preserva termo e filtro.
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    filter: SearchFilter = SearchFilter.ALL,
    onFilterChange: (SearchFilter) -> Unit = {},
    vm: SearchViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val history by vm.history.collectAsState()
    val dim = rememberTvDim()

    LaunchedEffect(Unit) { vm.loadCatalog() }
    LaunchedEffect(query, filter) { vm.onQueryChanged(query, filter) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            enabled = state.catalogReady,
            onSubmit = {
                // Só registra a palavra completa quando ela produziu
                // algum resultado real — evita poluir o histórico com
                // erros de digitação.
                val term = query.trim()
                val hasResults = state.results.channels.isNotEmpty() ||
                    state.results.movies.isNotEmpty() ||
                    state.results.series.isNotEmpty()
                if (term.length >= 2 && hasResults) vm.rememberSearch(term)
            }
        )
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(stringResource(R.string.filter_all), filter == SearchFilter.ALL) { onFilterChange(SearchFilter.ALL) }
            FilterChip(stringResource(R.string.filter_channels), filter == SearchFilter.LIVE) { onFilterChange(SearchFilter.LIVE) }
            FilterChip(stringResource(R.string.filter_movies), filter == SearchFilter.MOVIE) { onFilterChange(SearchFilter.MOVIE) }
            FilterChip(stringResource(R.string.filter_series), filter == SearchFilter.SERIES) { onFilterChange(SearchFilter.SERIES) }
        }

        when {
            state.loadingCatalog -> Text(
                stringResource(R.string.loading_catalog),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error != null && !state.catalogReady ->
                ErrorState(message = state.error!!, onRetry = { vm.retry() })
            query.trim().length < 2 -> PreSearchPanel(
                history = history,
                onPick = onQueryChange,
                onClear = { vm.clearHistory() }
            )
            else -> ResultsContent(state.results, onPlay, onOpenSeries, onOpenChannel)
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit = {}
) {
    // Mesmo padrão D-pad friendly do LocalFilterField/PinField: o foco
    // sozinho NÃO abre o IME. Só ao apertar OK/Enter no controle é que
    // entramos em modo edição (e o teclado aparece). Antes, ao entrar em
    // Buscar o BasicTextField já recebia foco e disparava o teclado.
    var editing by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            editorFocus.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            // Saiu do modo de edição: o usuário "finalizou" o termo.
            // Avisa a tela para persistir a palavra inteira no histórico
            // (a palavra inteira, não cada prefixo digitado).
            onSubmit()
        }
    }

    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (editing) MaterialTheme.colorScheme.primary else Color.Transparent

    // Compactado: era 16/12 + ícone+12 + fontSize 22. Como Buscar tem três
    // seções (canais/filmes/séries) competindo por altura, encolher o campo
    // libera ~30dp por dobra.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp).size(18.dp)
        )
        if (editing) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editorFocus)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Back, Key.Escape -> { editing = false; true }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(if (enabled) R.string.search_hint else R.string.loading_catalog),
                            color = Color(0x99FFFFFF),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    inner()
                }
            )
        } else {
            Text(
                text = query.ifEmpty {
                    stringResource(if (enabled) R.string.search_hint else R.string.loading_catalog)
                },
                color = if (query.isEmpty()) Color(0x99FFFFFF) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (enabled) { editing = true; true } else false
                            }
                            else -> false
                        }
                    }
                    .clickable(enabled = enabled) { editing = true }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // Sem prefixo "•": a cor de preenchimento do TouchableButton(selected)
    // já comunica a seleção — o marcador de texto era redundante.
    TouchableButton(onClick = onClick, compact = true, selected = selected) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ResultsContent(
    results: SearchResults,
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (Int, String, String?) -> Unit,
    onOpenChannel: (LiveChannel) -> Unit
) {
    val empty = results.channels.isEmpty() && results.movies.isEmpty() && results.series.isEmpty()
    if (empty) {
        EmptyState(
            title = stringResource(R.string.empty_search_no_results_title),
            message = stringResource(R.string.empty_search_no_results_message),
            icon = Icons.Filled.Search
        )
        return
    }

    // Cards compactos pra caber as 3 linhas (canais/filmes/séries) sem
    // scroll vertical na primeira dobra. Os tamanhos padrão (Channel 320,
    // Poster 220) consumiam ~700dp em TV.
    val dim = rememberTvDim()
    val posterW = when (dim.formFactor) {
        com.iptv.app.ui.common.FormFactor.Phone -> 90.dp
        com.iptv.app.ui.common.FormFactor.Tablet -> 100.dp
        com.iptv.app.ui.common.FormFactor.Tv -> 110.dp
    }
    val rowGap = 10.dp
    val cardGap = 8.dp
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        if (results.channels.isNotEmpty()) {
            ResultRow("Canais", results.channels.size, Icons.Filled.LiveTv) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(cardGap)) {
                    items(results.channels) { ch ->
                        ChannelMiniCard(
                            title = ch.name,
                            logoUrl = ch.logoUrl,
                            width = posterW
                        ) { onOpenChannel(ch) }
                    }
                }
            }
        }
        if (results.movies.isNotEmpty()) {
            ResultRow("Filmes", results.movies.size, Icons.Filled.Movie) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(cardGap)) {
                    items(results.movies) { m ->
                        PosterCard(
                            title = m.name,
                            imageUrl = m.posterUrl,
                            fallbackIcon = Icons.Filled.Movie,
                            overrideWidth = posterW,
                            compactTitle = true
                        ) {
                            onPlay(
                                PlayerArgs(
                                    kind = PlayerKind.MOVIE,
                                    streamId = m.id,
                                    title = m.name,
                                    containerExtension = m.containerExtension,
                                    posterUrl = m.posterUrl,
                                    categoryId = m.categoryId
                                )
                            )
                        }
                    }
                }
            }
        }
        if (results.series.isNotEmpty()) {
            ResultRow("Séries", results.series.size, Icons.Filled.Tv) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(cardGap)) {
                    items(results.series) { s ->
                        PosterCard(
                            title = s.name,
                            imageUrl = s.coverUrl,
                            fallbackIcon = Icons.Filled.Tv,
                            overrideWidth = posterW,
                            compactTitle = true
                        ) {
                            onOpenSeries(s.id, s.name, s.coverUrl)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card miniatura para canais: logo dentro de uma caixa quadrada (Fit, sem
 * crop) e nome em uma faixa logo abaixo. O PosterCard 2:3 cortava logos
 * largas (ESPN, Discovery) e a faixa do nome ficava em cima do logo.
 */
@Composable
private fun ChannelMiniCard(
    title: String,
    logoUrl: String?,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    com.iptv.app.ui.common.TouchableCard(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.width(width)
    ) {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Filled.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                }
            }
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    count: Int,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text(
                "$label ($count)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
            )
        }
        content()
    }
}

@Composable
private fun PreSearchPanel(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_search_title),
            message = stringResource(R.string.empty_search_message),
            icon = Icons.Filled.Search
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.search_history_title),
                style = MaterialTheme.typography.titleSmall
            )
            TouchableButton(onClick = onClear, compact = true) {
                Text(
                    stringResource(R.string.search_history_clear),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        // Wrap chips in a flow-like LazyRow; on Phone the content scrolls horizontally
        // when there are many entries.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { term ->
                HistoryChip(label = term, onClick = { onPick(term) })
            }
        }
    }
}

@Composable
private fun HistoryChip(label: String, onClick: () -> Unit) {
    TouchableButton(onClick = onClick, compact = true) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text("  $label", style = MaterialTheme.typography.labelMedium)
    }
}
