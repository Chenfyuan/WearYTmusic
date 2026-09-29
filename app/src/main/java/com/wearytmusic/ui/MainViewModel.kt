package com.wearytmusic.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.wearytmusic.data.DownloadStore
import com.wearytmusic.data.DownloadWorker
import com.wearytmusic.data.InnerTube
import com.wearytmusic.data.Lyrics
import com.wearytmusic.data.LyricsRepository
import com.wearytmusic.data.MusicRepository
import com.wearytmusic.data.PlaylistSummary
import com.wearytmusic.data.Prefs
import com.wearytmusic.data.Track
import com.wearytmusic.playback.PlaybackService
import com.wearytmusic.playback.YTM_SCHEME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Track> = emptyList(),
    val error: String? = null,
)

data class NowPlaying(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val hasItem: Boolean = false,
    val error: String? = null,
)

data class LibraryState(
    val loading: Boolean = false,
    val playlists: List<PlaylistSummary> = emptyList(),
    val error: String? = null,
)

data class PlaylistState(
    val title: String = "",
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

data class LyricsState(
    val loading: Boolean = false,
    val lyrics: Lyrics? = null,
    val error: String? = null,
)

enum class DownloadState { NONE, RUNNING, DONE }

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _now = MutableStateFlow(NowPlaying())
    val now: StateFlow<NowPlaying> = _now.asStateFlow()

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _playlist = MutableStateFlow(PlaylistState())
    val playlist: StateFlow<PlaylistState> = _playlist.asStateFlow()

    private val _lyrics = MutableStateFlow(LyricsState())
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()
    private var lyricsFor: String? = null

    val loggedIn: StateFlow<Boolean> =
        Prefs.cookie.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs.cookie.value != null)
    val downloads: StateFlow<List<Track>> = DownloadStore.items
    val downloading: StateFlow<Set<String>> = DownloadStore.running

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
            videoId = p.currentMediaItem?.mediaId.orEmpty(),
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artworkUrl = md.artworkUri?.toString(),
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            hasItem = p.mediaItemCount > 0,
            error = if (p.playerError != null) "播放失败，点击播放重试" else null,
        )
    }

    // ---- search ----

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

    // ---- library ----

    fun loadLibrary() {
        if (!loggedIn.value) return
        _library.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { InnerTube.libraryPlaylists() }
                .onSuccess { r -> _library.value = LibraryState(false, r) }
                .onFailure { e -> _library.update { it.copy(loading = false, error = e.message ?: "加载失败") } }
        }
    }

    fun openPlaylist(p: PlaylistSummary) {
        _playlist.value = PlaylistState(title = p.title, loading = true)
        viewModelScope.launch {
            runCatching { InnerTube.playlistTracks(p.browseId) }
                .onSuccess { r -> _playlist.update { it.copy(loading = false, tracks = r) } }
                .onFailure { e -> _playlist.update { it.copy(loading = false, error = e.message ?: "加载失败") } }
        }
    }

    // ---- account ----

    fun onLoggedIn(cookie: String) {
        Prefs.setCookie(cookie)
        _library.value = LibraryState()
    }

    fun logout() {
        Prefs.setCookie(null)
        CookieManager.getInstance().removeAllCookies(null)
        _library.value = LibraryState()
    }

    // ---- playback ----

    fun playSearch(index: Int) = playTracks(_search.value.results, index)

    fun playTracks(tracks: List<Track>, index: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map(::toMediaItem), index.coerceIn(tracks.indices), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playerError != null) { c.prepare(); c.play(); return }
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    private fun currentTrack(): Track? = controller?.currentMediaItem?.let(::toTrack)

    // ---- downloads ----

    fun downloadStateOf(videoId: String, done: List<Track>, running: Set<String>) = when {
        done.any { it.videoId == videoId } -> DownloadState.DONE
        videoId in running -> DownloadState.RUNNING
        else -> DownloadState.NONE
    }

    fun toggleDownloadCurrent() {
        val t = currentTrack() ?: return
        if (DownloadStore.isDownloaded(t.videoId)) DownloadStore.remove(t.videoId)
        else if (t.videoId !in downloading.value) DownloadWorker.enqueue(app, t)
    }

    fun downloadAll(tracks: List<Track>) = tracks.filterNot { DownloadStore.isDownloaded(it.videoId) }
        .forEach { DownloadWorker.enqueue(app, it) }

    fun clearDownloads() = DownloadStore.removeAll()

    // ---- lyrics ----

    fun loadLyrics() {
        val c = controller ?: return
        val t = currentTrack() ?: return
        if (lyricsFor == t.videoId && _lyrics.value.error == null) return
        lyricsFor = t.videoId
        _lyrics.value = LyricsState(loading = true)
        val durSec = if (c.duration > 0) c.duration / 1000 else t.durationSec
        viewModelScope.launch {
            runCatching { LyricsRepository.fetch(t.videoId, t.title, t.artist, durSec) }
                .onSuccess { _lyrics.value = LyricsState(lyrics = it) }
                .onFailure { _lyrics.value = LyricsState(error = "无法获取歌词") }
        }
    }

    // ---- mapping ----

    private fun toMediaItem(t: Track): MediaItem {
        val local = DownloadStore.fileFor(t.videoId)
        val uri = if (local.exists()) Uri.fromFile(local).toString() else "$YTM_SCHEME://${t.videoId}"
        return MediaItem.Builder()
            .setMediaId(t.videoId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setArtworkUri(t.artworkUrl?.let(Uri::parse))
                    .setExtras(Bundle().apply { putLong("dur", t.durationSec) })
                    .build(),
            )
            .build()
    }

    private fun toTrack(m: MediaItem) = Track(
        m.mediaId,
        m.mediaMetadata.title?.toString().orEmpty(),
        m.mediaMetadata.artist?.toString().orEmpty(),
        m.mediaMetadata.artworkUri?.toString(),
        m.mediaMetadata.extras?.getLong("dur") ?: 0L,
    )

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
