package com.iptv.app.ui.continueWatching

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.MovieCacheEntity
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.data.db.SeriesCacheEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.data.reco.Recommender
import com.iptv.app.domain.model.toModel
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.iptv.app.domain.model.ContentType
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContinueWatchingViewModel @Inject constructor(
    private val movieProgressDao: MovieProgressDao,
    private val seriesProgressDao: SeriesProgressDao,
    private val episodeProgressDao: EpisodeProgressDao,
    private val liveHistoryDao: com.iptv.app.data.db.LiveHistoryDao,
    private val categoryCache: com.iptv.app.data.db.CategoryCacheDao,
    private val currentProfile: CurrentProfile,
    private val recommender: Recommender,
    private val xtream: com.iptv.app.data.api.XtreamRepository,
    settings: SettingsStore
) : ViewModel() {
    private val profileIdFlow = settings.flow
        .map { currentProfile.id() }
        .distinctUntilChanged()

    // Conjunto de ids de categorias adultas por tipo — usado para esconder
    // conteúdo protegido dos históricos, inclusive entradas gravadas antes
    // do filtro existir.
    private fun adultIds(type: ContentType) = categoryCache.observe(type)
        .map { cats -> cats.filter { it.isAdult }.map { it.id }.toSet() }
        .distinctUntilChanged()

    val movies = combine(
        profileIdFlow.flatMapLatest { movieProgressDao.observeInProgress(it) },
        adultIds(ContentType.MOVIE)
    ) { list, adult -> list.filterNot { it.categoryId != null && it.categoryId in adult } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val series = profileIdFlow
        .flatMapLatest { seriesProgressDao.observeRecent(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentChannels = combine(
        // Busca a mais que 10 porque o filtro abaixo pode remover alguns.
        profileIdFlow.flatMapLatest { liveHistoryDao.observeRecent(it, limit = 30) },
        adultIds(ContentType.LIVE)
    ) { list, adult ->
        list.filterNot { it.categoryId != null && it.categoryId in adult }.take(10)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Recomendações removidas a pedido do usuário — o Recommender fazia
    // queries pesadas no banco ao abrir a Início e dava travadinhas ao
    // entrar em Filmes/Séries logo em seguida.
    fun refreshRecommendations() { /* no-op */ }

    suspend fun episodePercent(episodeId: String): Int {
        val ep = episodeProgressDao.getById(currentProfile.id(), episodeId) ?: return 0
        if (ep.durationMs <= 0L || ep.watched) return 0
        return ((ep.positionMs * 100L) / ep.durationMs).toInt().coerceIn(0, 100)
    }

    /**
     * Decide qual episódio tocar a partir do card "Continuar séries":
     *  - se o último visto NÃO foi 100% concluído → continua nele;
     *  - se foi concluído → busca o próximo episódio da série (próximo
     *    número na mesma temporada; se acabou, primeiro da próxima);
     *  - fallback: o próprio último episódio.
     */
    suspend fun resolveNextEpisode(item: SeriesProgressEntity): PlayerArgs {
        val pid = currentProfile.id()
        val last = episodeProgressDao.getById(pid, item.lastEpisodeId)
        val needsNext = last?.watched == true
        if (!needsNext) {
            return PlayerArgs(
                kind = PlayerKind.EPISODE,
                streamId = item.lastEpisodeId.toIntOrNull() ?: 0,
                title = item.title,
                containerExtension = null,
                seriesId = item.seriesId,
                seasonNumber = item.lastSeasonNumber,
                episodeId = item.lastEpisodeId,
                posterUrl = item.coverUrl
            )
        }
        // Concluído: buscar o próximo via seriesInfo.
        val next = runCatching {
            val resp = xtream.seriesInfo(item.seriesId)
            val all = resp.normalizedEpisodes()
                .flatMap { (key, list) ->
                    val fb = key.toIntOrNull() ?: 0
                    list.map { it.toModel(item.seriesId, fb) }
                }
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNum }))
            val idx = all.indexOfFirst { it.id == item.lastEpisodeId }
            all.getOrNull(idx + 1)
        }.getOrNull()
        return if (next != null) {
            PlayerArgs(
                kind = PlayerKind.EPISODE,
                streamId = next.id.toIntOrNull() ?: 0,
                title = next.title,
                containerExtension = next.containerExtension,
                seriesId = next.seriesId,
                seasonNumber = next.seasonNumber,
                episodeId = next.id,
                posterUrl = item.coverUrl
            )
        } else {
            // Sem próximo (série terminou) — toca o último mesmo.
            PlayerArgs(
                kind = PlayerKind.EPISODE,
                streamId = item.lastEpisodeId.toIntOrNull() ?: 0,
                title = item.title,
                containerExtension = null,
                seriesId = item.seriesId,
                seasonNumber = item.lastSeasonNumber,
                episodeId = item.lastEpisodeId,
                posterUrl = item.coverUrl
            )
        }
    }
}

@Composable
fun ContinueWatchingScreen(
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (com.iptv.app.ui.home.SeriesOpenArgs) -> Unit = { },
    // Callback separado para filmes recomendados (sem progresso). Em vez de
    // tocar direto como Continue Watching, abre a tela de detalhe com sinopse
    // — o usuário ainda não decidiu se quer ver.
    onOpenMovie: (PlayerArgs) -> Unit = onPlay,
    vm: ContinueWatchingViewModel = hiltViewModel()
) {
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val recentChannels by vm.recentChannels.collectAsState()
    val dim = rememberTvDim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dim.ScreenPadding, vertical = 6.dp)
    ) {
        val nothingToContinue = movies.isEmpty() && series.isEmpty() && recentChannels.isEmpty()
        if (nothingToContinue) {
            // Skeleton placeholder enquanto o cache populado dispara as
            // listas — dá a sensação de tela respondendo em vez de
            // "Nada por aqui" na primeira vez.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.iptv.app.ui.common.SkeletonSection(label = "")
                com.iptv.app.ui.common.SkeletonSection(label = "")
            }
            return
        }

        // Tamanhos compactos para caber pelo menos duas linhas de cards
        // sem scroll na primeira dobra em TV.
        var hasPriorSection = false
        val sectionTopPadding = { if (hasPriorSection) 8.dp else 0.dp }
        val headerStyle = MaterialTheme.typography.titleSmall
        val rowSpacing = 12.dp
        // Animação de entrada: cada seção aparece com slide-up + fade,
        // numa cascata de 80ms entre elas (mais natural que tudo de uma vez).
        var sectionIndex = 0
        @androidx.compose.runtime.Composable
        fun AnimatedSection(content: @androidx.compose.runtime.Composable () -> Unit) {
            val visible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val delayMs = sectionIndex * 80
            sectionIndex++
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(delayMs.toLong())
                visible.value = true
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = visible.value,
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(250)) +
                    androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(250)
                    )
            ) {
                Column { content() }
            }
        }

        if (recentChannels.isNotEmpty()) AnimatedSection {
            Text(
                stringResource(R.string.recent_channels),
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(recentChannels) { ch ->
                    ChannelMiniHomeCard(
                        title = ch.name,
                        logoUrl = ch.logoUrl,
                        width = HomePosterWidth
                    ) {
                        onPlay(
                            PlayerArgs(
                                kind = PlayerKind.LIVE,
                                streamId = ch.channelId,
                                title = ch.name,
                                containerExtension = null,
                                posterUrl = ch.logoUrl
                            )
                        )
                    }
                }
            }
            hasPriorSection = true
        }

        if (series.isNotEmpty()) AnimatedSection {
            Text(
                stringResource(R.string.continue_series),
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(series) { s -> SeriesContinueCard(s, vm, onOpenSeries) }
            }
            hasPriorSection = true
        }

        if (movies.isNotEmpty()) AnimatedSection {
            Text(
                stringResource(R.string.continue_movies),
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(movies) { m -> MovieContinueCard(m, onPlay) }
            }
            hasPriorSection = true
        }

        // Recomendações removidas: o Recommender consultava o catálogo
        // inteiro (50k+ filmes) e travava a Início. Vale repensar depois
        // como um cache pré-computado em WorkManager.
    }
}

// Pôsteres do Início em 96dp (altura 144dp na razão 2:3). Antes 110dp
// somava ~720dp com 3 seções e ainda forçava scroll vertical na TV 1080p
// — agora as 3 caem confortáveis na dobra. ChannelMiniHomeCard usa o
// mesmo width pra deixar a linha de canais com a mesma altura visual.
private val HomePosterWidth = 96.dp

@Composable
private fun MovieContinueCard(item: MovieProgressEntity, onPlay: (PlayerArgs) -> Unit) {
    val percent = if (item.durationMs > 0) (item.positionMs * 100 / item.durationMs).toInt().coerceIn(0, 100) else 0
    // Progresso agora vai dentro do card (barra de 3dp sobreposta).
    PosterCard(
        title = item.title,
        imageUrl = item.posterUrl,
        fallbackIcon = Icons.Filled.Movie,
        overrideWidth = HomePosterWidth,
        progressPercent = percent
    ) {
        onPlay(
            PlayerArgs(
                kind = PlayerKind.MOVIE,
                streamId = item.movieId,
                title = item.title,
                containerExtension = item.containerExtension,
                posterUrl = item.posterUrl,
                categoryId = item.categoryId,
                startPositionMs = item.positionMs
            )
        )
    }
}

@Composable
private fun SeriesContinueCard(
    item: SeriesProgressEntity,
    vm: ContinueWatchingViewModel,
    onOpenSeries: (com.iptv.app.ui.home.SeriesOpenArgs) -> Unit
) {
    var percent by androidx.compose.runtime.remember(item.lastEpisodeId) {
        androidx.compose.runtime.mutableStateOf(0)
    }
    androidx.compose.runtime.LaunchedEffect(item.lastEpisodeId) {
        percent = vm.episodePercent(item.lastEpisodeId)
    }
    PosterCard(
        title = "${item.title}\nT${item.lastSeasonNumber}E${item.lastEpisodeNum}",
        imageUrl = item.coverUrl,
        fallbackIcon = Icons.Filled.Tv,
        overrideWidth = HomePosterWidth,
        progressPercent = percent
    ) {
        // Abre o detalhe da série com a temporada do último episódio já
        // selecionada (e o diálogo de episódios aberto). O usuário pediu
        // ver a lista pra escolher — em vez de tocar direto.
        onOpenSeries(
            com.iptv.app.ui.home.SeriesOpenArgs(
                id = item.seriesId,
                title = item.title,
                cover = item.coverUrl,
                initialSeasonNumber = item.lastSeasonNumber,
                initialEpisodeId = item.lastEpisodeId
            )
        )
    }
}

@Composable
private fun RecommendedMovieCard(item: MovieCacheEntity, onPlay: (PlayerArgs) -> Unit) {
    PosterCard(
        title = item.name,
        imageUrl = item.posterUrl,
        fallbackIcon = Icons.Filled.Movie,
        overrideWidth = HomePosterWidth
    ) {
        onPlay(
            PlayerArgs(
                kind = PlayerKind.MOVIE,
                streamId = item.streamId,
                title = item.name,
                containerExtension = item.containerExtension,
                posterUrl = item.posterUrl,
                categoryId = item.categoryId
            )
        )
    }
}

@Composable
private fun RecommendedSeriesCard(
    item: SeriesCacheEntity,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit
) {
    PosterCard(
        title = item.name,
        imageUrl = item.coverUrl,
        fallbackIcon = Icons.Filled.Tv,
        overrideWidth = HomePosterWidth
    ) {
        onOpenSeries(item.seriesId, item.name, item.coverUrl)
    }
}

/**
 * Card de canal para a tela Início — logo dentro de um quadrado (Fit, sem
 * crop) e nome em uma faixa logo abaixo. Antes usávamos PosterCard com
 * aspect 2:3 + título sobreposto: as logos largas (ESPN, SBT, Discovery)
 * eram cortadas e o nome ficava por cima da imagem.
 */
@Composable
private fun ChannelMiniHomeCard(
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
                        Icons.Filled.Tv,
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
private fun ProgressStripe(percent: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent / 100f)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.CenterStart)
        )
    }
}
