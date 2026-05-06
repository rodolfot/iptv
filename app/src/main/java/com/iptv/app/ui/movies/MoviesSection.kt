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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.TvDim
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
    val cats by vm.movieCategories.collectAsState()
    val movies by vm.movies.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<Category?>(null) }
    var pendingMovie by remember { mutableStateOf<PlayerArgs?>(null) }
    var localFilter by rememberSaveable(selectedCat) { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadMovieCategories()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = TvDim.ScreenPadding, vertical = 12.dp)) {
        if (selectedCat == null) {
            Text(stringResource(R.string.section_movie_categories), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            if (cats.loading && cats.items.isEmpty()) Text(stringResource(R.string.loading))
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadMovieCategories(forceRefresh = true) }) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
            ) {
                items(cats.items) { cat ->
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
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Button(onClick = { selectedCat = null }) { Text(stringResource(R.string.back)) }
                val moviesDefault = stringResource(R.string.section_movies_default)
                Text(
                    "  ${cats.items.firstOrNull { it.id == selectedCat }?.name ?: moviesDefault}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Box(modifier = Modifier.weight(1f))
                SortMenuButton(
                    current = settings.moviesSort,
                    options = SortOption.MOVIE_OPTIONS
                ) { vm.setSort(SortScope.MOVIES, it) }
            }
            if (movies.loading && movies.items.isEmpty()) Text(stringResource(R.string.loading))
            movies.error?.let { ErrorState(message = it, onRetry = { vm.loadMovies(selectedCat, forceRefresh = true) }) }
            LocalFilterField(
                value = localFilter,
                onValueChange = { localFilter = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val needle = localFilter.trim().lowercase()
            val filteredMovies = if (needle.isBlank()) movies.items
                else movies.items.filter { it.name.lowercase().contains(needle) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(TvDim.MoviesGridColumns),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
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
}
