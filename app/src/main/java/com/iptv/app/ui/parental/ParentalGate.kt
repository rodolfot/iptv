package com.iptv.app.ui.parental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iptv.app.R
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

/**
 * Sessão de desbloqueio do controle parental.
 *
 * O timestamp é estático (escopo de processo), então o desbloqueio sobrevive
 * à recriação de telas/Composables — trocar de canal ou navegar entre seções
 * não pede a senha de novo. Uma vez digitada, vale por [WINDOW_MS] (1h) desde
 * a última vez; depois disso pede novamente. Reinício do app sempre tranca.
 */
class ParentalSession {
    fun isUnlocked(): Boolean = System.currentTimeMillis() - unlockedAt < WINDOW_MS
    fun unlock() { unlockedAt = System.currentTimeMillis() }
    fun lock() { unlockedAt = 0L }

    companion object {
        private const val WINDOW_MS = 60L * 60 * 1000 // 1 hora
        @Volatile private var unlockedAt: Long = 0L
    }
}

/**
 * Shown when accessing adult content. If [expectedPin] is null, prompts the user
 * to create one for the first time via [onPinCreated]; otherwise verifies it.
 */
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
    val firstKeyFocus = remember { FocusRequester() }

    // Confirma sozinho assim que o comprimento bate com o PIN salvo — evita
    // ter que navegar até um botão OK separado depois de já ter digitado
    // tudo pelo teclado numérico.
    LaunchedEffect(pin) {
        if (pin.isNotEmpty() && pin.length == expectedPin.length) {
            if (pin == expectedPin) onUnlocked() else {
                error = true
                pin = ""
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstKeyFocus.requestFocus() } }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.width(420.dp).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.parental_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.parental_message), style = MaterialTheme.typography.bodyMedium)
                PinDotDisplay(label = stringResource(R.string.parental_pass_label), length = pin.length)
                if (error) {
                    Text(stringResource(R.string.parental_wrong), color = MaterialTheme.colorScheme.error)
                }
                PinKeypad(
                    onDigit = { d -> pin = (pin + d).take(8); error = false },
                    onBackspace = { pin = pin.dropLast(1) },
                    firstKeyFocus = firstKeyFocus,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TouchableButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    TouchableButton(onClick = {
                        if (pin == expectedPin) onUnlocked() else { error = true; pin = "" }
                    }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

private enum class SetupStep { NEW, CONFIRM }

@Composable
fun ParentalPinSetupDialog(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(SetupStep.NEW) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val firstKeyFocus = remember { FocusRequester() }
    val minLen = stringResource(R.string.parental_min_length)
    val mismatch = stringResource(R.string.parental_mismatch)

    LaunchedEffect(step) { runCatching { firstKeyFocus.requestFocus() } }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.width(420.dp).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.parental_setup_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.parental_setup_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                when (step) {
                    SetupStep.NEW -> PinDotDisplay(label = stringResource(R.string.parental_new_pass), length = pin.length)
                    SetupStep.CONFIRM -> PinDotDisplay(label = stringResource(R.string.parental_confirm_pass), length = confirm.length)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PinKeypad(
                    onDigit = { d ->
                        error = null
                        when (step) {
                            SetupStep.NEW -> pin = (pin + d).take(8)
                            SetupStep.CONFIRM -> confirm = (confirm + d).take(8)
                        }
                    },
                    onBackspace = {
                        when (step) {
                            SetupStep.NEW -> pin = pin.dropLast(1)
                            SetupStep.CONFIRM -> confirm = confirm.dropLast(1)
                        }
                    },
                    firstKeyFocus = firstKeyFocus,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    if (step == SetupStep.CONFIRM) {
                        TouchableButton(onClick = { step = SetupStep.NEW; confirm = ""; error = null }) {
                            Text(stringResource(R.string.back))
                        }
                    }
                    TouchableButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    TouchableButton(onClick = {
                        when (step) {
                            SetupStep.NEW ->
                                if (pin.length < 4) error = minLen else step = SetupStep.CONFIRM
                            SetupStep.CONFIRM ->
                                if (pin != confirm) { error = mismatch; confirm = "" } else onCreated(pin)
                        }
                    }) {
                        Text(stringResource(if (step == SetupStep.NEW) R.string.parental_next else R.string.parental_create))
                    }
                }
            }
        }
    }
}

/** Rótulo + fileira de bolinhas preenchidas conforme dígitos digitados. */
@Composable
private fun PinDotDisplay(label: String, length: Int, maxDots: Int = 8) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(maxOf(length, 4).coerceAtMost(maxDots)) { i ->
                val filled = i < length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

/**
 * Teclado numérico próprio (0-9 + apagar), navegável por D-pad. Substitui o
 * teclado numérico do sistema (IME) para o PIN parental: em Android TV esse
 * IME abre como um painel flutuante numa posição fixa da tela, desconectada
 * do diálogo — quebrando o layout visualmente. Com o teclado embutido aqui,
 * tudo fica dentro do próprio diálogo.
 */
@Composable
private fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    firstKeyFocus: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, digit ->
                    val isFirst = rowIndex == 0 && colIndex == 0
                    val keyModifier = Modifier.weight(1f).let {
                        if (isFirst && firstKeyFocus != null) it.focusRequester(firstKeyFocus) else it
                    }
                    PinKey(label = digit.toString(), modifier = keyModifier, onClick = { onDigit(digit) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PinKey(icon = Icons.AutoMirrored.Filled.Backspace, modifier = Modifier.weight(1f), onClick = onBackspace)
            PinKey(label = "0", modifier = Modifier.weight(1f), onClick = { onDigit('0') })
            Box(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PinKey(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TouchableButton(onClick = onClick, modifier = modifier.height(52.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = stringResource(R.string.parental_backspace))
        } else {
            Text(label.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
