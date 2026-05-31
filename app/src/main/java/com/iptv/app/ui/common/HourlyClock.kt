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
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chip pequeno no canto superior da tela que mostra o horário atual por
 * 5 segundos no início de cada hora cheia (XX:00). Fica em opt-in — o flag
 * vem do SettingsStore.showHourlyClock.
 *
 * Implementação: um loop em LaunchedEffect calcula o delay até a próxima
 * hora cheia (~ms até :00) e dorme. Ao acordar, marca `visible = true` por
 * 5s e dorme até a próxima hora. Sem isso teríamos que pingar o sistema
 * a cada segundo só pra detectar a virada.
 */
@Composable
fun HourlyClockChip(enabled: Boolean) {
    if (!enabled) return
    var visible by remember { mutableStateOf(false) }
    var nowText by remember { mutableStateOf(currentTimeShort()) }
    LaunchedEffect(Unit) {
        // Mostra imediatamente uma vez ao habilitar — o usuário acabou de
        // configurar, é útil ver o relógio aparecer pra confirmar que ligou.
        nowText = currentTimeShort()
        visible = true
        kotlinx.coroutines.delay(5_000L)
        visible = false
        while (true) {
            val delayMs = msUntilNextHour()
            kotlinx.coroutines.delay(delayMs)
            nowText = currentTimeShort()
            visible = true
            kotlinx.coroutines.delay(5_000L)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = nowText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
            )
        }
    }
}

private fun currentTimeShort(): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date())
}

private fun msUntilNextHour(): Long {
    val now = java.util.Calendar.getInstance()
    val next = (now.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.HOUR_OF_DAY, 1)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return (next.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
}
