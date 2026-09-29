package com.wearytmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class PlaylistSummary(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
)

/** Minimal authenticated client for the YouTube Music web API (library playlists & their tracks). */
object InnerTube {
    private const val ORIGIN = "https://music.youtube.com"
    private const val KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    val LIKED = PlaylistSummary("VLLM", "喜欢的音乐", "", null)

    class NotLoggedIn : Exception("请先登录")

    suspend fun libraryPlaylists(): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        if (Prefs.cookie.value == null) throw NotLoggedIn()
        listOf(LIKED) + parsePlaylists(browse("FEmusic_liked_playlists"))
    }

    /** Tracks of a playlist/album; follows continuations up to [maxTracks]. Works logged-out for public playlists. */
    suspend fun playlistTracks(browseId: String, maxTracks: Int = 300): List<Track> = withContext(Dispatchers.IO) {
        val id = if (browseId.startsWith("VL") || browseId.startsWith("MPRE")) browseId else "VL$browseId"
        var json = browse(id)
        val out = parseTracks(json).toMutableList()
        var pages = 0
        while (out.size < maxTracks && pages++ < 10) {
            val token = continuationToken(json) ?: break
            json = browse(null, token)
            val more = parseTracks(json)
            if (more.isEmpty()) break
            out += more
        }
        out.distinctBy { it.videoId }.take(maxTracks)
    }

    private fun browse(browseId: String?, continuation: String? = null): JSONObject {
        val body = JSONObject().put(
            "context",
            JSONObject().put(
                "client",
                JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", "1.20250310.01.00")
                    .put("hl", "zh-CN"),
            ),
        )
        var url = "$ORIGIN/youtubei/v1/browse?prettyPrint=false&key=$KEY"
        if (browseId != null) body.put("browseId", browseId)
        if (continuation != null) url += "&ctoken=$continuation&continuation=$continuation&type=next"

        val req = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", UA)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Origin", ORIGIN)
            .header("X-Goog-AuthUser", "0")
            .apply {
                Prefs.cookie.value?.let { cookie ->
                    header("Cookie", cookie)
                    sapisidHash(cookie)?.let { header("Authorization", "SAPISIDHASH $it") }
                }
            }.build()
        Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) throw NotLoggedIn()
            check(resp.isSuccessful) { "请求失败 (${resp.code})" }
            return JSONObject(text)
        }
    }

    internal fun sapisidHash(cookie: String, nowSec: Long = System.currentTimeMillis() / 1000): String? {
        val sapisid = Regex("(?:^|;\\s*)SAPISID=([^;]+)").find(cookie)?.groupValues?.get(1) ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest("$nowSec $sapisid $ORIGIN".toByteArray())
        return "${nowSec}_" + digest.joinToString("") { "%02x".format(it) }
    }

    // ---- parsing (internal for unit tests) ----

    internal fun parsePlaylists(json: JSONObject): List<PlaylistSummary> =
        findAll(json, "musicTwoRowItemRenderer").mapNotNull { r ->
            val id = path(r, "navigationEndpoint", "browseEndpoint")?.optString("browseId").orEmpty()
            if (!(id.startsWith("VL") || id.startsWith("MPRE"))) return@mapNotNull null
            PlaylistSummary(
                browseId = id,
                title = runsText(r.optJSONObject("title")),
                subtitle = runsText(r.optJSONObject("subtitle")),
                artworkUrl = lastThumb(path(r, "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")),
            )
        }.filter { it.title.isNotEmpty() }

    internal fun parseTracks(json: JSONObject): List<Track> =
        findAll(json, "musicResponsiveListItemRenderer").mapNotNull { r ->
            val videoId = r.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
                .ifEmpty {
                    path(r, "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
                        "playNavigationEndpoint", "watchEndpoint")?.optString("videoId").orEmpty()
                }
            if (videoId.isEmpty()) return@mapNotNull null
            val cols = r.optJSONArray("flexColumns")
            fun col(i: Int) = cols?.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
            val title = col(0)?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val artist = col(1)?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val dur = r.optJSONArray("fixedColumns")?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            Track(
                videoId, title, artist,
                lastThumb(path(r, "thumbnail", "musicThumbnailRenderer", "thumbnail")),
                parseDuration(dur),
            )
        }.filter { it.title.isNotEmpty() }

    internal fun continuationToken(json: JSONObject): String? =
        findAll(json, "continuationCommand").firstOrNull()?.optString("token")?.takeIf { it.isNotEmpty() }

    internal fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0
        return text.split(":").fold(0L) { acc, p -> acc * 60 + (p.trim().toLongOrNull() ?: return 0) }
    }

    private fun path(o: JSONObject?, vararg keys: String): JSONObject? =
        keys.fold(o) { cur, k -> cur?.optJSONObject(k) }

    private fun runsText(o: JSONObject?): String {
        val runs = o?.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") }
    }

    private fun lastThumb(o: JSONObject?): String? =
        o?.optJSONArray("thumbnails")?.let { a -> if (a.length() == 0) null else a.getJSONObject(a.length() - 1).optString("url") }

    /** Depth-first collection of every object stored under [key] (does not descend into matches). */
    private fun findAll(node: Any?, key: String, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val v = node.opt(k)
                if (k == key && v is JSONObject) out += v else findAll(v, key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) findAll(node.opt(i), key, out)
        }
        return out
    }
}
