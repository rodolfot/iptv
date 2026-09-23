package com.iptv.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Quanto tempo o relógio fica na tela a cada hora cheia. */
private const val SHOW_MS = 10_000L

/** Intervalo máximo entre checagens do relógio de parede. */
private const val MAX_CHECK_INTERVAL_MS = 30_000L

/**
 * Chip com o horário atual exibido por [SHOW_MS] no início de cada hora cheia
 * (XX:00). Fica na raiz do app (MainActivity), então aparece em qualquer tela
 * — inclusive por cima do player, onde o usuário passa a maior parte do tempo.
 * Controlado por SettingsStore.showHourlyClock.
 *
 * Antes o chip vivia só dentro da Home (sumia no player) e dormia um único
 * `delay` até a próxima hora. Esse delay usa o relógio de uptime, que para
 * enquanto a TV está em standby — ao religar, o horário calculado já estava
 * errado e o relógio nunca aparecia. Agora a checagem é pelo relógio de
 * parede, acordando no máximo a cada [MAX_CHECK_INTERVAL_MS], e só roda com o
 * app em primeiro plano.
 */
@Composable
fun HourlyClockChip(enabled: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var nowText by remember { mutableStateOf(currentTimeShort()) }
    // Ligar a opção nas Configurações mostra o relógio uma vez na hora, para
    // o usuário confirmar que funcionou.
    var wasEnabled by remember { mutableStateOf(enabled) }
    LaunchedEffect(enabled) {
        val justEnabled = enabled && !wasEnabled
        wasEnabled = enabled
        if (!justEnabled) return@LaunchedEffect
        nowText = currentTimeShort()
        visible = true
        try {
            delay(SHOW_MS)
        } finally {
            visible = false
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            visible = false
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastShownHour: String? = null
            while (true) {
                val now = Calendar.getInstance()
                val hourKey = "${now.get(Calendar.DAY_OF_YEAR)}-${now.get(Calendar.HOUR_OF_DAY)}"
                if (now.get(Calendar.MINUTE) == 0 && hourKey != lastShownHour) {
                    lastShownHour = hourKey
                    nowText = currentTimeShort()
                    visible = true
                    try {
                        delay(SHOW_MS)
                    } finally {
                        visible = false
                    }
                }
                delay(msUntilNextHour().coerceIn(1_000L, MAX_CHECK_INTERVAL_MS))
            }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = nowText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun currentTimeShort(): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date())
}

private fun msUntilNextHour(): Long {
    val now = Calendar.getInstance()
    val next = (now.clone() as Calendar).apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return next.timeInMillis - now.timeInMillis
}
