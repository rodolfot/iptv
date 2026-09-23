package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.R

/**
 * D-pad friendly filter input for TV.
 *
 * Why: Android TV opens the IME the moment a `BasicTextField` gains focus. On
 * a list with a filter row at the top, that means just *passing through* the
 * field with the D-pad pops the keyboard, blocking the rows below. Users who
 * only want to scroll get trapped.
 *
 * UX: the field acts like a button while navigating (no IME). Pressing OK /
 * D-pad center / Enter enters edit mode and shows the IME. Back / Escape exits
 * edit mode without losing the focus row, so the user can keep navigating.
 */
@Composable
fun LocalFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }
    val displayFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            editorFocus.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            // Restaura o foco pro campo não-editável ao SAIR da edição — sem
            // isso o foco vira null ao fechar o IME e o D-pad fica morto.
            if (wasEditing) runCatching { displayFocus.requestFocus() }
        }
        wasEditing = editing
    }

    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (editing) MaterialTheme.colorScheme.primary else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.padding(end = 8.dp))

        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { editing = false }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(editorFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                editing = false
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.search_in_category),
                            color = Color(0x80FFFFFF),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    inner()
                }
            )
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color(0xCCFFFFFF),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onValueChange("") }
                )
            }
        } else {
            // Non-editable focusable surface. Acts like a button: focus does
            // not open the IME; only OK / Enter does.
            var isFocused by remember { mutableStateOf(false) }
            Text(
                text = value.ifEmpty { stringResource(R.string.search_in_category) },
                color = if (value.isEmpty()) Color(0x80FFFFFF) else Color.White,
                style = if (value.isEmpty()) MaterialTheme.typography.bodyMedium
                else TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(displayFocus)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                editing = true
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable { editing = true }
            )
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color(0xCCFFFFFF),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onValueChange("") }
                )
            }
        }
    }
}
