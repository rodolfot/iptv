package com.iptv.app.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.material3.Icon
import coil.compose.AsyncImage
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import com.iptv.app.data.api.SeriesInfoDetail
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.EpisodeProgressEntity
import com.iptv.app.data.db.FavoriteDao
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.data.db.WatchlistDao
import com.iptv.app.data.db.WatchlistEntity
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.model.Episode
import com.iptv.app.domain.model.Season
import com.iptv.app.domain.model.sortedForDisplay
import com.iptv.app.domain.model.toModel
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.AdvancedFilters
import com.iptv.app.ui.common.AdvancedFiltersDialog
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.PullToRefreshBox
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.parseYear
import com.iptv.app.ui.common.rememberTvDim
import kotlinx.coroutines.flow.first
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetail(
    val seriesId: Int,
    val title: String,
    val seasons: List<Season>,
    val episodesBySeason: Map<Int, List<Episode>>,
    val resume: SeriesResume? = null,
    val watchedEpisodes: Set<String> = emptySet(),
    val episodePercents: Map<String, Int> = emptyMap(),
    val info: SeriesInfoDetail? = null,
    val isInWatchlist: Boolean = false,
    val isFavorite: Boolean = false,
    val coverUrl: String? = null
)

data class SeriesResume(
    val episode: Episode,
    val positionMs: Long,
    val percent: Int
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val episodeProgressDao: EpisodeProgressDao,
    private val seriesProgressDao: SeriesProgressDao,
    private val watchlistDao: WatchlistDao,
    private val favoriteDao: FavoriteDao,
    private val currentProfile: CurrentProfile
) : ViewModel() {
    private val _state = MutableStateFlow<SeriesDetail?>(null)
    val state = _state.asStateFlow()

    fun load(id: Int, title: String, coverUrl: String? = null) {
        viewModelScope.launch {
            runCatching { repo.seriesInfo(id) }
                .onSuccess { resp ->
                    val seasons = resp.seasons?.map { it.toModel() } ?: emptyList()
                    // Providers are inconsistent: some put each season under its
                    // own key ("1" → S1 episodes, "5" → S5 episodes), others
                    // dump everything under "0" or under a single key and rely
                    // on the per-episode `season` field. Group by the episode's
                    // own season number (falling back to the map key) so
                    // misplaced episodes still land in the right bucket and
                    // seasons stop appearing "empty".
                    val episodesMap = resp.normalizedEpisodes()
                        .flatMap { (key, list) ->
                            val fallback = key.toIntOrNull() ?: 0
                            list.map { it.toModel(id, fallback) }
                        }
                        .groupBy { it.seasonNumber }
                        .mapValues { (_, eps) -> eps.sortedBy { it.episodeNum } }
                    // Mantém todas as temporadas declaradas pelo provedor,
                    // mesmo quando ele não devolveu os episódios. O usuário
                    // viu na lista do provedor que a temporada existe — se
                    // escondemos, parece um bug. O card mostra "sem
                    // episódios disponíveis" quando ela está vazia.
                    val declaredSeasonNumbers = (seasons.map { it.seasonNumber } +
                        episodesMap.keys).distinct().sorted()
                    val mergedSeasons = declaredSeasonNumbers.map { sn ->
                        val fromApi = seasons.firstOrNull { it.seasonNumber == sn }
                        Season(
                            seasonNumber = sn,
                            name = fromApi?.name ?: "Temporada $sn",
                            coverUrl = fromApi?.coverUrl,
                            episodeCount = episodesMap[sn]?.size ?: (fromApi?.episodeCount ?: 0)
                        )
                    }

                    val pid = currentProfile.id()
                    val progressBySeries = episodeProgressDao.getBySeries(pid, id)
                    val watched = progressBySeries.filter { it.watched }.map { it.episodeId }.toSet()
                    val percents = progressBySeries
                        .filter { !it.watched && it.durationMs > 0 }
                        .associate { it.episodeId to ((it.positionMs * 100L) / it.durationMs).toInt().coerceIn(0, 100) }

                    val resume = buildResume(pid, id, episodesMap, progressBySeries)
                    val inWatchlist = runCatching {
                        watchlistDao.observeAll(pid).first()
                            .any { it.type == ContentType.SERIES && it.itemId == id }
                    }.getOrDefault(false)
                    val isFav = runCatching {
                        favoriteDao.observeAll(pid).first()
                            .any { it.type == ContentType.SERIES && it.itemId == id }
                    }.getOrDefault(false)

                    _state.value = SeriesDetail(
                        seriesId = id,
                        title = title,
                        seasons = mergedSeasons,
                        episodesBySeason = episodesMap,
                        resume = resume,
                        watchedEpisodes = watched,
                        episodePercents = percents,
                        info = resp.info,
                        isInWatchlist = inWatchlist,
                        isFavorite = isFav,
                        coverUrl = coverUrl
                    )
                }
        }
    }

    fun toggleWatchlist() {
        val current = _state.value ?: return
        viewModelScope.launch {
            val pid = currentProfile.id()
            if (current.isInWatchlist) {
                watchlistDao.delete(pid, ContentType.SERIES, current.seriesId)
            } else {
                watchlistDao.insert(
                    WatchlistEntity(
                        profileId = pid,
                        type = ContentType.SERIES,
                        itemId = current.seriesId,
                        name = current.title,
                        logoUrl = current.coverUrl,
                        categoryId = null,
                        containerExtension = null
                    )
                )
            }
            _state.value = current.copy(isInWatchlist = !current.isInWatchlist)
        }
    }

    fun toggleFavorite() {
        val current = _state.value ?: return
        viewModelScope.launch {
            val pid = currentProfile.id()
            if (current.isFavorite) {
                favoriteDao.delete(pid, ContentType.SERIES, current.seriesId)
            } else {
                favoriteDao.insert(
                    FavoriteEntity(
                        profileId = pid,
                        type = ContentType.SERIES,
                        itemId = current.seriesId,
                        name = current.title,
                        logoUrl = current.coverUrl,
                        categoryId = null,
                        containerExtension = null
                    )
                )
            }
            _state.value = current.copy(isFavorite = !current.isFavorite)
        }
    }

    private suspend fun buildResume(
        profileId: String,
        seriesId: Int,
        episodesMap: Map<Int, List<Episode>>,
        progress: List<EpisodeProgressEntity>
    ): SeriesResume? {
        // Priority: in-progress episode (latest by updatedAt). Otherwise, next unwatched
        // episode after the last one the user watched (from SeriesProgress).
        val inProgress = progress
            .filter { !it.watched && it.positionMs > 0 }
            .maxByOrNull { it.updatedAt }
        if (inProgress != null) {
            val ep = findEpisode(episodesMap, inProgress.episodeId)
            if (ep != null) {
                val pct = if (inProgress.durationMs > 0)
                    ((inProgress.positionMs * 100L) / inProgress.durationMs).toInt().coerceIn(0, 100)
                else 0
                return SeriesResume(ep, inProgress.positionMs, pct)
            }
        }
        val seriesLast: SeriesProgressEntity? = seriesProgressDao.getById(profileId, seriesId)
        if (seriesLast != null) {
            val nextAfter = nextEpisodeAfter(
                episodesMap,
                seriesLast.lastSeasonNumber,
                seriesLast.lastEpisodeNum
            )
            if (nextAfter != null) return SeriesResume(nextAfter, 0L, 0)
        }
        return null
    }

    private fun findEpisode(map: Map<Int, List<Episode>>, episodeId: String): Episode? =
        map.values.flatten().firstOrNull { it.id == episodeId }

    private fun nextEpisodeAfter(
        map: Map<Int, List<Episode>>,
        season: Int,
        episodeNum: Int
    ): Episode? {
        val flat = map.entries
            .sortedBy { it.key }
            .flatMap { (s, list) -> list.sortedBy { it.episodeNum }.map { s to it } }
        val idx = flat.indexOfFirst { it.first == season && it.second.episodeNum == episodeNum }
        if (idx < 0) return null
        return flat.getOrNull(idx + 1)?.second
    }
}

@Composable
fun SeriesSection(
    vm: HomeViewModel,
    onPlay: (PlayerArgs) -> Unit,
    selectedCat: String? = null,
    onSelectedCatChange: (String?) -> Unit = {}
) {
    val rawCats by vm.seriesCategories.collectAsState()
    val series by vm.series.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val kidsAllowed by vm.kidsAllowedCategories.collectAsState()
    val kidsActive by vm.kidsMode.collectAsState()
    val cats = when {
        !kidsActive -> rawCats
        kidsAllowed.isNotEmpty() -> rawCats.copy(
            items = rawCats.items.filter { "series:${it.id}" in kidsAllowed }
        )
        else -> rawCats.copy(items = rawCats.items.filter { !it.isAdult })
    }
    val dim = rememberTvDim()
    var openSeries by remember { mutableStateOf<Triple<Int, String, String?>?>(null) }
    var localFilter by rememberSaveable(selectedCat) { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var advancedFilters by remember(selectedCat) { mutableStateOf(AdvancedFilters()) }
    var filtersDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadSeriesCategories()
    }
    // TV/Tablet: pula o grid de categorias e abre direto a primeira.
    val isTvLike = dim.formFactor != com.iptv.app.ui.common.FormFactor.Phone
    LaunchedEffect(cats.items, isTvLike) {
        if (isTvLike && selectedCat == null && cats.items.isNotEmpty()) {
            val first = cats.items.sortedForDisplay().firstOrNull() ?: cats.items.first()
            onSelectedCatChange(first.id)
            vm.loadSeries(first.id)
        }
    }
    androidx.activity.compose.BackHandler(enabled = !isTvLike && selectedCat != null && openSeries == null) {
        onSelectedCatChange(null)
    }
    if (!isTvLike && selectedCat != null && openSeries == null) {
        com.iptv.app.ui.common.RegisterHeaderBack { onSelectedCatChange(null) }
    }

    if (openSeries != null) {
        SeriesDetailScreen(
            seriesId = openSeries!!.first,
            title = openSeries!!.second,
            coverUrl = openSeries!!.third,
            onBack = { openSeries = null },
            // Mantém o detalhe aberto ao mandar pro player. Ao voltar do
            // player o usuário cai no detalhe (mesmo onde clicou Assistir).
            // Segundo Voltar fecha o detalhe e vai pra lista.
            onPlay = onPlay
        )
        return
    }

    // TV/Tablet: drawer + grid no mesmo padrão de Filmes.
    if (isTvLike) {
        if (selectedCat == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.iptv.app.ui.common.InlineLoading()
            }
        } else {
            val sortedCats = remember(cats.items) { cats.items.sortedForDisplay() }
            SeriesCategoriesScreen(
                categories = sortedCats,
                selectedCategoryId = selectedCat,
                series = series.items,
                loading = series.loading,
                error = series.error,
                advancedFilters = advancedFilters,
                onAdvancedFiltersClick = { filtersDialogOpen = true },
                sort = settings.seriesSort,
                onSortChange = { vm.setSort(SortScope.SERIES, it) },
                onCategorySelected = { catId ->
                    onSelectedCatChange(catId)
                    vm.loadSeries(catId)
                },
                onSeriesClick = { s -> openSeries = Triple(s.id, s.name, s.coverUrl) },
                onRetry = { vm.loadSeries(selectedCat, forceRefresh = true) },
                countByCategory = vm.seriesCountByCategory.collectAsState().value,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (filtersDialogOpen) {
            val availableGenres = remember(series.items) {
                series.items
                    .mapNotNull { it.genre }
                    .flatMap { it.split(',', '/', ';') }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
            }
            AdvancedFiltersDialog(
                initial = advancedFilters,
                availableGenres = availableGenres,
                onDismiss = { filtersDialogOpen = false },
                onApply = {
                    advancedFilters = it
                    filtersDialogOpen = false
                }
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dim.ScreenPadding, vertical = 12.dp)) {
        val catCols = when (dim.formFactor) {
            com.iptv.app.ui.common.FormFactor.Phone -> 2
            com.iptv.app.ui.common.FormFactor.Tablet -> 3
            com.iptv.app.ui.common.FormFactor.Tv -> 4
        }
        if (selectedCat == null) {
            val isPhoneCats = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            if (isPhoneCats) {
                Text(
                    stringResource(R.string.section_series_categories),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LocalFilterField(
                    value = categoryFilter,
                    onValueChange = { categoryFilter = it },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.section_series_categories),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Box(modifier = Modifier.weight(1f))
                    LocalFilterField(
                        value = categoryFilter,
                        onValueChange = { categoryFilter = it },
                        modifier = Modifier.width(360.dp)
                    )
                }
            }
            val needleCat = categoryFilter.trim().lowercase()
            val sortedCats = remember(cats.items) { cats.items.sortedForDisplay() }
            val visibleCats = if (needleCat.isBlank()) sortedCats
            else sortedCats.filter { it.name.lowercase().contains(needleCat) }

            // FTS hits across all categories — surfaces "Pokemon" even when
            // the user hasn't entered the right category yet.
            var foundSeries by remember { mutableStateOf<List<com.iptv.app.domain.model.Series>>(emptyList()) }
            androidx.compose.runtime.LaunchedEffect(needleCat) {
                foundSeries = vm.searchSeriesByName(needleCat)
            }

            if (cats.loading && cats.items.isEmpty()) com.iptv.app.ui.common.InlineLoading()
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadSeriesCategories(forceRefresh = true) }) }

            if (foundSeries.isNotEmpty()) {
                Text(
                    stringResource(R.string.section_series_default) + " (${foundSeries.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    lazyItems(foundSeries) { s ->
                        PosterCard(
                            title = s.name,
                            imageUrl = s.coverUrl,
                            fallbackIcon = Icons.Filled.Tv,
                            rating = s.rating
                        ) {
                            openSeries = Triple(s.id, s.name, s.coverUrl)
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = cats.loading,
                onRefresh = { vm.loadSeriesCategories(forceRefresh = true) },
                enabled = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(catCols),
                    horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                    verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
                ) {
                    items(visibleCats) { cat ->
                        CategoryCard(title = cat.name, count = null, locked = false) {
                            onSelectedCatChange(cat.id)
                            vm.loadSeries(cat.id)
                        }
                    }
                }
            }
        } else {
            // Header on phones overflowed horizontally when the category name
            // was long ("Series | Amazon Prime Video"). Stack title above the
            // action buttons on phones, keep single row on tablet/TV.
            val seriesDefault = stringResource(R.string.section_series_default)
            val categoryName = cats.items.firstOrNull { it.id == selectedCat }?.name ?: seriesDefault
            val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            if (isPhone) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    com.iptv.app.ui.common.FiltersButton(
                        active = advancedFilters.isActive,
                        onClick = { filtersDialogOpen = true }
                    )
                    SortMenuButton(
                        current = settings.seriesSort,
                        options = SortOption.SERIES_OPTIONS
                    ) { vm.setSort(SortScope.SERIES, it) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        categoryName,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Box(modifier = Modifier.weight(1f))
                    com.iptv.app.ui.common.FiltersButton(
                        active = advancedFilters.isActive,
                        onClick = { filtersDialogOpen = true }
                    )
                    SortMenuButton(
                        current = settings.seriesSort,
                        options = SortOption.SERIES_OPTIONS
                    ) { vm.setSort(SortScope.SERIES, it) }
                }
            }
            if (series.loading && series.items.isEmpty()) com.iptv.app.ui.common.InlineLoading()
            series.error?.let { ErrorState(message = it, onRetry = { vm.loadSeries(selectedCat, forceRefresh = true) }) }
            LocalFilterField(
                value = localFilter,
                onValueChange = { localFilter = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val needle = localFilter.trim().lowercase()
            // Build the genre vocabulary from the current category. Splits on common
            // separators ("Action, Drama" or "Action / Drama") so each label stands alone.
            val availableGenres = remember(series.items) {
                series.items
                    .mapNotNull { it.genre }
                    .flatMap { it.split(',', '/', ';') }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
            }
            val filteredSeries = series.items.asSequence()
                .filter { needle.isBlank() || it.name.lowercase().contains(needle) }
                .filter { s ->
                    val af = advancedFilters
                    if (!af.isActive) return@filter true
                    val year = parseYear(s.releaseDate)
                    val yearOk = (af.yearMin == null || (year != null && year >= af.yearMin)) &&
                        (af.yearMax == null || (year != null && year <= af.yearMax))
                    val ratingOk = af.ratingMin == null || s.rating >= af.ratingMin
                    val genreOk = af.genres.isEmpty() || s.genre?.let { g ->
                        val tokens = g.split(',', '/', ';').map { it.trim() }
                        af.genres.any { wanted -> tokens.any { it.equals(wanted, ignoreCase = true) } }
                    } == true
                    yearOk && ratingOk && genreOk
                }
                .toList()
            val seriesMinWidth = when (dim.formFactor) {
                com.iptv.app.ui.common.FormFactor.Phone -> 150.dp
                com.iptv.app.ui.common.FormFactor.Tablet -> 160.dp
                com.iptv.app.ui.common.FormFactor.Tv -> 180.dp
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(seriesMinWidth),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
            ) {
                items(filteredSeries) { s ->
                    PosterCard(
                        title = s.name,
                        imageUrl = s.coverUrl,
                        fallbackIcon = Icons.Filled.Tv,
                        fillWidth = true,
                        rating = s.rating
                    ) {
                        openSeries = Triple(s.id, s.name, s.coverUrl)
                    }
                }
            }
            if (filtersDialogOpen) {
                AdvancedFiltersDialog(
                    initial = advancedFilters,
                    availableGenres = availableGenres,
                    onDismiss = { filtersDialogOpen = false },
                    onApply = {
                        advancedFilters = it
                        filtersDialogOpen = false
                    }
                )
            }
        }
    }
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    title: String,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    coverUrl: String? = null,
    /** Quando vem do "Continuar Assistindo", pulamos a etapa de escolher
     *  temporada — abrimos direto o popup de episódios na temporada do
     *  último episódio assistido. */
    initialSeasonNumber: Int? = null,
    initialEpisodeId: String? = null,
    vm: SeriesDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val dim = rememberTvDim()
    val snackbar = LocalSnackbar.current
    val watchlistAddedMsg = stringResource(R.string.snack_watchlist_added)
    val watchlistRemovedMsg = stringResource(R.string.snack_watchlist_removed)
    val favoriteAddedMsg = stringResource(R.string.snack_favorite_added)
    val favoriteRemovedMsg = stringResource(R.string.snack_favorite_removed)
    var selectedSeason by rememberSaveable { mutableStateOf<Int?>(initialSeasonNumber) }
    // Elevado pra cá (antes ficava bem mais embaixo) porque o botão Resume
    // do topo agora marca o popup como aberto antes de chamar onPlay — assim
    // o usuário volta do player diretamente na lista de episódios.
    var seasonDialogOpen by rememberSaveable {
        mutableStateOf(initialSeasonNumber != null)
    }

    LaunchedEffect(seriesId) { vm.load(seriesId, title, coverUrl) }
    // Agora a tela mostra detalhes + combo de temporadas + episódios na
    // mesma view — não há mais "sub-tela" de episódios. Voltar sai direto
    // da tela de detalhe para a lista de séries.
    androidx.activity.compose.BackHandler { onBack() }
    com.iptv.app.ui.common.RegisterHeaderBack { onBack() }

    val playFocus = remember { FocusRequester() }
    val seasonsBringIntoView = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state) {
        if (state?.resume != null) runCatching { playFocus.requestFocus() }
    }

    val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone

    // Tela única: poster + metadados + botão de temporada (abre dialog com
    // a lista de episódios). Tudo compacto para caber sem scroll.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            val detail = state
            if (detail == null) {
                Text(stringResource(R.string.loading))
                return
            }

            // Poster + fontes compactos para a tela inteira caber sem scroll.
            // Antes o poster ocupava ~1/3 da largura e os metadados eram bodyMedium —
            // somava ~700dp de altura, forçando scroll na TV mesmo em 1080p.
            val (posterW, posterH) = when (dim.formFactor) {
                com.iptv.app.ui.common.FormFactor.Phone -> 100.dp to 150.dp
                com.iptv.app.ui.common.FormFactor.Tablet -> 130.dp to 195.dp
                com.iptv.app.ui.common.FormFactor.Tv -> 160.dp to 240.dp
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)) {
                Box(
                    modifier = Modifier
                        .width(posterW)
                        .height(posterH)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    val cover = detail.coverUrl ?: detail.info?.cover ?: coverUrl
                    if (cover.isNullOrBlank()) {
                        Icon(Icons.Filled.Tv, contentDescription = null)
                    } else {
                        AsyncImage(model = cover, contentDescription = title)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    detail.info?.let { info ->
                        info.releaseDate?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.movie_release, it), style = MaterialTheme.typography.bodySmall)
                        }
                        info.rating?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.movie_rating, it), style = MaterialTheme.typography.bodySmall)
                        }
                        info.genre?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.movie_genre, it), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        info.director?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.movie_director, it), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        info.cast?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.movie_cast, it), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        detail.resume?.let { resume ->
                            val labelRes = if (resume.positionMs > 0) R.string.series_resume else R.string.series_play_next
                            TouchableButton(
                                onClick = {
                                    // Marca o popup da temporada como aberto
                                    // *antes* de mandar pro player — assim ao
                                    // voltar do player o usuário cai na lista
                                    // de episódios, não na tela só com botões
                                    // de temporada.
                                    selectedSeason = resume.episode.seasonNumber
                                    seasonDialogOpen = true
                                    onPlay(
                                        PlayerArgs(
                                            kind = PlayerKind.EPISODE,
                                            streamId = resume.episode.id.toIntOrNull() ?: 0,
                                            title = resume.episode.title,
                                            containerExtension = resume.episode.containerExtension,
                                            seriesId = resume.episode.seriesId,
                                            seasonNumber = resume.episode.seasonNumber,
                                            episodeId = resume.episode.id,
                                            startPositionMs = resume.positionMs
                                        )
                                    )
                                },
                                modifier = Modifier.focusRequester(playFocus),
                                compact = true
                            ) {
                                Text(stringResource(labelRes, resume.episode.seasonNumber, resume.episode.episodeNum))
                            }
                        }
                        // Favorito e watchlist agora só ícone — texto duplica
                        // o tooltip do screen reader e ocupa muito espaço.
                        TouchableButton(
                            compact = true,
                            onClick = {
                                val wasFav = detail.isFavorite
                                vm.toggleFavorite()
                                snackbar?.show(if (wasFav) favoriteRemovedMsg else favoriteAddedMsg)
                            }
                        ) {
                            Icon(
                                if (detail.isFavorite) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (detail.isFavorite) R.string.remove_favorite else R.string.add_favorite
                                )
                            )
                        }
                        TouchableButton(
                            compact = true,
                            onClick = {
                                val wasInList = detail.isInWatchlist
                                vm.toggleWatchlist()
                                snackbar?.show(if (wasInList) watchlistRemovedMsg else watchlistAddedMsg)
                            }
                        ) {
                            Icon(
                                if (detail.isInWatchlist) Icons.Filled.Bookmark
                                else Icons.Filled.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (detail.isInWatchlist) R.string.watchlist_remove else R.string.watchlist_add
                                )
                            )
                        }
                    }
                }
            }

            detail.info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.synopsis),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        plot,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Pre-selecionar a primeira temporada que tem episódios.
            LaunchedEffect(detail.seriesId) {
                if (selectedSeason == null) {
                    selectedSeason = detail.seasons.firstOrNull {
                        (detail.episodesBySeason[it.seasonNumber]?.size ?: 0) > 0
                    }?.seasonNumber ?: detail.seasons.firstOrNull()?.seasonNumber
                }
            }

            // Botões: um por temporada. Clicar abre o popup com a lista de
            // episódios prontos pra tocar — sem manter grid grande na própria
            // tela, que ocupava muito espaço quando havia muitos episódios.
            // `seasonDialogOpen` está declarado no topo da função pra que o
            // botão Resume (acima) consiga marcá-lo antes do onPlay.
            val seasonRequesters = remember(detail.seasons.size) {
                detail.seasons.associate { it.seasonNumber to FocusRequester() }
            }
            // Quando o usuário desce do menu/header até esta seção, foco vai
            // pro botão da temporada SELECIONADA — antes caía na primeira
            // (T1) mesmo quando outra estava ativa, induzindo erro.
            LaunchedEffect(detail.seriesId, selectedSeason) {
                val req = selectedSeason?.let { seasonRequesters[it] } ?: return@LaunchedEffect
                runCatching { req.requestFocus() }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(seasonsBringIntoView)
                    .onFocusEvent { fs ->
                        if (fs.hasFocus) coroutineScope.launch { seasonsBringIntoView.bringIntoView() }
                    }
            ) {
                Text(
                    stringResource(R.string.section_seasons),
                    style = MaterialTheme.typography.titleSmall
                )
                // FlowRow quebra em múltiplas linhas quando há muitas
                // temporadas (Naruto etc.) — antes a LazyRow rolava
                // horizontalmente e o conteúdo abaixo era empurrado pra fora.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    detail.seasons.forEach { s ->
                        val isSel = s.seasonNumber == selectedSeason
                        val count = detail.episodesBySeason[s.seasonNumber]?.size ?: 0
                        val req = seasonRequesters[s.seasonNumber] ?: FocusRequester()
                        TouchableButton(
                            selected = isSel,
                            compact = true,
                            modifier = Modifier.focusRequester(req),
                            onClick = {
                                selectedSeason = s.seasonNumber
                                if (count > 0) seasonDialogOpen = true
                            }
                        ) {
                            Text("T${s.seasonNumber}" + if (count > 0) " ($count)" else "")
                        }
                    }
                }
            }

            if (seasonDialogOpen && selectedSeason != null) {
                val episodes = detail.episodesBySeason[selectedSeason] ?: emptyList()
                val seriesCover = detail.coverUrl ?: detail.info?.cover
                EpisodesDialog(
                    seasonLabel = "T${selectedSeason}",
                    episodes = episodes,
                    watched = detail.watchedEpisodes,
                    percents = detail.episodePercents,
                    fallbackPoster = seriesCover,
                    highlightEpisodeId = initialEpisodeId,
                    onDismiss = { seasonDialogOpen = false },
                    onPick = { e ->
                        // NÃO fechamos o popup ao mandar pro player. Assim,
                        // quando o usuário voltar (Back), cai na lista de
                        // episódios — onde parou — em vez do menu de séries.
                        // O dismiss do diálogo continua disponível via Back
                        // explícito dentro da própria tela de detalhe.
                        onPlay(
                            PlayerArgs(
                                kind = PlayerKind.EPISODE,
                                streamId = e.id.toIntOrNull() ?: 0,
                                title = e.title,
                                containerExtension = e.containerExtension,
                                seriesId = e.seriesId,
                                seasonNumber = e.seasonNumber,
                                episodeId = e.id
                            )
                        )
                    }
                )
            }
    }
}

@Composable
private fun EpisodesDialog(
    seasonLabel: String,
    episodes: List<Episode>,
    watched: Set<String>,
    percents: Map<String, Int>,
    fallbackPoster: String?,
    highlightEpisodeId: String? = null,
    onDismiss: () -> Unit,
    onPick: (Episode) -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.episodes_dialog_title, seasonLabel),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (episodes.isEmpty()) {
                Text(
                    stringResource(R.string.season_empty_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                // Quando veio de "Continuar Assistindo", rola até o episódio
                // e dá foco — vira o primeiro clique evidente do usuário.
                val highlightIndex = remember(highlightEpisodeId, episodes) {
                    if (highlightEpisodeId == null) -1
                    else episodes.indexOfFirst { it.id == highlightEpisodeId }
                }
                val highlightFocus = remember { FocusRequester() }
                LaunchedEffect(highlightIndex) {
                    if (highlightIndex >= 0) {
                        runCatching { listState.scrollToItem(highlightIndex) }
                        runCatching { highlightFocus.requestFocus() }
                    }
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(480.dp)
                ) {
                    lazyItemsIndexed(episodes) { index, e ->
                        EpisodeRow(
                            episode = e,
                            watched = e.id in watched,
                            percent = percents[e.id] ?: 0,
                            fallbackPoster = fallbackPoster,
                            highlighted = index == highlightIndex,
                            modifier = if (index == highlightIndex)
                                Modifier.focusRequester(highlightFocus) else Modifier,
                            onClick = { onPick(e) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    watched: Boolean,
    percent: Int,
    fallbackPoster: String? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Título sintetizado se o provedor não enviou um — alguns servidores
    // entregam só id/episodeNum sem o nome real do episódio. Cair em
    // "Episódio X" deixa pelo menos algo legível.
    val fallbackTitle = stringResource(R.string.episode_fallback_title, episode.episodeNum)
    val displayTitle = episode.title.takeIf { it.isNotBlank() && it != fallbackTitle }
        ?: fallbackTitle
    // Quando o episódio não tem poster próprio, usa a capa da série para
    // não deixar um quadrado cinza com ícone genérico.
    val poster = episode.poster?.takeIf { it.isNotBlank() } ?: fallbackPoster
    com.iptv.app.ui.common.TouchableCard(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa pequena à esquerda. Como agora são duas colunas, cada
            // metade da tela acomoda só ~400dp — manter compacto.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!poster.isNullOrBlank()) {
                    AsyncImage(
                        model = poster,
                        contentDescription = displayTitle,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Badge no canto: ✓ (assistido) ou ⏳ (meio assistido).
                // Mesmo padrão visual do PosterCard de filmes — manter a
                // semântica consistente entre as telas.
                val showWatched = watched
                val showInProgress = !watched && percent in 1..99
                if (showWatched || showInProgress) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showWatched) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                modifier = Modifier.size(11.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.HourglassBottom,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                // Título único combinando "T2E33 — Nome do episódio". Antes
                // estava em dois Text e o segundo era cortado pela altura do
                // row no dialog (o usuário só via "T2E33").
                val prefix = "T${episode.seasonNumber}E${episode.episodeNum}"
                val combined = "${if (watched) "✓ " else ""}$prefix — $displayTitle" +
                    (if (percent in 1..99) " ($percent%)" else "")
                Text(
                    combined,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                episode.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                    Text(
                        plot,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
