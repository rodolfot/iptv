@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.series

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.iptv.app.R
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.Series
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.AdvancedFilters
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.parseYear

@Composable
fun SeriesCategoriesScreen(
    categories: List<Category>,
    selectedCategoryId: String,
    series: List<Series>,
    loading: Boolean,
    error: String?,
    advancedFilters: AdvancedFilters,
    onAdvancedFiltersClick: () -> Unit,
    sort: SortOption,
    onSortChange: (SortOption) -> Unit,
    onCategorySelected: (String) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onRetry: () -> Unit,
    countByCategory: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    // Sem key: a busca persiste mesmo quando o usuário troca de categoria
    // ou abre o detalhe e volta. Usuário reclamava que a busca era zerada
    // ao voltar de uma série.
    var localFilter by rememberSaveable { mutableStateOf("") }
    val drawerSelectedRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(selectedCategoryId) {
        kotlinx.coroutines.delay(50)
        runCatching { drawerSelectedRequester.requestFocus() }
    }

    Row(modifier = modifier.fillMaxSize()) {
        SeriesCategoriesDrawer(
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
            ) {
                LocalFilterField(
                    value = localFilter,
                    onValueChange = { localFilter = it },
                    modifier = Modifier.weight(1f)
                )
                SortMenuButton(
                    current = sort,
                    options = SortOption.SERIES_OPTIONS,
                    onSelect = onSortChange
                )
            }

            if (loading && series.isEmpty()) {
                com.iptv.app.ui.common.InlineLoading()
                return@Column
            }
            error?.let { ErrorState(message = it, onRetry = onRetry) }

            val needle = localFilter.trim().lowercase()
            val filtered = series.asSequence()
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

            // Preserva scroll do grid por categoria (igual a Filmes).
            var savedFirstIndex by rememberSaveable(selectedCategoryId) { mutableStateOf(0) }
            var savedFirstOffset by rememberSaveable(selectedCategoryId) { mutableStateOf(0) }
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
            // Mesma densidade do menu Filmes (95dp) para manter consistência.
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(95.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { s ->
                    PosterCard(
                        title = s.name,
                        imageUrl = s.coverUrl,
                        fallbackIcon = Icons.Filled.Tv,
                        fillWidth = true,
                        rating = s.rating
                    ) {
                        onSeriesClick(s)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesCategoriesDrawer(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    selectedRequester: androidx.compose.ui.focus.FocusRequester,
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
        Text(
            stringResource(R.string.section_series_categories),
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
