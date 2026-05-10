@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Button that registers touch on phones AND keeps D-pad focus on TV.
 *
 * androidx.tv.material3.Button only fires onClick from a focused D-pad event;
 * tapping it on a phone does nothing. We pick the standard Material3 Button on
 * phones and the TV variant on tablet/TV so both input modes work.
 *
 * `selected = true` renders a filled "current selection" affordance; otherwise
 * a tonal variant is used so callers can express toggle groups without
 * managing colors directly.
 */
@Composable
fun TouchableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val dim = rememberTvDim()
    if (dim.formFactor == FormFactor.Phone) {
        if (selected) {
            androidx.compose.material3.Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content
            )
        } else {
            androidx.compose.material3.FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content
            )
        }
    } else {
        if (selected) {
            androidx.tv.material3.Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = androidx.tv.material3.MaterialTheme.colorScheme.primary,
                    contentColor = androidx.tv.material3.MaterialTheme.colorScheme.onPrimary
                ),
                content = content
            )
        } else {
            androidx.tv.material3.Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content
            )
        }
    }
}
