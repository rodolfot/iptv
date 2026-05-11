package com.iptv.app.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lightweight wrapper exposed via CompositionLocal so any screen can show a
 * snackbar without owning a host. The actual host is mounted once in the root
 * scaffold.
 */
class SnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: SnackbarDuration = SnackbarDuration.Short
    ) {
        scope.launch {
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = duration
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalSnackbar = compositionLocalOf<SnackbarController?> { null }
