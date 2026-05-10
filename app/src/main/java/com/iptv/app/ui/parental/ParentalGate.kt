package com.iptv.app.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iptv.app.R
import com.iptv.app.ui.common.TouchableButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.text.KeyboardOptions

class ParentalSession(private var unlockedAt: Long = 0L) {
    fun isUnlocked(): Boolean = System.currentTimeMillis() - unlockedAt < 15 * 60 * 1000L
    fun unlock() { unlockedAt = System.currentTimeMillis() }
    fun lock() { unlockedAt = 0L }
}

/**
 * Shown when accessing adult content. If [expectedPin] is null, prompts the user
 * to create one for the first time via [onPinCreated]; otherwise verifies it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ParentalPinDialog(
    expectedPin: String?,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
    onPinCreated: (String) -> Unit = {}
) {
    if (expectedPin.isNullOrBlank()) {
        ParentalPinSetupDialog(
            onCreated = { newPin ->
                onPinCreated(newPin)
                onUnlocked()
            },
            onCancel = onCancel
        )
        return
    }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.width(480.dp).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.parental_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.parental_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); error = false },
                    label = { Text(stringResource(R.string.parental_pass_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(stringResource(R.string.parental_wrong), color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TouchableButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    TouchableButton(onClick = {
                        if (pin == expectedPin) onUnlocked() else error = true
                    }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ParentalPinSetupDialog(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.width(520.dp).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.parental_setup_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.parental_setup_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); error = null },
                    label = { Text(stringResource(R.string.parental_new_pass)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(8); error = null },
                    label = { Text(stringResource(R.string.parental_confirm_pass)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TouchableButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    val minLen = stringResource(R.string.parental_min_length)
                    val mismatch = stringResource(R.string.parental_mismatch)
                    TouchableButton(onClick = {
                        when {
                            pin.length < 4 -> error = minLen
                            pin != confirm -> error = mismatch
                            else -> onCreated(pin)
                        }
                    }) { Text(stringResource(R.string.parental_create)) }
                }
            }
        }
    }
}
