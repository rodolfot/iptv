package com.iptv.app.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.domain.model.ContentType
import com.iptv.app.ui.common.ChannelCard
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.FormFactor
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.PreviewPlayer
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.parental.ParentalPinDialog
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

@Composable
fun FavoritesScreen(
    vm: HomeViewModel,
    parental: ParentalSession,
    onPlay: (PlayerArgs) -> Unit
) {
    val all by vm.favorites.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val dim = rememberTvDim()
    val pendingPlayState = remember { mutableStateOf<PlayerArgs?>(null) }

    // Sempre alfabético (A→Z) — usuário pediu ordenação fixa.
    fun sortFor(list: List<FavoriteEntity>): List<FavoriteEntity> =
        list.sortedBy { it.name.lowercase() }

    val channels = sortFor(all.filter { it.type == ContentType.LIVE })
    val movies = sortFor(all.filter { it.type == ContentType.MOVIE })
    val series = sortFor(all.filter { it.type == ContentType.SERIES })

    // Item atualmente sob foco — pode ser canal, filme ou série. Série não
    // mostra preview (usuário pediu pra economizar banda + não faz sentido
    // visualizar episódio aleatório).
    var focusedFavorite by remember { mutableStateOf<FavoriteEntity?>(null) }

    fun play(entry: FavoriteEntity) {
        onPlay(
            PlayerArgs(
                kind = PlayerKind.LIVE,
                streamId = entry.itemId,
                title = entry.name,
                containerExtension = null
            )
        )
    }
    fun playMovie(entry: FavoriteEntity) {
        onPlay(
            PlayerArgs(
                kind = PlayerKind.MOVIE,
                streamId = entry.itemId,
                title = entry.name,
                containerExtension = entry.containerExtension,
                posterUrl = entry.logoUrl,
                categoryId = entry.categoryId
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.tab_favorites), style = MaterialTheme.typography.titleLarge)

        if (all.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_favorites_title),
                message = stringResource(R.string.empty_favorites_message),
                icon = Icons.Filled.Favorite
            )
            return
        }

        val isPhone = dim.formFactor == FormFactor.Phone
        if (isPhone) {
            // Telefone não tem espaço pra painel de preview lateral — só as
            // seções, ocupando a largura toda.
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (channels.isNotEmpty()) {
                    MediaRowSection(stringResource(R.string.filter_channels), channels.size) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                            items(channels, key = { "live-${it.itemId}" }) { f ->
                                ChannelCard(title = f.name, number = null, logoUrl = f.logoUrl) { play(f) }
                            }
                        }
                    }
                }
                if (movies.isNotEmpty()) {
                    MediaRowSection(stringResource(R.string.filter_movies), movies.size) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                            items(movies, key = { "movie-${it.itemId}" }) { f ->
                                PosterCard(
                                    title = f.name,
                                    imageUrl = f.logoUrl,
                                    fallbackIcon = Icons.Filled.Movie
                                ) { playMovie(f) }
                            }
                        }
                    }
                }
                if (series.isNotEmpty()) {
                    MediaRowSection(stringResource(R.string.filter_series), series.size) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                            items(series, key = { "series-${it.itemId}" }) { f ->
                                PosterCard(
                                    title = f.name,
                                    imageUrl = f.logoUrl,
                                    fallbackIcon = Icons.Filled.Tv,
                                    onClick = { /* abertura de série não tem rota aqui — TODO */ }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // TV/Tablet: seções empilhadas à esquerda (canais/filmes/séries em
            // linhas horizontais, mesmo padrão visual de Filmes/Séries/Ao Vivo)
            // + painel de preview à direita, atualizado pelo item em foco.
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(3f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (channels.isNotEmpty()) {
                        MediaRowSection(stringResource(R.string.filter_channels), channels.size) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                                items(channels, key = { "live-${it.itemId}" }) { f ->
                                    Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedFavorite = f }) {
                                        ChannelCard(title = f.name, number = null, logoUrl = f.logoUrl) { play(f) }
                                    }
                                }
                            }
                        }
                    }
                    if (movies.isNotEmpty()) {
                        MediaRowSection(stringResource(R.string.filter_movies), movies.size) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                                items(movies, key = { "movie-${it.itemId}" }) { f ->
                                    Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedFavorite = f }) {
                                        PosterCard(
                                            title = f.name,
                                            imageUrl = f.logoUrl,
                                            fallbackIcon = Icons.Filled.Movie
                                        ) { playMovie(f) }
                                    }
                                }
                            }
                        }
                    }
                    if (series.isNotEmpty()) {
                        MediaRowSection(stringResource(R.string.filter_series), series.size) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                                items(series, key = { "series-${it.itemId}" }) { f ->
                                    Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedFavorite = f }) {
                                        PosterCard(
                                            title = f.name,
                                            imageUrl = f.logoUrl,
                                            fallbackIcon = Icons.Filled.Tv,
                                            onClick = { /* abertura de série não tem rota aqui — TODO */ }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                FavoritesPreviewPanel(
                    focused = focusedFavorite,
                    vm = vm,
                    modifier = Modifier.weight(2f).fillMaxHeight()
                )
            }
        }
    }

    pendingPlayState.value?.let { args ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingPlayState.value = null
                onPlay(args)
            },
            onCancel = { pendingPlayState.value = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }
}

/** Cabeçalho "Título (N)" seguido do conteúdo — mesmo padrão usado nas
 *  seções do Início e das telas de categoria. */
@Composable
private fun MediaRowSection(label: String, count: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label ($count)", style = MaterialTheme.typography.titleSmall)
        content()
    }
}

/**
 * Painel direito de Favoritos. Para LIVE e MOVIE renderiza o PreviewPlayer
 * (debounce + retry + áudio); para SERIES mostra só o pôster grande, já que
 * tocar um episódio aleatório não faz sentido pro usuário.
 */
@Composable
private fun FavoritesPreviewPanel(
    focused: FavoriteEntity?,
    vm: HomeViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (focused == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.favorites_preview_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }
        when (focused.type) {
            ContentType.SERIES -> {
                // Sem player — pôster grande + título.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        if (!focused.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = focused.logoUrl,
                                contentDescription = focused.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(
                                Icons.Filled.Tv,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(48.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                    Text(
                        focused.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ContentType.LIVE -> {
                val url by produceState<String?>(initialValue = null, focused.itemId) {
                    value = vm.previewUrl(focused.itemId)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewPlayer(streamUrl = url)
                    Text(
                        focused.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ContentType.MOVIE -> {
                val url by produceState<String?>(initialValue = null, focused.itemId) {
                    value = vm.moviePreviewUrl(focused.itemId, focused.containerExtension)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewPlayer(streamUrl = url)
                    Text(
                        focused.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
