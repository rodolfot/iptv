package com.iptv.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    ScreenPadding = 16.dp,
    SectionSpacing = 16.dp,
    CardSpacing = 12.dp,
    PosterCardW = 130.dp,
    PosterCardH = 195.dp,
    ChannelCardW = 180.dp,
    ChannelCardH = 130.dp,
    MoviesGridColumns = 2,
    ChannelGridColumns = 2,
    SeriesGridColumns = 2,
    FocusBorder = 2.dp,
    TitleFont = 28.sp,
    SectionTitleFont = 20.sp,
    SubtitleFont = 16.sp,
    BodyFont = 14.sp
)

/** Compose-aware dimensions that adapt to the current form factor. */
@Composable
@ReadOnlyComposable
fun rememberTvDim(): TvDimensions {
    val sw = LocalConfiguration.current.smallestScreenWidthDp
    return when {
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
