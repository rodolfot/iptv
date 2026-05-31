package com.iptv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.AdvancedFilters
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.parseYear

/**
 * Layout TV/Tablet do menu Filmes — inspirado em LiveChannelsScreen.
 * Coluna esquerda: lista permanente de categorias.
 * Painel direito: barra com busca + filtros + sort, e grid de pôsteres.
 */
@Composable
fun MoviesCategoriesScreen(
    categories: List<Category>,
    selectedCategoryId: String,
    movies: List<Movie>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    loading: Boolean,
    error: String?,
    advancedFilters: AdvancedFilters,
    onAdvancedFiltersClick: () -> Unit,
    sort: SortOption,
    onSortChange: (SortOption) -> Unit,
    onCategorySelected: (String) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onMovieDirectPlay: (Movie) -> Unit,
    onRetry: () -> Unit,
    countByCategory: Map<String, Int> = emptyMap(),
    /** Mapa movieId → (watched, percent) usado pelos badges nos cards. */
    movieProgress: Map<Int, com.iptv.app.ui.home.HomeViewModel.MovieWatchState> = emptyMap(),
    modifier: Modifier = Modifier
) {
    // Sem key: a busca persiste mesmo quando o usuário troca de categoria
    // ou abre o detalhe e volta.
    var localFilter by rememberSaveable { mutableStateOf("") }
    val drawerSelectedRequester = remember { FocusRequester() }
    // Ao entrar na seção, foco vai pro item selecionado do drawer — o
    // usuário pediu pra cair na primeira categoria à esquerda ao descer
    // do menu superior.
    LaunchedEffect(selectedCategoryId) {
        // Pequeno atraso pra esperar a composição assentar antes de pedir
        // foco — sem isso o requester pode ainda não estar attached.
        kotlinx.coroutines.delay(50)
        runCatching { drawerSelectedRequester.requestFocus() }
    }

    Row(modifier = modifier.fillMaxSize()) {
        MoviesCategoriesDrawer(
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
            // Busca ocupa toda a largura do header. Botão Filtros foi removido
            // a pedido do usuário; só sort à direita.
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
                    options = SortOption.MOVIE_OPTIONS,
                    onSelect = onSortChange
                )
            }

            if (loading && movies.isEmpty()) {
                com.iptv.app.ui.common.InlineLoading()
                return@Column
            }
            error?.let { ErrorState(message = it, onRetry = onRetry) }

            val needle = localFilter.trim().lowercase()
            val filtered = movies.asSequence()
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

            val locked = isCategoryAdult && !isParentalUnlocked
            // Preserva scroll do grid por categoria — ao voltar do detalhe do
            // filme, o usuário cai onde estava em vez de no topo. A key inclui
            // a categoria pra cada uma ter seu próprio "lembrar onde parou".
            var savedFirstIndex by rememberSaveable(selectedCategoryId) { mutableStateOf(0) }
            var savedFirstOffset by rememberSaveable(selectedCategoryId) { mutableStateOf(0) }
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
                initialFirstVisibleItemIndex = savedFirstIndex,
                initialFirstVisibleItemScrollOffset = savedFirstOffset,
            )
            // Persiste a posição enquanto o usuário rola — assim, mesmo se o
            // composable for desmontado (entrada no detalhe), o rememberSaveable
            // já tem o último valor.
            androidx.compose.runtime.LaunchedEffect(gridState) {
                androidx.compose.runtime.snapshotFlow {
                    gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                }.collect { (idx, off) ->
                    savedFirstIndex = idx
                    savedFirstOffset = off
                }
            }
            // Largura compacta (~95dp) para acomodar muito mais cards na
            // primeira dobra. Em 1080p ainda restam ~1576dp úteis depois do
            // drawer; com 95dp + 8dp de gap cabem ~15-16 colunas e duas
            // linhas inteiras já mostram >8 filmes sem rolar.
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(95.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { m ->
                    val mp = movieProgress[m.id]
                    PosterCard(
                        title = m.name,
                        imageUrl = m.posterUrl,
                        locked = locked,
                        fallbackIcon = Icons.Filled.Movie,
                        fillWidth = true,
                        rating = m.rating,
                        watched = mp?.watched == true,
                        progressPercent = mp?.percent?.takeIf { mp.watched.not() && it in 1..99 },
                        onLongClick = {
                            // Long-press OK toca direto, sem detalhe/sinopse.
                            val locked2 = isCategoryAdult && !isParentalUnlocked
                            if (!locked2) onMovieDirectPlay(m)
                        }
                    ) {
                        onMovieClick(m)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviesCategoriesDrawer(
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
        Text(
            stringResource(R.string.section_movie_categories),
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
