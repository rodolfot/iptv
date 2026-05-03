package com.iptv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val Background = Color(0xFF0B0F14)
private val Surface = Color(0xFF141A22)
private val SurfaceVariant = Color(0xFF1C2430)
private val Primary = Color(0xFF00B7FF)
private val OnPrimary = Color(0xFF001620)
private val TextPrimary = Color(0xFFF2F4F8)
private val TextSecondary = Color(0xFF9AA4B2)
private val Accent = Color(0xFFFFB800)

@OptIn(ExperimentalTvMaterial3Api::class)
private val IptvColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFF003E5C),
    onPrimaryContainer = TextPrimary,
    secondary = Accent,
    onSecondary = Color(0xFF1A1300),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    border = Color(0x33FFFFFF),
    borderVariant = Color(0x22FFFFFF)
)

@OptIn(ExperimentalTvMaterial3Api::class)
private val IptvTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 22.sp),
    bodyMedium = TextStyle(fontSize = 18.sp),
    bodySmall = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

@OptIn(ExperimentalTvMaterial3Api::class)
private val IptvShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IptvColors,
        typography = IptvTypography,
        shapes = IptvShapes,
        content = content
    )
}
