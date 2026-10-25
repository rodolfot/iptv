package com.iptv.app.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.iptv.app.data.prefs.DeviceProfile
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Largura lógica (dp) em que a UI de TV foi desenhada — a de uma TV 1080p em
 * xhdpi (1920 px / 2). Todas as medidas de [TvDimensions] partem dela.
 */
const val TV_DESIGN_WIDTH_DP = 960

/**
 * Margem de segurança contra overscan, já convertida em padding. Telas
 * top-level aplicam em volta do conteúdo; o player aplica só nos controles
 * (o vídeo continua de borda a borda).
 */
val LocalOverscanPadding = compositionLocalOf { PaddingValues(0.dp) }

/** TV "de verdade": Leanback (uiMode TELEVISION) ou o usuário escolheu TV no
 *  onboarding. Tablets grandes (sw ≥ 840dp) usam as medidas de TV mas não são
 *  TV — não recebem normalização de densidade nem margem de overscan. */
@Composable
@ReadOnlyComposable
fun isRealTv(): Boolean {
    val override = LocalDeviceProfile.current
    if (override != null) return override == DeviceProfile.TV
    val configuration = LocalConfiguration.current
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    if (uiModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val mgr = LocalContext.current.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return mgr?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Faz a UI de TV ocupar a mesma proporção da tela em qualquer aparelho, de 32"
 * a 100", e protege as bordas contra overscan.
 *
 * Por que: TVs e TV boxes reportam densidades muito diferentes para a mesma
 * resolução. Uma TV 1080p pode vir como xhdpi (960dp de largura — o caso para
 * o qual o layout foi desenhado), tvdpi (1440dp), mdpi (1920dp — tudo fica
 * minúsculo) ou uma 4K em xxhdpi (1280dp). Em vez de manter medidas para cada
 * combinação, recalculamos a densidade para que a largura lógica seja sempre
 * [TV_DESIGN_WIDTH_DP]: cards, fontes e ícones ficam na mesma fração da tela,
 * que é o que importa a 10 pés de distância (TV maior = sofá mais longe).
 *
 * O overscan é o corte que muitas TVs (sobretudo as de entrada, de 32"–43")
 * fazem nas bordas da imagem — no Ao Vivo da TV de 40" do relato o começo dos
 * nomes das categorias sumia. [overscanPercent] vira padding proporcional em
 * [LocalOverscanPadding] (5% é a margem recomendada para TV).
 *
 * Fora de TV não altera nada.
 */
@Composable
fun TvScreenAdapter(
    overscanPercent: Int,
    content: @Composable () -> Unit
) {
    if (!isRealTv()) {
        content()
        return
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = configuration.screenWidthDp * density.density
    val heightPx = configuration.screenHeightDp * density.density
    val targetDensity = widthPx / TV_DESIGN_WIDTH_DP
    // Dentro de 5% do esperado (o caso comum, xhdpi) não mexe — evita
    // reescalar por arredondamento de barras do sistema.
    val normalize = widthPx > 0f && abs(targetDensity - density.density) / density.density > 0.05f
    val effectiveDensity = if (normalize) targetDensity else density.density
    val overscan = remember(overscanPercent, widthPx, heightPx, effectiveDensity) {
        val fraction = overscanPercent.coerceIn(0, 10) / 100f
        PaddingValues(
            horizontal = (widthPx * fraction / effectiveDensity).dp,
            vertical = (heightPx * fraction / effectiveDensity).dp
        )
    }
    if (!normalize) {
        CompositionLocalProvider(LocalOverscanPadding provides overscan, content = content)
        return
    }
    val scaledConfig = remember(configuration, targetDensity) {
        Configuration(configuration).apply {
            screenWidthDp = TV_DESIGN_WIDTH_DP
            screenHeightDp = (heightPx / targetDensity).roundToInt()
            smallestScreenWidthDp = min(screenWidthDp, screenHeightDp)
            densityDpi = (targetDensity * 160).roundToInt()
        }
    }
    CompositionLocalProvider(
        LocalDensity provides Density(targetDensity, density.fontScale),
        LocalConfiguration provides scaledConfig,
        LocalOverscanPadding provides overscan,
        content = content
    )
}
