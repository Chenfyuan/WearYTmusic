package com.wearytmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val synced: List<LyricLine>, val plain: String?) {
    val isEmpty get() = synced.isEmpty() && plain.isNullOrBlank()
}

/** Lyrics from LRCLIB (https://lrclib.net) — free, no API key, supports time-synced LRC. */
object LyricsRepository {
    private val cache = HashMap<String, Lyrics>()

    suspend fun fetch(videoId: String, title: String, artist: String, durationSec: Long): Lyrics =
        withContext(Dispatchers.IO) {
            cache[videoId]?.let { return@withContext it }
            val t = cleanTitle(title)
            val a = cleanArtist(artist)
            var results = search(mapOf("track_name" to t, "artist_name" to a))
            if (results.length() == 0) results = search(mapOf("q" to "$t $a"))
            val lyrics = pick(results, durationSec)
            if (!lyrics.isEmpty) cache[videoId] = lyrics
            lyrics
        }

    private fun search(params: Map<String, String>): JSONArray {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url)
            .header("User-Agent", "WearYTMusic/0.2 (https://github.com/chenfyuan/wearytmusic)").build()
        Http.client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "歌词服务错误 (${resp.code})" }
            return JSONArray(resp.body?.string().orEmpty())
        }
    }

    internal fun pick(results: JSONArray, durationSec: Long): Lyrics {
        val items = (0 until results.length()).map { results.getJSONObject(it) }
        fun close(o: org.json.JSONObject) = durationSec <= 0 || abs(o.optDouble("duration", 0.0) - durationSec) <= 5
        val best = items.firstOrNull { close(it) && it.optString("syncedLyrics").isNotBlank() }
            ?: items.firstOrNull { it.optString("syncedLyrics").isNotBlank() }
            ?: items.firstOrNull { close(it) && it.optString("plainLyrics").isNotBlank() }
            ?: items.firstOrNull { it.optString("plainLyrics").isNotBlank() }
            ?: return Lyrics(emptyList(), null)
        val synced = parseLrc(best.optString("syncedLyrics"))
        return Lyrics(synced, best.optString("plainLyrics").ifBlank { null })
    }

    private val TS = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)]")

    internal fun parseLrc(lrc: String): List<LyricLine> =
        lrc.lineSequence().flatMap { line ->
            val stamps = TS.findAll(line).toList()
            val text = line.substring(stamps.lastOrNull()?.range?.last?.plus(1) ?: 0).trim()
            if (stamps.isEmpty() || text.isEmpty()) emptySequence()
            else stamps.asSequence().map {
                val ms = (it.groupValues[1].toLong() * 60_000 + it.groupValues[2].toDouble() * 1000).toLong()
                LyricLine(ms, text)
            }
        }.sortedBy { it.timeMs }.toList()

    private val NOISE = Regex(
        "\\s*[(\\[（【][^)\\]）】]*(official|video|mv|m/v|lyrics?|audio|live|remaster|hd|4k|visualizer)[^)\\]）】]*[)\\]）】]",
        RegexOption.IGNORE_CASE,
    )

    internal fun cleanTitle(t: String) = t.replace(NOISE, "").trim().ifEmpty { t }

    internal fun cleanArtist(a: String) = a.replace(Regex("\\s*-\\s*Topic$|VEVO$", RegexOption.IGNORE_CASE), "").trim()
}
