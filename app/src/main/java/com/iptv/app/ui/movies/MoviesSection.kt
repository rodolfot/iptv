package com.iptv.app.ui.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.sortedForDisplay
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.AdvancedFilters
import com.iptv.app.ui.common.AdvancedFiltersDialog
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.PullToRefreshBox
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.parseYear
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.parental.ParentalPinDialog
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

/** Converte um filme da listagem nos args de reprodução do player. */
internal fun com.iptv.app.domain.model.Movie.toMoviePlayerArgs() = PlayerArgs(
    kind = PlayerKind.MOVIE,
    streamId = id,
    title = name,
    containerExtension = containerExtension,
    posterUrl = posterUrl,
    categoryId = categoryId
)

@Composable
fun MoviesSection(
    vm: HomeViewModel,
    parental: ParentalSession,
    onPlay: (PlayerArgs) -> Unit,
    onPlayDirect: (PlayerArgs) -> Unit = onPlay,
    selectedCat: String?,
    onSelectedCatChange: (String?) -> Unit,
    // Busca local elevada para a Home: sobrevive à entrada no detalhe/player e
    // só é limpa ao trocar de categoria ou sair do menu.
    localFilter: String,
    onLocalFilterChange: (String) -> Unit
) {
    val rawCats by vm.movieCategories.collectAsState()
    val movies by vm.movies.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val kidsAllowed by vm.kidsAllowedCategories.collectAsState()
    val kidsActive by vm.kidsMode.collectAsState()
    val cats = when {
        // No Kids mode → show everything.
        !kidsActive -> rawCats
        // Kids mode with an explicit allowlist → show only those.
        kidsAllowed.isNotEmpty() -> rawCats.copy(
            items = rawCats.items.filter { "movie:${it.id}" in kidsAllowed }
        )
        // Kids mode without an allowlist configured → at least hide adult.
        else -> rawCats.copy(items = rawCats.items.filter { !it.isAdult })
    }
    val dim = rememberTvDim()
    var pendingCategory by remember { mutableStateOf<Category?>(null) }
    var pendingMovie by remember { mutableStateOf<PlayerArgs?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var advancedFilters by remember(selectedCat) { mutableStateOf(AdvancedFilters()) }
    var filtersDialogOpen by remember { mutableStateOf(false) }

    val movieProgress by vm.movieProgress.collectAsState()

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadMovieCategories()
    }
    // Categoria restaurada (volta do player, ou processo recriado pelo sistema
    // com a TV em standby): o rememberSaveable devolve a seleção, mas a lista
    // do ViewModel pode estar vazia — e o auto-load abaixo só roda quando não
    // há seleção. Sem isto a grade ficava em branco.
    LaunchedEffect(Unit) {
        val restored = selectedCat
        if (restored != null && movies.items.isEmpty() && !movies.loading) {
            vm.loadMovies(restored)
        }
    }
    // TV/Tablet: pula o grid de categorias e abre direto a primeira (assim
    // como o menu Ao Vivo). Em phone mantém o grid.
    val isTvLike = dim.formFactor != com.iptv.app.ui.common.FormFactor.Phone
    LaunchedEffect(cats.items, isTvLike) {
        if (isTvLike && selectedCat == null && cats.items.isNotEmpty()) {
            val first = cats.items.sortedForDisplay()
                .firstOrNull { !it.isAdult || parental.isUnlocked() }
                ?: cats.items.first()
            onSelectedCatChange(first.id)
            vm.loadMovies(first.id)
        }
    }
    androidx.activity.compose.BackHandler(enabled = !isTvLike && selectedCat != null) {
        onSelectedCatChange(null)
    }
    // Surface the "back to categories" action up in the app top bar while the
    // user is browsing inside one category (phone only — TV/Tablet usa drawer).
    if (!isTvLike && selectedCat != null) {
        com.iptv.app.ui.common.RegisterHeaderBack { onSelectedCatChange(null) }
    }

    // TV/Tablet: layout drawer (categorias) + grid (filmes), igual ao Ao Vivo.
    if (isTvLike) {
        if (selectedCat == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.iptv.app.ui.common.InlineLoading()
            }
        } else {
            val sortedCats = remember(cats.items) { cats.items.sortedForDisplay() }
            val cat = cats.items.firstOrNull { it.id == selectedCat }
            MoviesCategoriesScreen(
                categories = sortedCats,
                selectedCategoryId = selectedCat,
                movies = movies.items,
                isCategoryAdult = cat?.isAdult == true,
                isParentalUnlocked = parental.isUnlocked(),
                loading = movies.loading,
                error = movies.error,
                advancedFilters = advancedFilters,
                onAdvancedFiltersClick = { filtersDialogOpen = true },
                localFilter = localFilter,
                onLocalFilterChange = onLocalFilterChange,
                onSetMovieQueue = { ordered ->
                    vm.setMovieQueue(ordered.map { it.toMoviePlayerArgs() })
                },
                sort = settings.moviesSort,
                onSortChange = { vm.setSort(SortScope.MOVIES, it) },
                onCategorySelected = { catId ->
                    val nextCat = cats.items.firstOrNull { it.id == catId }
                    if (nextCat?.isAdult == true && !parental.isUnlocked()) {
                        pendingCategory = nextCat
                    } else {
                        onSelectedCatChange(catId)
                        vm.loadMovies(catId)
                    }
                },
                onMovieClick = { m ->
                    val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                    val args = PlayerArgs(
                        kind = PlayerKind.MOVIE,
                        streamId = m.id,
                        title = m.name,
                        containerExtension = m.containerExtension,
                        posterUrl = m.posterUrl,
                        categoryId = m.categoryId
                    )
                    if (locked) pendingMovie = args else onPlay(args)
                },
                onMovieDirectPlay = { m ->
                    val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                    if (!locked) {
                        onPlayDirect(
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
                },
                onRetry = { vm.loadMovies(selectedCat, forceRefresh = true) },
                countByCategory = vm.movieCountByCategory.collectAsState().value,
                movieProgress = movieProgress,
                modifier = Modifier.fillMaxSize()
            )
        }

        pendingCategory?.let { c ->
            ParentalPinDialog(
                expectedPin = settings.parentalPin,
                onUnlocked = {
                    parental.unlock()
                    pendingCategory = null
                    onSelectedCatChange(c.id)
                    vm.loadMovies(c.id)
                },
                onCancel = { pendingCategory = null },
                onPinCreated = { vm.setParentalPin(it) }
            )
        }
        pendingMovie?.let { args ->
            ParentalPinDialog(
                expectedPin = settings.parentalPin,
                onUnlocked = {
                    parental.unlock()
                    pendingMovie = null
                    onPlay(args)
                },
                onCancel = { pendingMovie = null },
                onPinCreated = { vm.setParentalPin(it) }
            )
        }
        if (filtersDialogOpen) {
            AdvancedFiltersDialog(
                initial = advancedFilters,
                availableGenres = emptyList(),
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
                    stringResource(R.string.section_movie_categories),
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
                        stringResource(R.string.section_movie_categories),
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
            val needle = categoryFilter.trim().lowercase()
            val sortedCats = remember(cats.items) { cats.items.sortedForDisplay() }
            val visibleCats = if (needle.isBlank()) sortedCats
            else sortedCats.filter { it.name.lowercase().contains(needle) }

            // FTS lookup over titles so "pokemon" surfaces the movie even when
            // the user hasn't entered any category. Empty when the query is
            // shorter than 2 chars (matches SearchScreen's contract).
            var foundMovies by remember { mutableStateOf<List<com.iptv.app.domain.model.Movie>>(emptyList()) }
            androidx.compose.runtime.LaunchedEffect(needle) {
                foundMovies = vm.searchMoviesByName(needle)
            }

            if (cats.loading && cats.items.isEmpty()) com.iptv.app.ui.common.InlineLoading()
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadMovieCategories(forceRefresh = true) }) }

            if (foundMovies.isNotEmpty()) {
                Text(
                    stringResource(R.string.section_movies_default) + " (${foundMovies.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    lazyItems(foundMovies) { m ->
                        val mp = movieProgress[m.id]
                        PosterCard(
                            title = m.name,
                            imageUrl = m.posterUrl,
                            fallbackIcon = Icons.Filled.Movie,
                            rating = m.rating,
                            watched = mp?.watched == true,
                            progressPercent = mp?.percent?.takeIf { mp.watched.not() && it in 1..99 }
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

            PullToRefreshBox(
                isRefreshing = cats.loading,
                onRefresh = { vm.loadMovieCategories(forceRefresh = true) },
                enabled = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(catCols),
                    horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                    verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
                ) {
                    items(visibleCats) { cat ->
                        CategoryCard(
                            title = cat.name,
                            count = null,
                            locked = cat.isAdult && !parental.isUnlocked()
                        ) {
                            if (cat.isAdult && !parental.isUnlocked()) {
                                pendingCategory = cat
                            } else {
                                onSelectedCatChange(cat.id)
                                vm.loadMovies(cat.id)
                            }
                        }
                    }
                }
            }
        } else {
            // Phone: stack title row above action row so a long category name
            // doesn't push the buttons off-screen.
            val moviesDefault = stringResource(R.string.section_movies_default)
            val categoryName = cats.items.firstOrNull { it.id == selectedCat }?.name ?: moviesDefault
            val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            if (isPhone) {
                // Back lives in the app TopBar via RegisterHeaderBack; only the
                // category name stays here as a header.
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
                        current = settings.moviesSort,
                        options = SortOption.MOVIE_OPTIONS
                    ) { vm.setSort(SortScope.MOVIES, it) }
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
                        current = settings.moviesSort,
                        options = SortOption.MOVIE_OPTIONS
                    ) { vm.setSort(SortScope.MOVIES, it) }
                }
            }
            if (movies.loading && movies.items.isEmpty()) com.iptv.app.ui.common.InlineLoading()
            movies.error?.let { ErrorState(message = it, onRetry = { vm.loadMovies(selectedCat, forceRefresh = true) }) }
            LocalFilterField(
                value = localFilter,
                onValueChange = onLocalFilterChange,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val needle = localFilter.trim().lowercase()
            val filteredMovies = movies.items.asSequence()
                .filter { needle.isBlank() || it.name.lowercase().contains(needle) }
                .filter { m ->
                    val af = advancedFilters
                    if (!af.isActive) return@filter true
                    val year = parseYear(m.releaseDate)
                    val yearOk = (af.yearMin == null || (year != null && year >= af.yearMin)) &&
                        (af.yearMax == null || (year != null && year <= af.yearMax))
                    val ratingOk = af.ratingMin == null || m.rating >= af.ratingMin
                    yearOk && ratingOk
                }
                .toList()
            // Adaptive grid: fits as many ~160dp-wide posters as the screen
            // allows. Previously TV used GridCells.Fixed(5) with a fixed
            // PosterCard width, which on some TVs ended up rendering oversized
            // tiles (only 2 fit per row).
            val posterMinWidth = when (dim.formFactor) {
                com.iptv.app.ui.common.FormFactor.Phone -> 150.dp
                com.iptv.app.ui.common.FormFactor.Tablet -> 160.dp
                com.iptv.app.ui.common.FormFactor.Tv -> 180.dp
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(posterMinWidth),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
            ) {
                items(filteredMovies) { m ->
                    val cat = cats.items.firstOrNull { it.id == selectedCat }
                    val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                    val mp = movieProgress[m.id]
                    PosterCard(
                        title = m.name,
                        imageUrl = m.posterUrl,
                        locked = locked,
                        fallbackIcon = Icons.Filled.Movie,
                        fillWidth = true,
                        rating = m.rating,
                        watched = mp?.watched == true,
                        progressPercent = mp?.percent?.takeIf { mp.watched.not() && it in 1..99 }
                    ) {
                        val args = m.toMoviePlayerArgs()
                        if (locked) pendingMovie = args
                        else {
                            vm.setMovieQueue(filteredMovies.map { it.toMoviePlayerArgs() })
                            onPlay(args)
                        }
                    }
                }
            }
        }
    }

    pendingCategory?.let { cat ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingCategory = null
                onSelectedCatChange(cat.id)
                vm.loadMovies(cat.id)
            },
            onCancel = { pendingCategory = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }
    pendingMovie?.let { args ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingMovie = null
                onPlay(args)
            },
            onCancel = { pendingMovie = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }

    if (filtersDialogOpen) {
        AdvancedFiltersDialog(
            initial = advancedFilters,
            availableGenres = emptyList(),
            onDismiss = { filtersDialogOpen = false },
            onApply = {
                advancedFilters = it
                filtersDialogOpen = false
            }
        )
    }
}
