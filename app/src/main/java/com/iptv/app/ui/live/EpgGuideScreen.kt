package com.iptv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.data.db.EpgProgrammeEntity
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.sortedForDisplay
import com.iptv.app.ui.common.ComboBox
import com.iptv.app.ui.common.ComboOption
import com.iptv.app.ui.common.InlineLoading
import com.iptv.app.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Dimensões da grade. HourWidth define a escala tempo→largura; blocos e régua
// derivam disso pra ficarem sempre alinhados.
private val ChannelColW = 128.dp
private val HourWidth = 220.dp
private val RowHeight = 60.dp
private val RulerHeight = 28.dp
private val RowGap = 4.dp

private fun minutesToDp(minutes: Float): Dp = HourWidth * (minutes / 60f)

/**
 * Guia de programação em grade (estilo TV): canais nas linhas, horário nas
 * colunas. A coluna de canais fica fixa à esquerda; a linha do tempo e a
 * régua de horas rolam juntas (scroll horizontal compartilhado). Uma linha
 * vermelha marca o "agora". OK/clique num programa abre o canal.
 */
@Composable
fun LiveEpgGuideLayout(
    vm: HomeViewModel,
    categories: List<Category>,
    selectedCategoryId: String,
    channels: List<LiveChannel>,
    onCategorySelected: (String) -> Unit,
    onPlay: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val guide by vm.epgGuide.collectAsState()
    // Recarrega a EPG do guia sempre que a lista de canais muda (troca de
    // categoria ou primeira carga).
    LaunchedEffect(channels) { vm.loadEpgGuide(channels) }

    val sharedH = rememberScrollState()
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val comboFocus = remember { FocusRequester() }
    val now = System.currentTimeMillis()

    val sortedCats = remember(categories) { categories.sortedForDisplay() }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val windowStart = guide.windowStartMs
    val windowEnd = guide.windowEndMs
    val totalHours = if (windowEnd > windowStart)
        ((windowEnd - windowStart) / 3600_000L).toInt().coerceAtLeast(1) else 1
    val timelineWidth = HourWidth * totalHours

    // Foco inicial na célula do primeiro canal — sempre focável, mesmo quando
    // a categoria não tem EPG. De lá: → entra na grade de programas, ↓ desce
    // pros outros canais, ↑ vai pro seletor de categoria.
    LaunchedEffect(channels) {
        kotlinx.coroutines.delay(80)
        runCatching { firstFocus.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Cabeçalho: seletor de categoria + relógio "agora".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            val options = remember(sortedCats) {
                sortedCats.map { ComboOption(id = it.id, label = it.name) }
            }
            val current = options.firstOrNull { it.id == selectedCategoryId }
            ComboBox(
                selected = current,
                options = options,
                onSelect = { onCategorySelected(it.id) },
                modifier = Modifier.width(320.dp).focusRequester(comboFocus)
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.guide_now_label, timeFmt.format(Date(now))),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (guide.loading && guide.byChannel.isEmpty()) {
            InlineLoading()
            return
        }
        if (channels.isEmpty()) {
            Text(stringResource(R.string.channel_no_epg), style = MaterialTheme.typography.bodyMedium)
            return
        }

        // Régua de horas: espaçador fixo (largura da coluna de canais) +
        // rótulos de hora rolando junto com a linha do tempo (sharedH).
        Row(modifier = Modifier.fillMaxWidth().height(RulerHeight)) {
            Spacer(Modifier.width(ChannelColW))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(sharedH)
            ) {
                Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
                    for (h in 0 until totalHours) {
                        val labelMs = windowStart + h * 3600_000L
                        Text(
                            timeFmt.format(Date(labelMs)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = HourWidth * h).padding(start = 4.dp)
                        )
                    }
                    NowLine(windowStart = windowStart, now = now)
                }
            }
        }

        // Corpo: uma linha por canal.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(RowGap)
        ) {
            items(channels, key = { it.id }) { channel ->
                val programmes = channel.epgChannelId?.let { guide.byChannel[it] }.orEmpty()
                GuideChannelRow(
                    channel = channel,
                    programmes = programmes,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    timelineWidth = timelineWidth,
                    now = now,
                    sharedH = sharedH,
                    timeFmt = timeFmt,
                    firstChannel = channel.id == channels.first().id,
                    firstFocus = firstFocus,
                    onOpen = { onPlay(channel) }
                )
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    channel: LiveChannel,
    programmes: List<EpgProgrammeEntity>,
    windowStart: Long,
    windowEnd: Long,
    timelineWidth: Dp,
    now: Long,
    sharedH: androidx.compose.foundation.ScrollState,
    timeFmt: SimpleDateFormat,
    firstChannel: Boolean,
    firstFocus: FocusRequester,
    onOpen: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(RowHeight)) {
        // Célula de canal fixa (não rola horizontalmente). Focável e clicável:
        // é o alvo garantido pra assistir o canal mesmo quando a linha não tem
        // EPG (nesses casos não há bloco de programa pra focar).
        var cellFocused by remember { mutableStateOf(false) }
        val cellShape = RoundedCornerShape(8.dp)
        Row(
            modifier = Modifier
                .width(ChannelColW)
                .fillMaxHeight()
                .padding(end = 4.dp)
                .then(if (firstChannel) Modifier.focusRequester(firstFocus) else Modifier)
                .clip(cellShape)
                .background(
                    if (cellFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
                .onFocusChanged { cellFocused = it.isFocused }
                .clickable(onClick = onOpen)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    AsyncImage(model = channel.logoUrl, contentDescription = channel.name, modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (cellFocused) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Linha do tempo do canal (rola com sharedH).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(sharedH)
        ) {
            Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
                if (programmes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(timelineWidth)
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            stringResource(R.string.channel_no_epg),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else {
                    programmes.forEach { p ->
                        val startClamped = p.startMs.coerceAtLeast(windowStart)
                        val endClamped = p.stopMs.coerceAtMost(windowEnd)
                        if (endClamped <= startClamped) return@forEach
                        val startX = minutesToDp((startClamped - windowStart) / 60000f)
                        val blockW = minutesToDp((endClamped - startClamped) / 60000f)
                        val isNow = p.startMs <= now && p.stopMs > now
                        ProgrammeBlock(
                            title = p.title,
                            timeText = timeFmt.format(Date(p.startMs)),
                            startX = startX,
                            width = blockW,
                            isNow = isNow,
                            onClick = onOpen
                        )
                    }
                }
                NowLine(windowStart = windowStart, now = now)
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(
    title: String,
    timeText: String,
    startX: Dp,
    width: Dp,
    isNow: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        isNow -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        isNow -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .offset(x = startX)
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(shape)
            .background(bg)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else Color.Transparent,
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column {
            Text(
                timeText,
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.8f),
                maxLines = 1
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Linha vertical vermelha marcando o instante atual dentro da linha do tempo. */
@Composable
private fun NowLine(windowStart: Long, now: Long) {
    if (now < windowStart) return
    val x = minutesToDp((now - windowStart) / 60000f)
    Box(
        modifier = Modifier
            .offset(x = x)
            .width(2.dp)
            .fillMaxHeight()
            .background(Color(0xFFE53935))
    )
}
