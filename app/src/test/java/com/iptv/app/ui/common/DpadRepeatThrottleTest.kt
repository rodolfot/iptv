package com.iptv.app.ui.common

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadRepeatThrottleTest {

    private val down = KeyEvent.ACTION_DOWN
    private val dpadDown = KeyEvent.KEYCODE_DPAD_DOWN

    @Test
    fun firstPressAndSpacedPressesPass() {
        val t = DpadRepeatThrottle(90)
        assertFalse(t.shouldDrop(down, dpadDown, 1_000))
        assertFalse(t.shouldDrop(down, dpadDown, 1_150))
        assertFalse(t.shouldDrop(down, dpadDown, 1_300))
    }

    @Test
    fun burstIsThinnedOut() {
        val t = DpadRepeatThrottle(90)
        // 30 eventos por segundo (segurando a seta).
        val accepted = (0 until 30).count { i -> !t.shouldDrop(down, dpadDown, 10_000L + i * 33) }
        assertTrue("aceitou $accepted", accepted in 9..12)
    }

    @Test
    fun keyUpAndOtherKeysAreNeverDropped() {
        val t = DpadRepeatThrottle(90)
        assertFalse(t.shouldDrop(down, dpadDown, 5_000))
        assertFalse(t.shouldDrop(KeyEvent.ACTION_UP, dpadDown, 5_010))
        assertFalse(t.shouldDrop(down, KeyEvent.KEYCODE_DPAD_CENTER, 5_020))
        assertFalse(t.shouldDrop(down, KeyEvent.KEYCODE_BACK, 5_030))
    }
}
