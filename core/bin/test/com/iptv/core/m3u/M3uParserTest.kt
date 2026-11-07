package com.iptv.core.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses extinf attributes and url`() {
        val src = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn.us" tvg-logo="https://cnn/logo.png" group-title="News",CNN HD
            http://server/live/cnn.m3u8
            #EXTINF:-1 group-title="Sports",ESPN
            http://server/live/espn.m3u8
        """.trimIndent()
        val tracks = M3uParser.parse(src)
        assertEquals(2, tracks.size)
        assertEquals("CNN HD", tracks[0].name)
        assertEquals("http://server/live/cnn.m3u8", tracks[0].url)
        assertEquals("cnn.us", tracks[0].epgChannelId)
        assertEquals("https://cnn/logo.png", tracks[0].logoUrl)
        assertEquals("News", tracks[0].groupTitle)
        assertEquals("ESPN", tracks[1].name)
        assertNull(tracks[1].logoUrl)
    }

    @Test
    fun `empty or non-m3u input yields empty list`() {
        assertTrue(M3uParser.parse("").isEmpty())
        assertTrue(M3uParser.parse("hello").isEmpty())
    }

    @Test
    fun `tolerates extra blank lines and comments between entries`() {
        val src = """
            #EXTM3U

            #EXTINF:-1,Channel One
            #EXTGRP:Misc
            http://srv/1.m3u8


            #EXTINF:-1,Channel Two
            http://srv/2.m3u8
        """.trimIndent()
        val tracks = M3uParser.parse(src)
        assertEquals(2, tracks.size)
        assertEquals("Channel One", tracks[0].name)
        assertEquals("http://srv/1.m3u8", tracks[0].url)
    }
}
