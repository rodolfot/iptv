package com.iptv.app.ui.continueWatching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.ui.common.AnimatedTartaLogo
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.rememberTvDim

private data class Shortcut(val key: String, val label: Int, val icon: ImageVector)

private val SHORTCUTS = listOf(
    Shortcut("live", R.string.tab_live, Icons.Filled.LiveTv),
    Shortcut("movies", R.string.tab_movies, Icons.Filled.Movie),
    Shortcut("series", R.string.tab_series, Icons.Filled.Tv),
    Shortcut("search", R.string.tab_search, Icons.Filled.Search)
)

/**
 * Início sem nada assistido ainda (#33): marca, convite para o primeiro
 * canal/filme, atalhos grandes (foco inicial no Ao Vivo) e dicas do controle.
 * Some sozinho quando surgir histórico — a Início volta ao layout normal.
 */
@Composable
fun HomeEmptyTutorial(onNavigate: (String) -> Unit) {
    val dim = rememberTvDim()
    val first = remember { FocusRequester() }
    // Na TV o foco precisa cair num atalho, senão o D-pad fica "perdido".
    LaunchedEffect(Unit) { if (!dim.useTouchUi) runCatching { first.requestFocus() } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AnimatedTartaLogo(size = 72.dp)
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SHORTCUTS.forEachIndexed { i, sc ->
                TouchableButton(
                    onClick = { onNavigate(sc.key) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(sc.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(sc.label))
                    }
                }
            }
        }
        // Dicas do controle remoto só fazem sentido com D-pad.
        if (!dim.useTouchUi) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                listOf(
                    R.string.home_tip_drawer,
                    R.string.home_tip_preview,
                    R.string.home_tip_zapping,
                    R.string.home_tip_favorite
                ).forEach { tip ->
                    Text(
                        stringResource(tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
