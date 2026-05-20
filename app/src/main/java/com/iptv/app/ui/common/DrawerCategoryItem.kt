package com.iptv.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Item de drawer de categorias com indicador animado de foco (barra azul
 * deslizando à esquerda) em vez do efeito de scale do Card TV — que ficava
 * pesado em TVs lentas e cansava visualmente em listas longas.
 *
 * Estados:
 *  - selecionado: fundo `primaryContainer` permanente
 *  - focado: barra de 4dp à esquerda + fundo translúcido
 *  - normal: transparente
 */
@Composable
fun DrawerCategoryItem(
    name: String,
    isSelected: Boolean,
    isAdult: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Quantidade de itens na categoria — quando >0 vira "Nome (N)". */
    count: Int? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val barWidth by animateDpAsState(
        targetValue = if (focused) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "drawer-bar"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            focused -> primary.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 220),
        label = "drawer-bg"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .background(bgColor)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(barWidth)
                .background(primary)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val display = if (count != null && count > 0) "$name ($count)" else name
            Text(
                display,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isAdult) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
