@file:Suppress("MatchingDeclarationName")

package com.iptv.app.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Thin facade over [androidx.compose.material3] for the bits of the app that
 * don't need TV-specific focus behaviour (typography, basic text/icon).
 *
 * Background: the app was built on top of `androidx.tv.material3` (1.0.0)
 * which duplicates `Text`, `MaterialTheme`, `Icon`, `Tab`, etc. on top of the
 * core Material3 library. We want to move off the TV duplicates wherever
 * doing so doesn't lose D-pad behaviour — namely Text, Icon, MaterialTheme
 * lookups. Tabs and clickable surfaces stay on tv.material3 because their
 * focus glow is what makes them usable with a remote.
 *
 * Importing through this file lets us migrate file-by-file without touching
 * every call site twice. Replace
 *   `import androidx.tv.material3.Text` → `import com.iptv.app.ui.common.IptvText as Text`
 * in a screen and it just keeps working.
 */
@Composable
fun IptvText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTypography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        fontWeight = fontWeight
    )
}

@Composable
fun IptvIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier)
}

@Composable
fun RowScope.IptvRowIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier)
}

/** Re-export so callers don't need to import Material3 directly. */
val LocalTypography
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography

val LocalColors
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme
