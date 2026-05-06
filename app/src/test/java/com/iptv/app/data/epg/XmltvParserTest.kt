package com.iptv.app.data.epg

import com.iptv.app.data.db.EpgProgrammeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream

class XmltvParserTest {

    companion object {
        @BeforeClass @JvmStatic fun registerKxml() {
            // Robolectric stubs XmlPullParserFactory; point it at kxml2 for tests.
            System.setProperty(
                "org.xmlpull.v1.XmlPullParserFactory",
                "org.kxml2.io.KXmlParser,org.kxml2.io.KXmlSerializer"
            )
        }
    }

    private val sampleXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="discovery.br"><display-name>Discovery</display-name></channel>
          <programme channel="discovery.br" start="20260506180000 +0000" stop="20260506190000 +0000">
            <title>News at 6</title>
            <desc>Daily news.</desc>
          </programme>
          <programme channel="discovery.br" start="20260506190000 +0000" stop="20260506200000 +0000">
            <title>Documentary</title>
          </programme>
          <programme channel="other.br" start="20260506180000 +0000" stop="20260506190000 +0000">
            <title>Ignored</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test fun `parses programmes within window for known channels`() {
        val collected = mutableListOf<EpgProgrammeEntity>()
        XmltvParser.parse(
            input = ByteArrayInputStream(sampleXml.toByteArray()),
            windowStartMs = 0L,
            windowEndMs = Long.MAX_VALUE,
            knownChannelIds = setOf("discovery.br"),
            emit = { collected.add(it) }
        )

        assertEquals(2, collected.size)
        assertTrue(collected.all { it.channelId == "discovery.br" })
        assertEquals("News at 6", collected[0].title)
        assertEquals("Daily news.", collected[0].description)
        assertTrue(collected[0].stopMs > collected[0].startMs)
    }

    @Test fun `unknown channels are skipped when filter is provided`() {
        val collected = mutableListOf<EpgProgrammeEntity>()
        XmltvParser.parse(
            input = ByteArrayInputStream(sampleXml.toByteArray()),
            windowStartMs = 0,
            windowEndMs = Long.MAX_VALUE,
            knownChannelIds = setOf("nope.br"),
            emit = { collected.add(it) }
        )
        assertTrue(collected.isEmpty())
    }
}
