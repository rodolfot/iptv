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

/** Central multimídia: perfil Carro escolhido ou uiMode CAR do sistema. */
@Composable
@ReadOnlyComposable
fun isCarHeadUnit(): Boolean {
    val override = LocalDeviceProfile.current
    if (override != null) return override == DeviceProfile.CAR
    val configuration = LocalConfiguration.current
    if ((configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_CAR) return true
    val mgr = LocalContext.current.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return mgr?.currentModeType == Configuration.UI_MODE_TYPE_CAR
}

/**
 * Ampliação automática da central multimídia: tela de 7"–10" a um braço de
 * distância, quase sempre 1024×600 ou 1280×720 em densidade baixa (mdpi/hdpi)
 * — a UI de tablet ficava miúda. 135% deixa textos e alvos de toque no
 * tamanho de um celular.
 */
private const val CAR_AUTO_SCALE_PERCENT = 135

/**
 * Ajusta a densidade da UI para cada tela, de forma nativa:
 *
 * **TV (32" a 100")** — TVs e TV boxes reportam densidades muito diferentes
 * para a mesma resolução (uma 1080p pode vir como xhdpi = 960dp, tvdpi,
 * mdpi = 1920dp — tudo minúsculo — ou uma 4K em xxhdpi). A densidade é
 * recalculada para a largura lógica ser sempre [TV_DESIGN_WIDTH_DP]: cards,
 * fontes e ícones ficam na mesma fração da tela. Mais a margem contra
 * overscan ([overscanPercent] vira padding em [LocalOverscanPadding]).
 *
 * **Central multimídia** — no automático amplia [CAR_AUTO_SCALE_PERCENT].
 *
 * **Tamanho da interface** ([uiScalePercent], Configurações → Tela) — escala
 * escolhida pelo usuário, em cima da base acima, em qualquer aparelho.
 * 0 = automático.
 */
@Composable
fun TvScreenAdapter(
    overscanPercent: Int,
    uiScalePercent: Int = 0,
    content: @Composable () -> Unit
) {
    val tv = isRealTv()
    val car = !tv && isCarHeadUnit()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = configuration.screenWidthDp * density.density
    val heightPx = configuration.screenHeightDp * density.density
    // Base: TV normalizada para TV_DESIGN_WIDTH_DP (dentro de 5% do esperado
    // não mexe — evita reescalar por arredondamento de barras do sistema).
    var base = density.density
    if (tv && widthPx > 0f) {
        val target = widthPx / TV_DESIGN_WIDTH_DP
        if (abs(target - base) / base > 0.05f) base = target
    }
    val scale = when {
        uiScalePercent > 0 -> uiScalePercent / 100f
        car -> CAR_AUTO_SCALE_PERCENT / 100f
        else -> 1f
    }
    val effectiveDensity = base * scale
    val overscan = remember(tv, overscanPercent, widthPx, heightPx, effectiveDensity) {
        if (!tv) PaddingValues(0.dp) else {
            val fraction = overscanPercent.coerceIn(0, 10) / 100f
            PaddingValues(
                horizontal = (widthPx * fraction / effectiveDensity).dp,
                vertical = (heightPx * fraction / effectiveDensity).dp
            )
        }
    }
    // Sempre a mesma estrutura de composição: trocar o tamanho nas
    // Configurações não pode recriar o NavHost (voltaria ao início).
    val unchanged = widthPx <= 0f || abs(effectiveDensity - density.density) < 0.01f
    val scaledConfig = remember(configuration, effectiveDensity, unchanged) {
        if (unchanged) configuration else Configuration(configuration).apply {
            screenWidthDp = (widthPx / effectiveDensity).roundToInt()
            screenHeightDp = (heightPx / effectiveDensity).roundToInt()
            smallestScreenWidthDp = min(screenWidthDp, screenHeightDp)
            densityDpi = (effectiveDensity * 160).roundToInt()
        }
    }
    val scaledDensity = if (unchanged) density else Density(effectiveDensity, density.fontScale)
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalConfiguration provides scaledConfig,
        LocalOverscanPadding provides overscan,
        LocalScaledEnvironment provides if (unchanged) null else ScaledEnvironment(scaledDensity, scaledConfig),
        content = content
    )
}

/** Densidade/configuração ajustadas pelo [TvScreenAdapter]; null = sem ajuste. */
class ScaledEnvironment(val density: Density, val configuration: Configuration)

val LocalScaledEnvironment = compositionLocalOf<ScaledEnvironment?> { null }

/**
 * Diálogos e pop-ups abrem numa janela própria, cuja composição volta a
 * fornecer a densidade original do aparelho — sem isto eles ficavam fora do
 * tamanho escolhido (TV normalizada, central ampliada, "Tamanho da
 * interface"). Reaplica o ambiente capturado fora da janela.
 */
@Composable
fun WithScaledEnvironment(env: ScaledEnvironment?, content: @Composable () -> Unit) {
    if (env == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides env.density,
            LocalConfiguration provides env.configuration,
            content = content
        )
    }
}

/** [androidx.compose.ui.window.Dialog] respeitando a escala da interface. */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
    content: @Composable () -> Unit
) {
    val env = LocalScaledEnvironment.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        WithScaledEnvironment(env, content)
    }
}

/** [androidx.compose.material3.AlertDialog] respeitando a escala da interface. */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties()
) {
    val env = LocalScaledEnvironment.current
    fun wrap(slot: (@Composable () -> Unit)?): (@Composable () -> Unit)? =
        slot?.let { s -> { WithScaledEnvironment(env, s) } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = wrap(confirmButton)!!,
        modifier = modifier,
        dismissButton = wrap(dismissButton),
        icon = wrap(icon),
        title = wrap(title),
        text = wrap(text),
        properties = properties
    )
}
