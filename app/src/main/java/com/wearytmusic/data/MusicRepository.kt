package com.wearytmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
)

object MusicRepository {
    private val yt get() = ServiceList.YouTube

    /**
     * Search YouTube Music. The "songs" filter alone is sparse (sometimes empty), so
     * songs come first, followed by music videos (we only play their audio), de-duplicated.
     */
    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        val filters = listOf(
            YoutubeSearchQueryHandlerFactory.MUSIC_SONGS,
            YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS,
        )
        var lastError: Throwable? = null
        val tracks = filters.flatMap { filter ->
            runCatching { searchWithFilter(query, filter) }
                .onFailure { lastError = it }
                .getOrDefault(emptyList())
        }.distinctBy { it.videoId }
        if (tracks.isEmpty()) lastError?.let { throw it }
        tracks
    }

    private fun searchWithFilter(query: String, filter: String): List<Track> {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
        val extractor = yt.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = videoIdOf(item.url) ?: return@mapNotNull null
                Track(id, item.name, item.uploaderName.orEmpty(), item.thumbnails.lastOrNull()?.url)
            }
    }

    private fun videoIdOf(url: String): String? =
        Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([\\w-]{11})").find(url)?.groupValues?.get(1)

    private data class Cached(val url: String, val expiresAt: Long)

    private val urlCache = ConcurrentHashMap<String, Cached>()

    /**
     * Resolve a direct audio URL for [videoId]. Blocking — call from a loader/IO thread.
     * Stream URLs expire (~6h), so results are cached for a shorter time.
     */
    fun resolveAudioUrl(videoId: String): String {
        val now = System.currentTimeMillis()
        urlCache[videoId]?.takeIf { it.expiresAt > now }?.let { return it.url }

        val info = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$videoId")
        val stream = pickAudio(info.audioStreams)
            ?: error("No playable audio stream for $videoId")
        urlCache[videoId] = Cached(stream.content, now + 3 * 60 * 60 * 1000L)
        return stream.content
    }

    /** Best progressive stream up to ~160 kbps (saves watch battery/data); else the lowest available. */
    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        val progressive = streams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl
        }
        val capped = progressive.filter { it.averageBitrate in 1..160 }
        return capped.maxByOrNull { it.averageBitrate } ?: progressive.minByOrNull { it.averageBitrate }
    }
}
