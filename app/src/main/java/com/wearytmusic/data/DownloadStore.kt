package com.wearytmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Index of tracks downloaded for offline playback (audio files in filesDir/music). */
object DownloadStore {
    private lateinit var dir: File
    private lateinit var index: File

    private val _items = MutableStateFlow<List<Track>>(emptyList())
    val items: StateFlow<List<Track>> = _items.asStateFlow()

    private val _running = MutableStateFlow<Set<String>>(emptySet())
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    fun init(context: Context) {
        dir = File(context.filesDir, "music").apply { mkdirs() }
        index = File(context.filesDir, "downloads.json")
        _items.value = runCatching {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Track(o.getString("id"), o.getString("title"), o.getString("artist"),
                    o.optString("art").ifEmpty { null }, o.optLong("dur"))
            }.filter { fileFor(it.videoId).exists() }
        }.getOrDefault(emptyList())
    }

    fun fileFor(videoId: String) = File(dir, "$videoId.audio")
    fun partFor(videoId: String) = File(dir, "$videoId.part")

    fun isDownloaded(videoId: String) = fileFor(videoId).exists()

    fun setRunning(videoId: String, running: Boolean) =
        _running.update { if (running) it + videoId else it - videoId }

    @Synchronized
    fun add(track: Track) {
        _items.update { list -> list.filterNot { it.videoId == track.videoId } + track }
        persist()
    }

    @Synchronized
    fun remove(videoId: String) {
        fileFor(videoId).delete()
        partFor(videoId).delete()
        _items.update { list -> list.filterNot { it.videoId == videoId } }
        persist()
    }

    fun removeAll() = _items.value.map { it.videoId }.forEach(::remove)

    fun totalBytes() = _items.value.sumOf { fileFor(it.videoId).length() }

    private fun persist() {
        val arr = JSONArray()
        _items.value.forEach {
            arr.put(JSONObject().put("id", it.videoId).put("title", it.title).put("artist", it.artist)
                .put("art", it.artworkUrl ?: "").put("dur", it.durationSec))
        }
        index.writeText(arr.toString())
    }
}
