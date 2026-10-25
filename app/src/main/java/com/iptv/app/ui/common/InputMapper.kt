package com.iptv.app.ui.common

import android.view.KeyEvent

/**
 * Ação de alto nível vinda de qualquer forma de entrada — controle remoto
 * (D-pad), teclado, botões de mídia do volante/painel do carro ou knob
 * rotativo. Telas e player tratam a ação, não o keycode: o mesmo "próximo
 * canal" vale para CH+, ⏭ do volante e → no player.
 */
enum class InputAction {
    UP, DOWN, LEFT, RIGHT, SELECT, BACK,
    PLAY_PAUSE, PLAY, PAUSE, STOP,
    NEXT, PREVIOUS,
    CHANNEL_UP, CHANNEL_DOWN,
    FAST_FORWARD, REWIND
}

object InputMapper {

    /** Traduz um keycode do Android para a ação correspondente, ou null. */
    fun actionOf(keyCode: Int): InputAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> InputAction.UP
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> InputAction.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> InputAction.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> InputAction.RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> InputAction.SELECT
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> InputAction.BACK
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> InputAction.PLAY_PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY -> InputAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> InputAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_STOP -> InputAction.STOP
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> InputAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> InputAction.PREVIOUS
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> InputAction.CHANNEL_UP
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> InputAction.CHANNEL_DOWN
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> InputAction.FAST_FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> InputAction.REWIND
        else -> null
    }

    /**
     * Normaliza entradas que a UI não entende direto em equivalentes do
     * D-pad: o knob rotativo de centrais multimídia (Android Automotive e
     * várias centrais Android) gira gerando NAVIGATE_NEXT/PREVIOUS, que viram
     * ↓/↑ — assim listas, grades e menus funcionam com o knob sem código
     * próprio em cada tela.
     */
    fun normalize(event: KeyEvent): KeyEvent {
        val target = when (event.keyCode) {
            KeyEvent.KEYCODE_NAVIGATE_NEXT -> KeyEvent.KEYCODE_DPAD_DOWN
            KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> KeyEvent.KEYCODE_DPAD_UP
            KeyEvent.KEYCODE_NAVIGATE_IN -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_NAVIGATE_OUT -> KeyEvent.KEYCODE_BACK
            else -> return event
        }
        return KeyEvent(
            event.downTime, event.eventTime, event.action, target,
            event.repeatCount, event.metaState, event.deviceId, event.scanCode,
            event.flags, event.source
        )
    }
}

/**
 * Limita a cadência de ↑↓←→ quando a tecla fica pressionada (#25). Segurar a
 * seta gera ~20–30 eventos por segundo — como repetições (repeatCount > 0) ou,
 * em muitos controles IR baratos, como toques novos (repeatCount 0) — e cada
 * um movia o foco, rolava a lista e recompunha os cards; em TVs lentas a fila
 * crescia até o Android encerrar o app por ANR. Eventos que chegam antes de
 * [minIntervalMs] desde o último aceito são descartados. Toques humanos
 * normais (>100ms entre si) e o soltar da tecla nunca são afetados; ~11
 * movimentos por segundo continuam rolando rápido, dentro do que a TV desenha.
 */
class DpadRepeatThrottle(private val minIntervalMs: Long = 90L) {
    private var lastAcceptedAt = Long.MIN_VALUE / 2

    fun shouldDrop(event: KeyEvent): Boolean = shouldDrop(event.action, event.keyCode, event.eventTime)

    /** Versão sem [KeyEvent] para teste. */
    fun shouldDrop(action: Int, keyCode: Int, eventTime: Long): Boolean {
        if (action != KeyEvent.ACTION_DOWN) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
            else -> return false
        }
        if (eventTime - lastAcceptedAt < minIntervalMs) return true
        lastAcceptedAt = eventTime
        return false
    }
}
