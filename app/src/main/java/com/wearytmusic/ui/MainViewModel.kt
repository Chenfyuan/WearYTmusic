package com.wearytmusic.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.wearytmusic.data.MusicRepository
import com.wearytmusic.data.Track
import com.wearytmusic.playback.PlaybackService
import com.wearytmusic.playback.YTM_SCHEME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Track> = emptyList(),
    val error: String? = null,
)

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val hasItem: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _now = MutableStateFlow(NowPlaying())
    val now: StateFlow<NowPlaying> = _now.asStateFlow()

    private var controller: MediaController? = null

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = publish(player)
                })
                publish(c)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun publish(p: Player) {
        val md = p.mediaMetadata
        _now.value = NowPlaying(
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artworkUrl = md.artworkUri?.toString(),
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            hasItem = p.mediaItemCount > 0,
        )
    }

    fun onQueryChange(q: String) = _search.update { it.copy(query = q) }

    fun runSearch() {
        val q = _search.value.query.trim()
        if (q.isEmpty()) return
        _search.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { MusicRepository.searchSongs(q) }
                .onSuccess { r -> _search.update { it.copy(loading = false, results = r) } }
                .onFailure { e -> _search.update { it.copy(loading = false, error = e.message ?: "搜索失败") } }
        }
    }

    /** Play the current results as a queue, starting at [index]. */
    fun playFrom(index: Int) {
        val c = controller ?: return
        val items = _search.value.results.map(::toMediaItem)
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    private fun toMediaItem(t: Track) = MediaItem.Builder()
        .setMediaId(t.videoId)
        .setUri("$YTM_SCHEME://${t.videoId}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setArtworkUri(t.artworkUrl?.let(Uri::parse))
                .build(),
        )
        .build()

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
