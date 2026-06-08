package com.iptv.app.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the JSON shapes the app expects from Xtream Codes panels. Variants in this
 * file are real-world responses we have seen — keep them so future codec edits do
 * not silently break parsing.
 */
class XtreamDtoParsingTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test fun `login response with auth=1`() {
        val json = """
            {
              "user_info": {"username":"u","password":"p","auth":1,"status":"Active",
                "exp_date":"1999999999","is_trial":"0","active_cons":"0","max_connections":"3"},
              "server_info": {"url":"x.com","port":"80","https_port":"443","server_protocol":"http"}
            }
        """.trimIndent()
        val parsed = moshi.adapter(LoginResponse::class.java).fromJson(json)!!
        assertEquals(1, parsed.userInfo?.auth)
        assertEquals("Active", parsed.userInfo?.status)
        assertEquals("80", parsed.serverInfo?.port)
    }

    @Test fun `login response with auth=0 and message`() {
        val json = """
            {"user_info": {"auth":0,"message":"Invalid credentials"}}
        """.trimIndent()
        val parsed = moshi.adapter(LoginResponse::class.java).fromJson(json)!!
        assertEquals(0, parsed.userInfo?.auth)
        assertEquals("Invalid credentials", parsed.userInfo?.message)
        assertNull(parsed.serverInfo)
    }

    @Test fun `live stream list with optional fields missing`() {
        val json = """
            [
              {"num":1,"name":"A","stream_type":"live","stream_id":10,
               "stream_icon":"http://logo","epg_channel_id":"a.id","added":"1700000000",
               "category_id":"4","tv_archive":1,"direct_source":""},
              {"name":"B","stream_id":11}
            ]
        """.trimIndent()
        val type = Types.newParameterizedType(List::class.java, LiveStreamDto::class.java)
        val parsed: List<LiveStreamDto> = moshi.adapter<List<LiveStreamDto>>(type).fromJson(json)!!
        assertEquals(2, parsed.size)
        assertEquals("A", parsed[0].name)
        assertEquals(10, parsed[0].streamId)
        assertEquals("a.id", parsed[0].epgChannelId)
        assertEquals(11, parsed[1].streamId)
        assertNull(parsed[1].epgChannelId)
    }

    @Test fun `vod with rating as string and rating5 as number`() {
        val json = """
            [{"name":"X","stream_id":1,"rating":"7.5","rating_5based":4.2,
              "added":"1700000000","container_extension":"mp4",
              "release_date":"2024-01-01"}]
        """.trimIndent()
        val type = Types.newParameterizedType(List::class.java, VodStreamDto::class.java)
        val parsed = moshi.adapter<List<VodStreamDto>>(type).fromJson(json)!!
        assertEquals("7.5", parsed[0].rating)
        assertEquals(4.2, parsed[0].rating5!!, 0.001)
        assertEquals("mp4", parsed[0].containerExtension)
    }

    @Test fun `series with releaseDate camel and snake variants`() {
        val json = """
            [{"name":"S","series_id":1,"releaseDate":"2024-05-01"},
             {"name":"S2","series_id":2,"release_date":"2024-06-01"}]
        """.trimIndent()
        val type = Types.newParameterizedType(List::class.java, SeriesDto::class.java)
        val parsed = moshi.adapter<List<SeriesDto>>(type).fromJson(json)!!
        assertEquals("2024-05-01", parsed[0].releaseDate)
        assertEquals("2024-06-01", parsed[1].releaseDateSnake)
    }

    @Test fun `series info with episodes map keyed by season number`() {
        val json = """
            {
              "info": {"name":"Show","cover":"c"},
              "seasons": [{"season_number":1,"episode_count":2}],
              "episodes": {
                "1": [
                  {"id":"100","episode_num":1,"title":"E1","container_extension":"mkv",
                   "info":{"duration_secs":1200}},
                  {"id":"101","episode_num":2,"title":"E2","container_extension":"mkv"}
                ]
              }
            }
        """.trimIndent()
        val parsed = moshi.adapter(SeriesInfoResponse::class.java).fromJson(json)!!
        assertEquals("Show", parsed.info?.name)
        assertEquals(1, parsed.seasons?.size)
        val episodes = parsed.normalizedEpisodes().getValue("1")
        assertEquals(2, episodes.size)
        // durationSecs virou @Loose String? na 1.3.0 (provedores mandam Int,
        // Double ou String), então o valor cru parseado é a string "1200".
        assertEquals("1200", episodes[0].info?.durationSecs)
    }

    @Test fun `category with parent_id missing`() {
        val json = """[{"category_id":"1","category_name":"All"}]"""
        val type = Types.newParameterizedType(List::class.java, CategoryDto::class.java)
        val parsed = moshi.adapter<List<CategoryDto>>(type).fromJson(json)!!
        assertTrue(parsed[0].parentId == null || parsed[0].parentId == 0)
    }
}
