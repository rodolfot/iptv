package com.iptv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.material3.Shapes as M3Shapes
import androidx.compose.material3.Typography as M3Typography
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme
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
    // Tipografia compactada: -4sp em cada nível (mínimo 10sp). O usuário
    // reclamou que tudo estava grande demais — antes labelSmall=14sp; agora
    // 10sp. Headlines/displays também caem proporcionalmente.
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
)

@OptIn(ExperimentalTvMaterial3Api::class)
private val IptvShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Parallel Material3 (non-TV) color scheme so any `androidx.compose.material3`
// component used on phones (FilledTonalButton, Snackbar, OutlinedTextField,
// AlertDialog…) renders against the same dark palette. Without this they fall
// back to the default light theme and show up as near-white pills on top of
// our dark surfaces.
private val IptvColorsM3 = m3DarkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFF003E5C),
    onPrimaryContainer = TextPrimary,
    secondary = Accent,
    onSecondary = Color(0xFF1A1300),
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0x55FFFFFF),
    outlineVariant = Color(0x22FFFFFF)
)

private val IptvTypographyM3 = M3Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
)

private val IptvShapesM3 = M3Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    // Wrap with both Material3 themes so screens can mix tv.material3 and
    // material3 components without one of them flipping to the default light
    // palette mid-screen.
    M3MaterialTheme(
        colorScheme = IptvColorsM3,
        typography = IptvTypographyM3,
        shapes = IptvShapesM3
    ) {
        MaterialTheme(
            colorScheme = IptvColors,
            typography = IptvTypography,
            shapes = IptvShapes,
            content = content
        )
    }
}
