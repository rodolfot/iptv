package com.iptv.app.ui.live

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.sortedForDisplay
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ChannelCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.PullToRefreshBox
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.parental.ParentalPinDialog
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

@Composable
fun LiveSection(
    vm: HomeViewModel,
    parental: ParentalSession,
    onPlay: (PlayerArgs) -> Unit
) {
    // Helper local: clicar num canal toca direto. A tela de detalhe (EPG +
    // favoritos) deixou de ser usada via Ao Vivo — preview no painel da
    // grade já dá feedback suficiente. SearchScreen ainda chama
    // ChannelDetailScreen para preservar o caminho de detalhe lá.
    fun playChannelDirect(ch: com.iptv.app.domain.model.LiveChannel) {
        onPlay(
            PlayerArgs(
                kind = PlayerKind.LIVE,
                streamId = ch.id,
                title = ch.name,
                containerExtension = null
            )
        )
    }
    val rawCats by vm.liveCategories.collectAsState()
    val channels by vm.channels.collectAsState()
    val epgNow by vm.epgNow.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val kidsAllowed by vm.kidsAllowedCategories.collectAsState()
    val kidsActive by vm.kidsMode.collectAsState()
    // Kids profile: prefer the parent's allowlist; otherwise at least hide
    // categories flagged as adult so toggling Kids isn't a no-op.
    val cats = when {
        !kidsActive -> rawCats
        kidsAllowed.isNotEmpty() -> rawCats.copy(
            items = rawCats.items.filter { "live:${it.id}" in kidsAllowed }
        )
        else -> rawCats.copy(items = rawCats.items.filter { !it.isAdult })
    }
    val dim = rememberTvDim()
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<Category?>(null) }
    var pendingChannel by remember { mutableStateOf<PlayerArgs?>(null) }
    var localFilter by rememberSaveable(selectedCat) { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadLiveCategories()
    }
    // Em TV/Tablet, pula a tela de "grid de categorias" e abre direto a
    // primeira (que já tem categorias no drawer lateral do LiveChannelsScreen).
    // Em phone segue mostrando o grid (não tem espaço para drawer).
    val isTvLike = dim.formFactor != com.iptv.app.ui.common.FormFactor.Phone
    LaunchedEffect(cats.items, isTvLike) {
        if (isTvLike && selectedCat == null && cats.items.isNotEmpty()) {
            val first = cats.items.sortedForDisplay()
                .firstOrNull { !it.isAdult || parental.isUnlocked() }
                ?: cats.items.first()
            selectedCat = first.id
            vm.loadChannels(first.id)
        }
    }
    androidx.activity.compose.BackHandler(enabled = !isTvLike && selectedCat != null) {
        selectedCat = null
    }
    if (!isTvLike && selectedCat != null) {
        com.iptv.app.ui.common.RegisterHeaderBack { selectedCat = null }
    }

    // TV/Tablet com categoria selecionada: LiveChannelsScreen ocupa a tela
    // inteira sem padding lateral — equivalente a Filmes/Séries, onde o
    // drawer começa em x=0. Antes era envolvido pela Column com padding,
    // deslocando o drawer ~24dp para a direita e desalinhando dos demais.
    if (isTvLike && selectedCat != null) {
        val cat = cats.items.firstOrNull { it.id == selectedCat }
        val sortedCats = remember(cats.items) { cats.items.sortedForDisplay() }
        val needle = localFilter.trim().lowercase()
        val filteredChannels = if (needle.isBlank()) channels.items
            else channels.items.filter { it.name.lowercase().contains(needle) }
        LiveChannelsScreen(
            vm = vm,
            categories = sortedCats,
            selectedCategoryId = selectedCat!!,
            channels = filteredChannels,
            isCategoryAdult = cat?.isAdult == true,
            isParentalUnlocked = parental.isUnlocked(),
            loading = channels.loading,
            onCategorySelected = { catId ->
                val nextCat = cats.items.firstOrNull { it.id == catId }
                if (nextCat?.isAdult == true && !parental.isUnlocked()) {
                    pendingCategory = nextCat
                } else {
                    selectedCat = catId
                    vm.loadChannels(catId)
                }
            },
            onPlay = { ch ->
                val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                if (locked) {
                    pendingChannel = PlayerArgs(
                        kind = PlayerKind.LIVE,
                        streamId = ch.id,
                        title = ch.name,
                        containerExtension = null
                    )
                } else {
                    playChannelDirect(ch)
                }
            },
            countByCategory = vm.liveCountByCategory.collectAsState().value,
            modifier = Modifier.fillMaxSize()
        )
        // Pendings (parental) ainda precisam aparecer mesmo no fluxo TV.
        pendingCategory?.let { c ->
            ParentalPinDialog(
                expectedPin = settings.parentalPin,
                onUnlocked = {
                    parental.unlock()
                    pendingCategory = null
                    selectedCat = c.id
                    vm.loadChannels(c.id)
                },
                onCancel = { pendingCategory = null },
                onPinCreated = { vm.setParentalPin(it) }
            )
        }
        pendingChannel?.let { args ->
            ParentalPinDialog(
                expectedPin = settings.parentalPin,
                onUnlocked = {
                    parental.unlock()
                    pendingChannel = null
                    onPlay(args)
                },
                onCancel = { pendingChannel = null },
                onPinCreated = { vm.setParentalPin(it) }
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
        // Em TV/Tablet, o grid de categorias é um estado transitório (some
        // assim que a primeira categoria é auto-selecionada). Renderizá-lo
        // causava um flash visível antes do LiveChannelsScreen aparecer.
        // Substituímos pelo loading enquanto a auto-seleção não ocorre.
        if (selectedCat == null && isTvLike) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(stringResource(R.string.loading))
            }
        } else if (selectedCat == null) {
            // Header: title on the left, inline filter on the right (TV/Tablet).
            // Phone keeps the stacked layout — narrow viewport can't share the row.
            val isPhoneCats = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            if (isPhoneCats) {
                Text(
                    stringResource(R.string.section_categories),
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.section_categories),
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

            // FTS hits across all categories.
            var foundChannels by remember { mutableStateOf<List<com.iptv.app.domain.model.LiveChannel>>(emptyList()) }
            androidx.compose.runtime.LaunchedEffect(needleCat) {
                foundChannels = vm.searchLiveByName(needleCat)
            }

            if (cats.loading && cats.items.isEmpty()) Text(stringResource(R.string.loading))
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadLiveCategories(forceRefresh = true) }) }

            if (foundChannels.isNotEmpty()) {
                Text(
                    stringResource(R.string.section_live_default) + " (${foundChannels.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    lazyItems(foundChannels) { ch ->
                        com.iptv.app.ui.common.ChannelCard(
                            title = ch.name,
                            number = ch.num,
                            logoUrl = ch.logoUrl
                        ) { playChannelDirect(ch) }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = cats.loading,
                onRefresh = { vm.loadLiveCategories(forceRefresh = true) },
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
                                vm.loadChannels(cat.id)
                            }
                        }
                    }
                }
            }
        } else {
            val sectionDefault = stringResource(R.string.section_live_default)
            val categoryName = cats.items.firstOrNull { it.id == selectedCat }?.name ?: sectionDefault
            val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            val needle = localFilter.trim().lowercase()
            val filteredChannels = if (needle.isBlank()) channels.items
                else channels.items.filter { it.name.lowercase().contains(needle) }

            if (isPhone) {
                // Phone keeps the original stacked layout — narrow screen has
                // no room for an inline filter beside the title.
                Text(
                    categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    SortMenuButton(
                        current = settings.liveSort,
                        options = SortOption.LIVE_OPTIONS
                    ) { vm.setSort(SortScope.LIVE, it) }
                }
                if (channels.loading && channels.items.isEmpty()) Text(stringResource(R.string.loading))
                channels.error?.let { ErrorState(message = it, onRetry = { vm.loadChannels(selectedCat, forceRefresh = true) }) }
                LocalFilterField(
                    value = localFilter,
                    onValueChange = { localFilter = it },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(dim.ChannelGridColumns),
                    horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                    verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
                ) {
                    items(filteredChannels) { ch ->
                        val cat = cats.items.firstOrNull { it.id == selectedCat }
                        val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                        val now = ch.epgChannelId?.let { epgNow[it] }
                        val nowProgress = now?.let {
                            val span = (it.stopMs - it.startMs).coerceAtLeast(1)
                            ((System.currentTimeMillis() - it.startMs).toFloat() / span)
                                .coerceIn(0f, 1f)
                        }
                        ChannelCard(
                            title = ch.name,
                            number = ch.num,
                            logoUrl = ch.logoUrl,
                            locked = locked,
                            nowPlaying = now?.title,
                            nowProgress = nowProgress
                        ) {
                            if (locked) {
                                pendingChannel = PlayerArgs(
                                    kind = PlayerKind.LIVE,
                                    streamId = ch.id,
                                    title = ch.name,
                                    containerExtension = null
                                )
                            } else {
                                playChannelDirect(ch)
                            }
                        }
                    }
                }
            } else {
                // TV/Tablet: layout inspirado no Smarters Player Lite —
                // categorias permanentes à esquerda, grid de canais com
                // logos grandes à direita, PIP do canal focado no canto.
                val cat = cats.items.firstOrNull { it.id == selectedCat }
                val sortedCats = remember(cats.items) { cats.items.sortedForDisplay() }
                LiveChannelsScreen(
                    vm = vm,
                    categories = sortedCats,
                    selectedCategoryId = selectedCat!!,
                    channels = filteredChannels,
                    isCategoryAdult = cat?.isAdult == true,
                    isParentalUnlocked = parental.isUnlocked(),
                    loading = channels.loading,
                    onCategorySelected = { catId ->
                        val nextCat = cats.items.firstOrNull { it.id == catId }
                        if (nextCat?.isAdult == true && !parental.isUnlocked()) {
                            pendingCategory = nextCat
                        } else {
                            selectedCat = catId
                            vm.loadChannels(catId)
                        }
                    },
                    onPlay = { ch ->
                        val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                        if (locked) {
                            pendingChannel = PlayerArgs(
                                kind = PlayerKind.LIVE,
                                streamId = ch.id,
                                title = ch.name,
                                containerExtension = null
                            )
                        } else {
                            playChannelDirect(ch)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
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
                vm.loadChannels(cat.id)
            },
            onCancel = { pendingCategory = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }
    pendingChannel?.let { args ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingChannel = null
                onPlay(args)
            },
            onCancel = { pendingChannel = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }
}
