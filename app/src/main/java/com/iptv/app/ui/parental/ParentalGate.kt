package com.iptv.app.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ParentalPinDialog(
    expectedPin: String,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
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
                Text("Conteúdo restrito", style = MaterialTheme.typography.titleLarge)
                Text("Digite a senha para continuar", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); error = false },
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text("Senha incorreta", color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    Button(onClick = onCancel) { Text("Cancelar") }
                    Button(onClick = {
                        if (pin == expectedPin) onUnlocked() else error = true
                    }) { Text("OK") }
                }
            }
        }
    }
}
