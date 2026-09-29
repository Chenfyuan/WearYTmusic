package com.wearytmusic

import com.wearytmusic.data.DownloaderImpl
import com.wearytmusic.data.InnerTube
import com.wearytmusic.data.LyricsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

/** Live-network tests; run with `./gradlew :app:testDebugUnitTest -PliveTests`. */
class LiveNetworkTest {
    @Test fun lyricsFromLrclib() = runBlocking {
        assumeTrue(System.getProperty("liveTests") != null)
        val l = LyricsRepository.fetch("x", "Love Story (Official Video)", "Taylor Swift - Topic", 236)
        println("lyrics synced=${l.synced.size} first=${l.synced.firstOrNull()}")
        assertTrue(l.synced.size > 10)
    }

    @Test fun publicPlaylistTracksWithContinuation() = runBlocking {
        assumeTrue(System.getProperty("liveTests") != null)
        NewPipe.init(DownloaderImpl(), Localization.DEFAULT, ContentCountry.DEFAULT)
        val yt = ServiceList.YouTube
        val ex = yt.getSearchExtractor(yt.searchQHFactory.fromQuery("top hits", listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS), ""))
        ex.fetchPage()
        val url = ex.initialPage.items.first().url
        val id = Regex("list=([\\w-]+)").find(url)!!.groupValues[1]
        println("playlist=$id")
        val tracks = InnerTube.playlistTracks(id, maxTracks = 150)
        println("tracks=${tracks.size} first=${tracks.firstOrNull()}")
        assertTrue(tracks.size > 20)
    }
}
