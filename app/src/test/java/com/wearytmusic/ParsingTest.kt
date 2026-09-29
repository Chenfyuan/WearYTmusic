package com.wearytmusic

import com.wearytmusic.data.InnerTube
import com.wearytmusic.data.LyricsRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {
    // Shapes follow the YouTube Music web responses (hand-written fixtures, not captured from an account).
    private val libraryJson = JSONObject("""
    {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
      {"gridRenderer":{"items":[
        {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"新歌单"}]}}},
        {"musicTwoRowItemRenderer":{
          "title":{"runs":[{"text":"跑步"}]},
          "subtitle":{"runs":[{"text":"歌单"},{"text":" • "},{"text":"12 首歌曲"}]},
          "navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL123"}},
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"a"},{"url":"b"}]}}}}}
      ]}}]}}}}]}}}
    """)

    private val tracksJson = JSONObject("""
    {"contents":{"musicPlaylistShelfRenderer":{"contents":[
      {"musicResponsiveListItemRenderer":{
        "playlistItemData":{"videoId":"abc12345678"},
        "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"t1"},{"url":"t2"}]}}},
        "flexColumns":[
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"晴天"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"周杰伦"},{"text":" • "},{"text":"叶惠美"}]}}}],
        "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"4:29"}]}}}]}},
      {"musicResponsiveListItemRenderer":{"flexColumns":[
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"已删除的歌"}]}}}]}},
      {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"TOK"}}}}
    ]}}}
    """)

    @Test fun playlists() {
        val p = InnerTube.parsePlaylists(libraryJson)
        assertEquals(1, p.size)
        assertEquals("VLPL123", p[0].browseId)
        assertEquals("跑步", p[0].title)
        assertEquals("歌单 • 12 首歌曲", p[0].subtitle)
        assertEquals("b", p[0].artworkUrl)
    }

    @Test fun tracks() {
        val t = InnerTube.parseTracks(tracksJson)
        assertEquals(1, t.size) // the unavailable track (no videoId) is skipped
        assertEquals("abc12345678", t[0].videoId)
        assertEquals("周杰伦", t[0].artist)
        assertEquals(269L, t[0].durationSec)
        assertEquals("t2", t[0].artworkUrl)
        assertEquals("TOK", InnerTube.continuationToken(tracksJson))
    }

    @Test fun duration() {
        assertEquals(3723L, InnerTube.parseDuration("1:02:03"))
        assertEquals(0L, InnerTube.parseDuration(null))
        assertEquals(0L, InnerTube.parseDuration("abc"))
    }

    @Test fun sapisidHash() {
        assertNull(InnerTube.sapisidHash("foo=bar"))
        val h = InnerTube.sapisidHash("a=b; SAPISID=secret; c=d", nowSec = 1000)!!
        assertTrue(h.startsWith("1000_") && h.length == 5 + 40)
    }

    @Test fun lrc() {
        val l = LyricsRepository.parseLrc("[ti:x]\n[00:01.50]第一句\n[01:02.00][00:30.00]重复句\n[00:40.00]\n")
        assertEquals(listOf(1500L, 30000L, 62000L), l.map { it.timeMs })
        assertEquals("重复句", l[1].text)
    }

    @Test fun lyricsPick() {
        val arr = JSONArray("""[
          {"duration":100,"syncedLyrics":"","plainLyrics":"plain-near"},
          {"duration":250,"syncedLyrics":"[00:01.00]hi","plainLyrics":"p"}]""")
        val far = LyricsRepository.pick(arr, 100)
        assertEquals("hi", far.synced.single().text) // synced wins over a closer plain-only match
    }

    @Test fun titleCleaning() {
        assertEquals("Love Story", LyricsRepository.cleanTitle("Love Story (Official Music Video)"))
        assertEquals("晴天", LyricsRepository.cleanTitle("晴天 【Official MV】"))
        assertEquals("Taylor Swift", LyricsRepository.cleanArtist("Taylor Swift - Topic"))
    }
}
