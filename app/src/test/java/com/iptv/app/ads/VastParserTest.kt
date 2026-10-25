package com.iptv.app.ads

import com.iptv.app.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [33])
class VastParserTest {

    @Test
    fun inlineAdPicksMp4UpTo720pAndPercentSkip() {
        val xml = """
            <VAST version="3.0"><Ad id="1"><InLine>
              <Impression><![CDATA[https://t.example/imp]]></Impression>
              <Creatives><Creative><Linear skipoffset="25%">
                <Duration>00:00:20.000</Duration>
                <TrackingEvents>
                  <Tracking event="start"><![CDATA[https://t.example/start]]></Tracking>
                  <Tracking event="complete"><![CDATA[https://t.example/complete]]></Tracking>
                </TrackingEvents>
                <VideoClicks><ClickThrough><![CDATA[https://adv.example]]></ClickThrough></VideoClicks>
                <MediaFiles>
                  <MediaFile delivery="progressive" type="video/mp4" height="1080" bitrate="5000"><![CDATA[https://cdn.example/1080.mp4]]></MediaFile>
                  <MediaFile delivery="progressive" type="video/mp4" height="720" bitrate="2500"><![CDATA[https://cdn.example/720.mp4]]></MediaFile>
                  <MediaFile delivery="progressive" type="video/mp4" height="480" bitrate="1000"><![CDATA[https://cdn.example/480.mp4]]></MediaFile>
                  <MediaFile delivery="streaming" type="application/x-mpegURL"><![CDATA[https://cdn.example/a.m3u8]]></MediaFile>
                </MediaFiles>
              </Linear></Creative></Creatives>
            </InLine></Ad></VAST>
        """.trimIndent()
        val doc = VastParser.parse(xml)
        assertTrue(doc is VastDocument.InLine)
        val ad = (doc as VastDocument.InLine).ad
        assertEquals("https://cdn.example/720.mp4", ad.mediaUrl)
        assertEquals(20_000L, ad.durationMs)
        assertEquals(5_000L, ad.skipOffsetMs)
        assertEquals(listOf("https://t.example/imp"), ad.impressions)
        assertEquals(listOf("https://t.example/complete"), ad.tracking["complete"])
        assertEquals("https://adv.example", ad.clickThrough)
    }

    @Test
    fun wrapperAndEmptyResponses() {
        val wrapper = """
            <VAST version="3.0"><Ad><Wrapper>
              <Impression>https://t.example/w</Impression>
              <VASTAdTagURI><![CDATA[https://ads.example/next]]></VASTAdTagURI>
            </Wrapper></Ad></VAST>
        """.trimIndent()
        val doc = VastParser.parse(wrapper)
        assertTrue(doc is VastDocument.Wrapper)
        assertEquals("https://ads.example/next", (doc as VastDocument.Wrapper).nextUrl)
        assertTrue(VastParser.parse("<VAST version=\"3.0\"/>") is VastDocument.Empty)
    }

    @Test
    fun parsesTimecodes() {
        assertEquals(90_500L, VastParser.parseTime("00:01:30.500"))
        assertEquals(null, VastParser.parseTime("90"))
    }
}
