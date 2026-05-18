package com.iptv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.data.api.VodInfoDetail
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.FavoriteDao
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.WatchlistDao
import com.iptv.app.data.db.WatchlistEntity
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.domain.model.ContentType
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.player.PlayerArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieDetailUiState(
    val loading: Boolean = false,
    val info: VodInfoDetail? = null,
    val resumeMs: Long = 0L,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val movieProgressDao: MovieProgressDao,
    private val favoriteDao: FavoriteDao,
    private val watchlistDao: WatchlistDao,
    private val currentProfile: CurrentProfile
) : ViewModel() {
    private val _state = MutableStateFlow(MovieDetailUiState())
    val state = _state.asStateFlow()

    fun load(streamId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.vodInfo(streamId) }
                .onSuccess { resp ->
                    val pid = currentProfile.id()
                    val resume = movieProgressDao.getById(pid, streamId)
                        ?.takeIf { !it.watched }
                        ?.positionMs ?: 0L
                    _state.value = _state.value.copy(
                        loading = false,
                        info = resp.info,
                        resumeMs = resume,
                        isFavorite = isFavorite(pid, streamId),
                        isInWatchlist = isInWatchlist(pid, streamId)
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                }
        }
    }

    private suspend fun isFavorite(profileId: String, id: Int): Boolean = try {
        favoriteDao.observeAll(profileId).first()
            .any { it.type == ContentType.MOVIE && it.itemId == id }
    } catch (_: Throwable) { false }

    private suspend fun isInWatchlist(profileId: String, id: Int): Boolean = try {
        watchlistDao.observeAll(profileId).first()
            .any { it.type == ContentType.MOVIE && it.itemId == id }
    } catch (_: Throwable) { false }

    fun toggleFavorite(args: PlayerArgs) {
        viewModelScope.launch {
            val pid = currentProfile.id()
            val current = _state.value.isFavorite
            if (current) {
                favoriteDao.delete(pid, ContentType.MOVIE, args.streamId)
            } else {
                favoriteDao.insert(
                    FavoriteEntity(
                        profileId = pid,
                        type = ContentType.MOVIE,
                        itemId = args.streamId,
                        name = args.title,
                        logoUrl = args.posterUrl,
                        categoryId = args.categoryId,
                        containerExtension = args.containerExtension
                    )
                )
            }
            _state.value = _state.value.copy(isFavorite = !current)
        }
    }

    fun toggleWatchlist(args: PlayerArgs) {
        viewModelScope.launch {
            val pid = currentProfile.id()
            val current = _state.value.isInWatchlist
            if (current) {
                watchlistDao.delete(pid, ContentType.MOVIE, args.streamId)
            } else {
                watchlistDao.insert(
                    WatchlistEntity(
                        profileId = pid,
                        type = ContentType.MOVIE,
                        itemId = args.streamId,
                        name = args.title,
                        logoUrl = args.posterUrl,
                        categoryId = args.categoryId,
                        containerExtension = args.containerExtension
                    )
                )
            }
            _state.value = _state.value.copy(isInWatchlist = !current)
        }
    }
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun MovieDetailScreen(
    args: PlayerArgs,
    onPlay: (PlayerArgs) -> Unit,
    onBack: () -> Unit,
    vm: MovieDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val dim = rememberTvDim()
    val snackbar = LocalSnackbar.current
    val addedMsg = stringResource(R.string.snack_favorite_added)
    val removedMsg = stringResource(R.string.snack_favorite_removed)
    val watchlistAddedMsg = stringResource(R.string.snack_watchlist_added)
    val watchlistRemovedMsg = stringResource(R.string.snack_watchlist_removed)
    LaunchedEffect(args.streamId) { vm.load(args.streamId) }
    androidx.activity.compose.BackHandler(onBack = onBack)

    val playFocus = remember { FocusRequester() }
    val synopsisBringIntoView = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    // Pull focus to the primary action once data is in. Detail screens always
    // open with the Back button as the natural first focusable; the user wants
    // to start playback, not exit.
    LaunchedEffect(state.info) {
        if (state.info != null) {
            runCatching { playFocus.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tamanhos compactos para que tudo (cabeçalho + metadados +
        // botões + sinopse) caiba sem scroll na primeira dobra.
        val (posterW, posterH) = when (dim.formFactor) {
            com.iptv.app.ui.common.FormFactor.Phone -> 120.dp to 180.dp
            com.iptv.app.ui.common.FormFactor.Tablet -> 160.dp to 240.dp
            com.iptv.app.ui.common.FormFactor.Tv -> 180.dp to 270.dp
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone) 12.dp else 20.dp)) {
            Box(
                modifier = Modifier
                    .width(posterW)
                    .height(posterH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (args.posterUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.Movie, contentDescription = null)
                } else {
                    AsyncImage(model = args.posterUrl, contentDescription = args.title)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    args.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                state.info?.let { info ->
                    info.releasedate?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_release, it), style = MaterialTheme.typography.bodySmall)
                    }
                    info.rating?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_rating, it), style = MaterialTheme.typography.bodySmall)
                    }
                    info.duration?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_duration, it), style = MaterialTheme.typography.bodySmall)
                    }
                    info.genre?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            stringResource(R.string.movie_genre, it),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    info.director?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            stringResource(R.string.movie_director, it),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    info.cast?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            stringResource(R.string.movie_cast, it),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                state.error?.let {
                    Text(stringResource(R.string.error_prefix, it), color = MaterialTheme.colorScheme.error)
                }

                // FlowRow quebra para a próxima linha quando faltam pixels —
                // antes em TV com Continuar (texto longo) + Reiniciar +
                // Favorito + bandeirinha, o último era empurrado pra fora.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (state.resumeMs > 0L) {
                        TouchableButton(
                            onClick = { onPlay(args.copy(startPositionMs = state.resumeMs)) },
                            modifier = Modifier.focusRequester(playFocus)
                        ) {
                            Text(stringResource(R.string.movie_resume, formatTime(state.resumeMs)))
                        }
                        TouchableButton(onClick = { onPlay(args.copy(startPositionMs = 0L)) }) {
                            Text(stringResource(R.string.movie_restart))
                        }
                    } else {
                        TouchableButton(
                            onClick = { onPlay(args) },
                            modifier = Modifier.focusRequester(playFocus)
                        ) {
                            Text(stringResource(R.string.movie_play))
                        }
                    }
                    TouchableButton(onClick = {
                        val wasFavorite = state.isFavorite
                        vm.toggleFavorite(args)
                        snackbar?.show(if (wasFavorite) removedMsg else addedMsg)
                    }) {
                        Text(stringResource(
                            if (state.isFavorite) R.string.remove_favorite else R.string.add_favorite
                        ))
                    }
                    // Bandeirinha sempre apenas ícone — usuário pediu para
                    // manter só o ícone (estava sumindo do layout em algumas
                    // configurações por falta de espaço).
                    TouchableButton(onClick = {
                        val wasInList = state.isInWatchlist
                        vm.toggleWatchlist(args)
                        snackbar?.show(if (wasInList) watchlistRemovedMsg else watchlistAddedMsg)
                    }) {
                        androidx.compose.material3.Icon(
                            if (state.isInWatchlist) Icons.Filled.Bookmark
                            else Icons.Filled.BookmarkBorder,
                            contentDescription = stringResource(
                                if (state.isInWatchlist) R.string.watchlist_remove else R.string.watchlist_add
                            )
                        )
                    }
                }
            }
        }

        state.info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            // Sinopse precisa ser focável para o D-pad descer até aqui e levar
            // o scroll junto — sem isso, em TV (sem touch) o usuário não tem
            // como ler o texto inteiro.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(synopsisBringIntoView)
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch { synopsisBringIntoView.bringIntoView() }
                        }
                    }
                    .focusable()
            ) {
                Text(
                    stringResource(R.string.synopsis),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    plot,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
