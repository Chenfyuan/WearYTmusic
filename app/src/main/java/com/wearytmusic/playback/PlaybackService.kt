package com.wearytmusic.playback

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wearytmusic.data.DownloaderImpl
import com.wearytmusic.data.MusicRepository

/** Items are queued as `ytm://<videoId>`; the real stream URL is resolved lazily when loading. */
const val YTM_SCHEME = "ytm"

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(DownloaderImpl.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val resolving = ResolvingDataSource.Factory(http) { spec: DataSpec ->
            if (spec.uri.scheme == YTM_SCHEME) {
                val id = spec.uri.host ?: spec.uri.schemeSpecificPart.trimStart('/')
                spec.withUri(Uri.parse(MusicRepository.resolveAudioUrl(id)))
            } else spec
        }
        // Order: local files -> disk cache (keyed by ytm://id, so expiring URLs don't matter) -> resolver -> HTTP
        val cached = CacheDataSource.Factory()
            .setCache(MediaCache.get(this))
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSource = DefaultDataSource.Factory(this, cached)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** Process-wide LRU cache of streamed audio (SimpleCache must be a singleton per directory). */
object MediaCache {
    private const val MAX_BYTES = 128L * 1024 * 1024
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: android.content.Context): SimpleCache = cache ?: SimpleCache(
        java.io.File(context.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context),
    ).also { cache = it }

    fun sizeBytes() = cache?.cacheSpace ?: 0L
}
