package com.wearytmusic

import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme
import com.wearytmusic.data.Track
import com.wearytmusic.ui.NowPlaying
import com.wearytmusic.ui.PlayerScreen
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
        Track("4", "Blank Space", "Taylor Swift", null),
    )

    @Test fun search() = shot("search") {
        SearchScreen(SearchState(query = "周杰伦", results = tracks), NowPlaying(title = "晴天", hasItem = true), {}, {}, {}, {})
    }

    @Test fun player() = shot("player") {
        PlayerScreen(NowPlaying("晴天", "Jay Chou", null, isPlaying = true, hasItem = true), {}, {}, {})
    }
}
