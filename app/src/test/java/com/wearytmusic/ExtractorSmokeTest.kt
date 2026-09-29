package com.wearytmusic

import com.wearytmusic.data.DownloaderImpl
import com.wearytmusic.data.MusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** Live-network smoke test: search + stream resolution. Run with `./gradlew :app:testDebugUnitTest -PliveTests`. */
class ExtractorSmokeTest {
    @Test
    fun searchAndResolve() = runBlocking {
        org.junit.Assume.assumeTrue(System.getProperty("liveTests") != null)
        NewPipe.init(DownloaderImpl(), Localization.DEFAULT, ContentCountry.DEFAULT)
        val results = MusicRepository.searchSongs("周杰伦 晴天")
        println("results=${results.size} first=${results.firstOrNull()}")
        assertTrue(results.isNotEmpty())
        val url = MusicRepository.resolveAudioUrl(results.first().videoId)
        println("url=${url.take(80)}")
        assertTrue(url.startsWith("http"))
    }
}
