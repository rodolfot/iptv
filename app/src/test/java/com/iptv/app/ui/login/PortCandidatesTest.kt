package com.iptv.app.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Port auto-detection was removed because providers behind reverse proxies were
 * routing to the wrong endpoint when we probed extra ports in parallel. The
 * function now just returns the host the user typed; this test pins that
 * contract so a future "let's bring it back" PR has to deliberately update
 * both sides.
 */
class PortCandidatesTest {

    @Test
    fun `returns only the host the user typed`() {
        assertEquals(
            listOf("http://example.com"),
            portCandidatesFor("http://example.com")
        )
        assertEquals(
            listOf("http://example.com:1234"),
            portCandidatesFor("http://example.com:1234")
        )
        assertEquals(
            listOf("https://secure.example"),
            portCandidatesFor("https://secure.example")
        )
    }
}
