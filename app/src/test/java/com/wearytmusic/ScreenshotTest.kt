package com.wearytmusic

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material.MaterialTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.wearytmusic.data.Lyrics
import com.wearytmusic.data.LyricLine
import com.wearytmusic.data.PlaylistSummary
import com.wearytmusic.data.Track
import com.wearytmusic.ui.DownloadState
import com.wearytmusic.ui.DownloadsScreen
import com.wearytmusic.ui.HomeScreen
import com.wearytmusic.ui.LibraryScreen
import com.wearytmusic.ui.LibraryState
import com.wearytmusic.ui.LyricsScreen
import com.wearytmusic.ui.LyricsState
import com.wearytmusic.ui.NowPlaying
import com.wearytmusic.ui.PlayerScreen
import com.wearytmusic.ui.PlaylistScreen
import com.wearytmusic.ui.PlaylistState
import com.wearytmusic.ui.SearchScreen
import com.wearytmusic.ui.SearchState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w227dp-h227dp-round-xxhdpi", sdk = [34])
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme { content() } }
        rule.mainClock.advanceTimeBy(2000)
        val dir = File("build/shots").apply { mkdirs() }
        rule.onRoot().captureRoboImage(File(dir, "$name.png").path)
    }

    private val tracks = listOf(
        Track("1", "晴天", "Jay Chou", null),
        Track("2", "七里香", "Jay Chou", null),
        Track("3", "Love Story", "Taylor Swift", null),
    )
    private val playing = NowPlaying("1", "晴天", "Jay Chou", null, isPlaying = true, hasItem = true)

    @Test fun home() = shot("home") { HomeScreen(playing, true, 3, {}, {}, {}, {}, {}, {}) }
    @Test fun homeLoggedOut() = shot("home_logged_out") { HomeScreen(NowPlaying(), false, 0, {}, {}, {}, {}, {}, {}) }
    @Test fun search() = shot("search") { SearchScreen(SearchState(query = "周杰伦", results = tracks), playing, {}, {}, {}, {}) }
    @Test fun player() = shot("player") { PlayerScreen(playing, DownloadState.NONE, {}, {}, {}, {}, {}) }
    @Test fun playerDownloaded() = shot("player_downloaded") { PlayerScreen(playing, DownloadState.DONE, {}, {}, {}, {}, {}) }
    @Test fun library() = shot("library") {
        LibraryScreen(LibraryState(playlists = listOf(
            PlaylistSummary("VLLM", "喜欢的音乐", "", null),
            PlaylistSummary("VL1", "跑步", "歌单 • 12 首歌曲", null),
            PlaylistSummary("VL2", "通勤", "歌单 • 40 首歌曲", null))), {}, {})
    }
    @Test fun playlist() = shot("playlist") { PlaylistScreen(PlaylistState("跑步", tracks = tracks), {}, {}) }
    @Test fun downloads() = shot("downloads") { DownloadsScreen(tracks, {}, {}) }
    @Test fun lyrics() = shot("lyrics") {
        LyricsScreen(LyricsState(lyrics = Lyrics(listOf(
            LyricLine(0, "故事的小黄花"), LyricLine(0, "从出生那年就飘着"), LyricLine(5000, "童年的荡秋千"), LyricLine(9000, "随记忆一直晃到现在")), null)),
            position = { 1000L }, onRetry = {})
    }
}
