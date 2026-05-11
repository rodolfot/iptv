package com.iptv.app.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortCandidatesTest {

    @Test
    fun `bare host gets common Xtream ports plus the original`() {
        val cands = portCandidatesFor("http://example.com")
        assertEquals(
            listOf(
                "http://example.com",
                "http://example.com:8080",
                "http://example.com:8880",
                "http://example.com:80",
                "http://example.com:25461"
            ),
            cands
        )
    }

    @Test
    fun `host with explicit port is left alone`() {
        val cands = portCandidatesFor("http://example.com:1234")
        assertEquals(listOf("http://example.com:1234"), cands)
    }

    @Test
    fun `https scheme is preserved when generating candidates`() {
        val cands = portCandidatesFor("https://secure.example")
        assertTrue(cands.all { it.startsWith("https://") })
    }
}
