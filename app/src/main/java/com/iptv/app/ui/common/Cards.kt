@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage

/**
 * Card clicável que funciona com touch em celular/tablet/multimídia de carro
 * e com D-pad em TV Leanback.
 *
 * Em dispositivos touch (`useTouchUi`) usamos um `Surface` com
 * `combinedClickable` — `androidx.tv.material3.Card` só aceita clique após
 * receber foco do D-pad, e isso prendia o usuário em telas touch puras (vide
 * multimídia de carro Android, onde o app ficava travado na tela inicial).
 *
 * Em TV mantemos `androidx.tv.material3.Card` para preservar o highlight de
 * foco do controle remoto. `scale = 1f` em todos os estados — o usuário
 * removeu o efeito de zoom ao focar no app inteiro.
 *
 * Público (em vez de privado) porque telas que renderizam tiles próprios
 * (canais, episódios) precisam do mesmo wrapper para também ganhar touch.
 */
@Composable
fun TouchableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val dim = rememberTvDim()
    if (dim.useTouchUi) {
        @OptIn(ExperimentalFoundationApi::class)
        androidx.compose.material3.Surface(
            modifier = modifier
                .clip(shape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = shape
        ) { content() }
    } else {
        Card(
            onClick = onClick,
            onLongClick = onLongClick ?: onClick,
            modifier = modifier,
            shape = CardDefaults.shape(shape = shape),
            scale = CardDefaults.scale(
                scale = 1f,
                focusedScale = 1f,
                pressedScale = 1f,
            )
        ) { content() }
    }
}

@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    locked: Boolean = false,
    fallbackIcon: ImageVector = Icons.Filled.Movie,
    fillWidth: Boolean = false,
    rating: Double? = null,
    overrideWidth: androidx.compose.ui.unit.Dp? = null,
    /** Quando true, usa labelSmall no rótulo do card — usado em Favoritos/Watchlist
     *  para caber mais itens visíveis. */
    compactTitle: Boolean = false,
    /** Progresso de reprodução em 0..100 — quando setado, desenha uma barra
     *  azul na base do card sobreposta à barra de título. */
    progressPercent: Int? = null,
    /** Quando true, mostra um badge "assistido" (check verde) no canto
     *  superior direito. Tem precedência sobre o ícone de meio assistido. */
    watched: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val dim = rememberTvDim()
    // Foco do card — quando focado, o título rola (marquee) caso seja maior
    // do que o espaço disponível.
    var focused by remember { mutableStateOf(false) }
    val baseModifier = when {
        fillWidth -> Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        overrideWidth != null -> Modifier.width(overrideWidth).aspectRatio(2f / 3f)
        dim.formFactor == FormFactor.Phone -> Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        else -> Modifier.width(dim.PosterCardW).height(dim.PosterCardH)
    }
    val cardModifier = baseModifier.onFocusChanged { focused = it.isFocused }
    TouchableCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = cardModifier
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (imageUrl.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(fallbackIcon, contentDescription = null, modifier = Modifier.size(64.dp))
                }
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    // Crop instead of the default Fit so posters always fill
                    // the card. Providers ship images at unpredictable aspect
                    // ratios; Fit leaves dark bands on the sides that made the
                    // recommendation row look ragged.
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                )
            }
            if (locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = Color.White)
                }
            }
            // Badge de status assistido — só aparece quando NÃO está bloqueado
            // (Lock também usa TopEnd). watched > inProgress: o usuário marcou
            // como visto, então não interessa o percentual.
            val showWatched = watched && !locked
            val showInProgress = !watched && !locked &&
                progressPercent != null && progressPercent in 1..99
            if (showWatched || showInProgress) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showWatched) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.a11y_watched),
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.HourglassBottom,
                            contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.a11y_in_progress),
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            // Rating chip — only when the provider returned something useful.
            // Many catalogs return 0.0 for "unknown"; treat that as missing
            // so we don't show a meaningless "★ 0.0" on every card.
            if (rating != null && rating > 0.0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        " %.1f".format(rating),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xEE000000))
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                // Título sempre em labelSmall (10sp) para ficar idêntico ao
                // ChannelTile do Ao Vivo. Antes Filmes/Séries usavam
                // titleSmall (14sp) e Live labelSmall, ficando desalinhados.
                // Quando focado, basicMarquee rola texto longo.
                @OptIn(ExperimentalFoundationApi::class)
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = (if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    else Modifier)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            // Barra de progresso (azul) na base do card — só aparece quando
            // o caller passar `progressPercent` (cards de Continuar).
            if (progressPercent != null && progressPercent in 1..100) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x55FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressPercent / 100f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelCard(
    title: String,
    number: Int?,
    logoUrl: String?,
    locked: Boolean = false,
    nowPlaying: String? = null,
    nowProgress: Float? = null,
    onClick: () -> Unit
) {
    val dim = rememberTvDim()
    var focused by remember { mutableStateOf(false) }
    val baseChannelModifier = if (dim.formFactor == FormFactor.Phone) {
        Modifier.fillMaxWidth().height(dim.ChannelCardH)
    } else {
        Modifier.width(dim.ChannelCardW).height(dim.ChannelCardH)
    }
    val channelModifier = baseChannelModifier.onFocusChanged { focused = it.isFocused }
    TouchableCard(
        onClick = onClick,
        modifier = channelModifier
    ) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                if (logoUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(48.dp))
                } else {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
                if (locked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = Color.White)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    if (number != null) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.iptv.app.R.string.channel_number, number),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    @OptIn(ExperimentalFoundationApi::class)
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        else Modifier
                    )
                    if (!nowPlaying.isNullOrBlank()) {
                        @OptIn(ExperimentalFoundationApi::class)
                        Text(
                            nowPlaying,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            else Modifier
                        )
                        if (nowProgress != null && nowProgress in 0f..1f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(nowProgress)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .height(2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card compacto de canal (logo quadrada + nome numa faixa embaixo) para
 * linhas horizontais densas — Início, Favoritos, Lista. Diferente de
 * [ChannelCard] (320dp, pensado pra grid de Ao Vivo), este recebe a largura
 * de fora pra caber mais itens por linha e não estourar a tela em altura.
 */
@Composable
fun CompactChannelCard(
    title: String,
    logoUrl: String?,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    TouchableCard(
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
                    Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(28.dp))
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryCard(
    title: String,
    count: Int?,
    locked: Boolean,
    onClick: () -> Unit
) {
    val dim = rememberTvDim()
    val isPhone = dim.formFactor == FormFactor.Phone
    val (w, h, pad) = when (dim.formFactor) {
        // Smaller, denser tiles on phone — caller asked to see more categories
        // per viewport without horizontal scroll.
        FormFactor.Phone -> Triple(170.dp, 68.dp, 10.dp)
        FormFactor.Tablet -> Triple(220.dp, 88.dp, 14.dp)
        FormFactor.Tv -> Triple(280.dp, 96.dp, 16.dp)
    }
    val categoryModifier = if (isPhone) {
        Modifier.fillMaxWidth().height(h)
    } else {
        Modifier.width(w).height(h)
    }
    TouchableCard(
        onClick = onClick,
        modifier = categoryModifier
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // Barra de destaque à esquerda — mesma linguagem visual do
            // DrawerCategoryItem, dá um acento de cor ao card que antes era
            // só texto sobre uma superfície lisa.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = pad + 6.dp, end = pad, top = pad, bottom = pad)
            ) {
                Text(
                    title,
                    style = when (dim.formFactor) {
                        FormFactor.Phone -> MaterialTheme.typography.titleSmall
                        FormFactor.Tablet -> MaterialTheme.typography.titleMedium
                        FormFactor.Tv -> MaterialTheme.typography.titleMedium
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (count != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "$count itens",
                            style = if (isPhone) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.a11y_locked),
                    modifier = Modifier.align(Alignment.TopEnd).padding(pad)
                )
            }
        }
    }
}
