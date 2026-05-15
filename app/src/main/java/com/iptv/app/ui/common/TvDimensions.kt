package com.iptv.app.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.app.data.prefs.DeviceProfile

enum class FormFactor { Phone, Tablet, Tv }

data class TvDimensions(
    val formFactor: FormFactor,
    val ScreenPadding: Dp,
    val SectionSpacing: Dp,
    val CardSpacing: Dp,
    val PosterCardW: Dp,
    val PosterCardH: Dp,
    val ChannelCardW: Dp,
    val ChannelCardH: Dp,
    val MoviesGridColumns: Int,
    val ChannelGridColumns: Int,
    val SeriesGridColumns: Int,
    val FocusBorder: Dp,
    val TitleFont: TextUnit,
    val SectionTitleFont: TextUnit,
    val SubtitleFont: TextUnit,
    val BodyFont: TextUnit
)

private val TvDefaults = TvDimensions(
    formFactor = FormFactor.Tv,
    ScreenPadding = 64.dp,
    SectionSpacing = 32.dp,
    CardSpacing = 24.dp,
    PosterCardW = 220.dp,
    PosterCardH = 330.dp,
    ChannelCardW = 320.dp,
    ChannelCardH = 200.dp,
    MoviesGridColumns = 5,
    ChannelGridColumns = 4,
    SeriesGridColumns = 5,
    FocusBorder = 4.dp,
    TitleFont = 48.sp,
    SectionTitleFont = 32.sp,
    SubtitleFont = 24.sp,
    BodyFont = 20.sp
)

private val TabletDefaults = TvDimensions(
    formFactor = FormFactor.Tablet,
    ScreenPadding = 32.dp,
    SectionSpacing = 24.dp,
    CardSpacing = 16.dp,
    PosterCardW = 160.dp,
    PosterCardH = 240.dp,
    ChannelCardW = 240.dp,
    ChannelCardH = 160.dp,
    MoviesGridColumns = 4,
    ChannelGridColumns = 3,
    SeriesGridColumns = 4,
    FocusBorder = 3.dp,
    TitleFont = 36.sp,
    SectionTitleFont = 24.sp,
    SubtitleFont = 18.sp,
    BodyFont = 16.sp
)

private val PhoneDefaults = TvDimensions(
    formFactor = FormFactor.Phone,
    ScreenPadding = 12.dp,
    SectionSpacing = 16.dp,
    CardSpacing = 10.dp,
    PosterCardW = 158.dp,
    PosterCardH = 232.dp,
    ChannelCardW = 168.dp,
    ChannelCardH = 152.dp,
    MoviesGridColumns = 2,
    ChannelGridColumns = 2,
    SeriesGridColumns = 2,
    FocusBorder = 2.dp,
    TitleFont = 28.sp,
    SectionTitleFont = 20.sp,
    SubtitleFont = 16.sp,
    BodyFont = 14.sp
)

/**
 * Manual override do form factor escolhido pelo usuário no onboarding.
 * Quando null, cai na auto-detecção (uiMode + smallestScreenWidthDp).
 * Provido lá em [com.iptv.app.MainActivity] a partir de [SettingsStore].
 */
val LocalDeviceProfile = compositionLocalOf<DeviceProfile?> { null }

/**
 * Compose-aware dimensions that adapt to the current form factor.
 *
 * Ordem de precedência:
 *  1. Override manual do usuário (LocalDeviceProfile) — escolhido no onboarding.
 *  2. UiModeManager.UI_MODE_TYPE_TELEVISION — alguns Android TVs (PS5 HDMI,
 *     TCLs) reportam smallestScreenWidthDp <600 e cairiam em phone sem este
 *     check.
 *  3. smallestScreenWidthDp como heurística final.
 */
@Composable
@ReadOnlyComposable
fun rememberTvDim(): TvDimensions {
    val override = LocalDeviceProfile.current
    if (override != null) {
        return when (override) {
            DeviceProfile.TV -> TvDefaults
            DeviceProfile.TABLET -> TabletDefaults
            DeviceProfile.PHONE -> PhoneDefaults
        }
    }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val sw = configuration.smallestScreenWidthDp
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val isLeanback = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    return when {
        isLeanback -> TvDefaults
        sw < 600 -> PhoneDefaults
        sw < 840 -> TabletDefaults
        else -> TvDefaults
    }
}

/**
 * Static defaults (TV-sized) — kept for non-Composable contexts only.
 * Prefer [rememberTvDim] inside Composables so screens adapt to phone/tablet.
 */
object TvDim {
    val ScreenPadding = TvDefaults.ScreenPadding
    val SectionSpacing = TvDefaults.SectionSpacing
    val CardSpacing = TvDefaults.CardSpacing
    val PosterCardW = TvDefaults.PosterCardW
    val PosterCardH = TvDefaults.PosterCardH
    val ChannelCardW = TvDefaults.ChannelCardW
    val ChannelCardH = TvDefaults.ChannelCardH
    val MoviesGridColumns = TvDefaults.MoviesGridColumns
    val ChannelGridColumns = TvDefaults.ChannelGridColumns
    val SeriesGridColumns = TvDefaults.SeriesGridColumns
    val FocusBorder = TvDefaults.FocusBorder
    val TitleFont = TvDefaults.TitleFont
    val SectionTitleFont = TvDefaults.SectionTitleFont
    val SubtitleFont = TvDefaults.SubtitleFont
    val BodyFont = TvDefaults.BodyFont
}
