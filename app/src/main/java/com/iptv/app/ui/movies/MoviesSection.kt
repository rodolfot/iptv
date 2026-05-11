package com.iptv.app.ui.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.iptv.app.ui.common.TouchableButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MoviesSection(
    vm: HomeViewModel,
    parental: ParentalSession,
    onPlay: (PlayerArgs) -> Unit
) {
    val rawCats by vm.movieCategories.collectAsState()
    val movies by vm.movies.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val kidsAllowed by vm.kidsAllowedCategories.collectAsState()
    val cats = if (kidsAllowed.isEmpty()) rawCats
        else rawCats.copy(items = rawCats.items.filter { "movie:${it.id}" in kidsAllowed })
    val dim = rememberTvDim()
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<Category?>(null) }
    var pendingMovie by remember { mutableStateOf<PlayerArgs?>(null) }
    var localFilter by rememberSaveable(selectedCat) { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var advancedFilters by remember(selectedCat) { mutableStateOf(AdvancedFilters()) }
    var filtersDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadMovieCategories()
    }
    androidx.activity.compose.BackHandler(enabled = selectedCat != null) {
        selectedCat = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dim.ScreenPadding, vertical = 12.dp)) {
        val catCols = when (dim.formFactor) {
            com.iptv.app.ui.common.FormFactor.Phone -> 2
            com.iptv.app.ui.common.FormFactor.Tablet -> 3
            com.iptv.app.ui.common.FormFactor.Tv -> 3
        }
        if (selectedCat == null) {
            Text(stringResource(R.string.section_movie_categories), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            // Local filter typed by the user, applied to the visible category names.
            // Saves a round-trip and lets people drill into a category from a
            // catalog with hundreds of entries.
            LocalFilterField(
                value = categoryFilter,
                onValueChange = { categoryFilter = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val needle = categoryFilter.trim().lowercase()
            val visibleCats = if (needle.isBlank()) cats.items
            else cats.items.filter { it.name.lowercase().contains(needle) }
            if (cats.loading && cats.items.isEmpty()) Text(stringResource(R.string.loading))
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadMovieCategories(forceRefresh = true) }) }
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
                                selectedCat = cat.id
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    TouchableButton(onClick = { selectedCat = null }) { Text(stringResource(R.string.back)) }
                    Text(
                        categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    TouchableButton(onClick = { filtersDialogOpen = true }) {
                        Text(stringResource(
                            if (advancedFilters.isActive) R.string.filters_button_active else R.string.filters_button
                        ))
                    }
                    SortMenuButton(
                        current = settings.moviesSort,
                        options = SortOption.MOVIE_OPTIONS
                    ) { vm.setSort(SortScope.MOVIES, it) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    TouchableButton(onClick = { selectedCat = null }) { Text(stringResource(R.string.back)) }
                    Text(
                        "  $categoryName",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Box(modifier = Modifier.weight(1f))
                    TouchableButton(onClick = { filtersDialogOpen = true }) {
                        Text(stringResource(
                            if (advancedFilters.isActive) R.string.filters_button_active else R.string.filters_button
                        ))
                    }
                    SortMenuButton(
                        current = settings.moviesSort,
                        options = SortOption.MOVIE_OPTIONS
                    ) { vm.setSort(SortScope.MOVIES, it) }
                }
            }
            if (movies.loading && movies.items.isEmpty()) Text(stringResource(R.string.loading))
            movies.error?.let { ErrorState(message = it, onRetry = { vm.loadMovies(selectedCat, forceRefresh = true) }) }
            LocalFilterField(
                value = localFilter,
                onValueChange = { localFilter = it },
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(dim.MoviesGridColumns),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
            ) {
                items(filteredMovies) { m ->
                    val cat = cats.items.firstOrNull { it.id == selectedCat }
                    val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                    PosterCard(
                        title = m.name,
                        imageUrl = m.posterUrl,
                        locked = locked,
                        fallbackIcon = Icons.Filled.Movie
                    ) {
                        val args = PlayerArgs(
                            kind = PlayerKind.MOVIE,
                            streamId = m.id,
                            title = m.name,
                            containerExtension = m.containerExtension,
                            posterUrl = m.posterUrl,
                            categoryId = m.categoryId
                        )
                        if (locked) pendingMovie = args
                        else onPlay(args)
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
                selectedCat = cat.id
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
