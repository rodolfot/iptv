package com.iptv.app.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Largura da gaveta de categorias aberta (igual em todas as telas). */
val DrawerExpandedWidth: Dp = 280.dp

/** Largura do trilho quando a gaveta recolhe (TV com foco no conteúdo). */
val DrawerCollapsedWidth: Dp = 64.dp

/**
 * Gaveta lateral + conteúdo. Na TV ([collapsible] = true) a gaveta é
 * retrátil, no padrão das interfaces de 10 pés: abre (280dp) quando o D-pad
 * entra nela e recolhe num trilho de 64dp — ícone + nome da categoria atual na
 * vertical — quando o foco vai para a grade, devolvendo espaço ao conteúdo.
 *
 * Aberta, a gaveta se sobrepõe ao conteúdo em vez de empurrá-lo: a grade não
 * muda de número de colunas a cada abre/fecha, então os cards não "pulam" de
 * lugar e o item focado continua onde estava. Os itens da gaveta são sempre
 * medidos com a largura cheia (só recortados quando recolhida) para a lista não
 * mudar de altura nem de rolagem ao animar.
 *
 * Em telas touch a gaveta fica fixa, como antes.
 */
@Composable
fun CollapsibleDrawerLayout(
    collapsible: Boolean,
    /** Texto mostrado no trilho recolhido (ex.: categoria selecionada). */
    collapsedLabel: String,
    drawer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!collapsible) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(Modifier.width(DrawerExpandedWidth).fillMaxHeight()) { drawer() }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
        return
    }
    var drawerHasFocus by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (drawerHasFocus) DrawerExpandedWidth else DrawerCollapsedWidth,
        animationSpec = tween(durationMillis = 180),
        label = "drawer-width"
    )
    val railAlpha by animateFloatAsState(
        targetValue = if (drawerHasFocus) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "drawer-rail"
    )
    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.width(DrawerCollapsedWidth))
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
        Box(
            modifier = Modifier
                .zIndex(1f)
                .width(width)
                .fillMaxHeight()
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
                .onFocusChanged { drawerHasFocus = it.hasFocus }
        ) {
            Box(
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(DrawerExpandedWidth)
                    .fillMaxHeight()
            ) { drawer() }
            if (railAlpha > 0f) {
                CollapsedRail(
                    label = collapsedLabel,
                    modifier = Modifier
                        .width(DrawerCollapsedWidth)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = railAlpha }
                )
            }
        }
        // Sombra na borda da gaveta aberta, separando-a do conteúdo por baixo.
        if (railAlpha < 1f) {
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .padding(start = width)
                    .width(16.dp)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = 1f - railAlpha }
                    .background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))
                    )
            )
        }
    }
}

@Composable
private fun CollapsedRail(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).verticalText()
        )
    }
}

/** Texto girado 90° (lido de baixo para cima) ocupando a altura disponível. */
private fun Modifier.verticalText(): Modifier = this
    .layout { measurable, constraints ->
        // Mede com a altura disponível como largura e troca os eixos.
        val placeable = measurable.measure(
            Constraints(maxWidth = constraints.maxHeight.coerceAtLeast(0), maxHeight = constraints.maxWidth)
        )
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(
                x = -(placeable.width - placeable.height) / 2,
                y = (placeable.width - placeable.height) / 2
            ) { rotationZ = -90f }
        }
    }
