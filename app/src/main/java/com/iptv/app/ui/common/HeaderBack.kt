package com.iptv.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Section-level Back handler hoisted to the app top bar.
 *
 * The TopBar in HomeScreen knows nothing about whether a section (Movies,
 * Series, Live, Search…) is currently drilled into a category. Instead of
 * threading callbacks through every section, sections call [setBack] from a
 * [DisposableEffect] when they enter a sub-state; the TopBar reads
 * [sectionBack] and displays a Back chip next to the logo.
 *
 * Detail screens (MovieDetail/ChannelDetail) keep their own dedicated Back —
 * they're stacked, not internal navigation — handled directly in HomeScreen.
 */
class HeaderBackController {
    val sectionBack = mutableStateOf<(() -> Unit)?>(null)

    fun setBack(handler: (() -> Unit)?) {
        sectionBack.value = handler
    }
}

val LocalHeaderBack = compositionLocalOf { HeaderBackController() }

/**
 * Convenience: registers a back handler while this composable is in the
 * composition and clears it on dispose. Use from a screen that just entered a
 * sub-state and wants its back action surfaced in the top bar.
 */
@Composable
fun RegisterHeaderBack(handler: () -> Unit) {
    val controller = LocalHeaderBack.current
    DisposableEffect(controller, handler) {
        controller.setBack(handler)
        onDispose { controller.setBack(null) }
    }
}
