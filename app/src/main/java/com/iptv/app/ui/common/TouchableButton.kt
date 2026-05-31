@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Button that registers touch on phones/tablet/car AND keeps D-pad focus on TV.
 *
 * androidx.tv.material3.Button only fires onClick from a focused D-pad event;
 * tapping it em dispositivos touch puros (celular, tablet, multimídia de carro)
 * não dispara o clique. Mantemos o componente TV apenas em Leanback real,
 * onde o input principal é o controle remoto — em qualquer outro caso usamos
 * o Material3 padrão para que o toque funcione.
 *
 * `selected = true` renders a filled "current selection" affordance; otherwise
 * a tonal variant is used so callers can express toggle groups without
 * managing colors directly.
 *
 * On phones we tighten content padding and text style so toolbar-style rows
 * of these buttons (Back / Filters / Sort) don't bloat to the full headline
 * height and consume two visual lines.
 */
@Composable
fun TouchableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    /** Botão menor: usado em telas de detalhe (filme/série) e nas barras de
     *  ação onde os botões padrão do TV Material são desproporcionalmente
     *  grandes em relação ao corpo das telas. Reduz padding e fonte. */
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val dim = rememberTvDim()
    if (dim.useTouchUi) {
        // Material3 defaults aim for tablet sizing; on a phone toolbar a row of
        // 3 buttons doesn't fit. Shrink padding and text style without touching
        // touch-target size (still ≥ 36dp tall, ≥ 48dp wide). Em Tablet/Car
        // mantemos o padding default do M3 (mais confortável pra dedos em
        // telas grandes).
        val isPhone = dim.formFactor == FormFactor.Phone
        val touchPadding = if (isPhone) PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            else if (compact) PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            else ButtonDefaults.ContentPadding
        val phoneStyle = if (compact) M3MaterialTheme.typography.labelMedium
            else M3MaterialTheme.typography.labelLarge
        val textStyle = if (isPhone) phoneStyle else LocalTextStyle.current
        CompositionLocalProvider(LocalTextStyle provides textStyle) {
            if (selected) {
                androidx.compose.material3.Button(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                    contentPadding = touchPadding,
                    content = content
                )
            } else {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                    contentPadding = touchPadding,
                    content = content
                )
            }
        }
    } else {
        // Cores explícitas: o default do tv.material3.Button deixa o
        // texto branco sobre fundo branco quando focado, ficando ilegível.
        // Aqui fixamos contraste em todos os 4 estados (default/focused/
        // pressed/disabled).
        val tvScheme = androidx.tv.material3.MaterialTheme.colorScheme
        val tvContent: @Composable RowScope.() -> Unit = if (compact) {
            {
                CompositionLocalProvider(
                    LocalTextStyle provides androidx.tv.material3.MaterialTheme.typography.labelMedium
                ) {
                    content()
                }
            }
        } else content
        val tvPadding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 4.dp) else null
        if (selected) {
            androidx.tv.material3.Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                contentPadding = tvPadding ?: androidx.tv.material3.ButtonDefaults.ContentPadding,
                scale = androidx.tv.material3.ButtonDefaults.scale(
                    scale = 1f, focusedScale = 1f, pressedScale = 1f
                ),
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = tvScheme.primary,
                    contentColor = tvScheme.onPrimary,
                    focusedContainerColor = tvScheme.primary,
                    focusedContentColor = tvScheme.onPrimary,
                    pressedContainerColor = tvScheme.primary,
                    pressedContentColor = tvScheme.onPrimary
                ),
                content = tvContent
            )
        } else {
            androidx.tv.material3.Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                contentPadding = tvPadding ?: androidx.tv.material3.ButtonDefaults.ContentPadding,
                scale = androidx.tv.material3.ButtonDefaults.scale(
                    scale = 1f, focusedScale = 1f, pressedScale = 1f
                ),
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = tvScheme.secondaryContainer,
                    contentColor = tvScheme.onSecondaryContainer,
                    focusedContainerColor = tvScheme.primary,
                    focusedContentColor = tvScheme.onPrimary,
                    pressedContainerColor = tvScheme.primary,
                    pressedContentColor = tvScheme.onPrimary
                ),
                content = tvContent
            )
        }
    }
}

