package com.wearytmusic.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.wearytmusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.FileOutputStream

/** Downloads one track's audio into [DownloadStore], in 1 MiB ranged chunks (avoids YouTube throttling). */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val track = Track(
            inputData.getString("id") ?: return Result.failure(),
            inputData.getString("title").orEmpty(),
            inputData.getString("artist").orEmpty(),
            inputData.getString("art"),
            inputData.getLong("dur", 0),
        )
        if (DownloadStore.isDownloaded(track.videoId)) return Result.success()
        DownloadStore.setRunning(track.videoId, true)
        return try {
            setForeground(foregroundInfo(track.title))
            withContext(Dispatchers.IO) { download(track) }
            DownloadStore.add(track)
            Result.success()
        } catch (e: Exception) {
            DownloadStore.partFor(track.videoId).delete()
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            DownloadStore.setRunning(track.videoId, false)
        }
    }

    private fun download(track: Track) {
        val url = MusicRepository.resolveAudioUrl(track.videoId)
        val total = Uri.parse(url).getQueryParameter("clen")?.toLongOrNull()
        val part = DownloadStore.partFor(track.videoId)
        FileOutputStream(part).use { out ->
            if (total == null) {
                fetch(url).use { it.copyTo(out) }
            } else {
                var pos = 0L
                while (pos < total) {
                    val end = minOf(pos + CHUNK, total) - 1
                    fetch("$url&range=$pos-$end").use { it.copyTo(out) }
                    pos = end + 1
                }
            }
        }
        check(part.renameTo(DownloadStore.fileFor(track.videoId))) { "rename failed" }
    }

    private fun fetch(url: String): java.io.InputStream {
        val req = Request.Builder().url(url).header("User-Agent", DownloaderImpl.USER_AGENT).build()
        val resp = Http.client.newCall(req).execute()
        if (!resp.isSuccessful) { resp.close(); error("HTTP ${resp.code}") }
        return resp.body!!.byteStream()
    }

    private fun foregroundInfo(title: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "下载", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("正在下载")
            .setContentText(title)
            .setOngoing(true)
            .build()
        return ForegroundInfo(title.hashCode(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val CHUNK = 1L shl 20
        private const val CHANNEL = "downloads"

        fun enqueue(context: Context, t: Track) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("id" to t.videoId, "title" to t.title, "artist" to t.artist,
                    "art" to t.artworkUrl, "dur" to t.durationSec))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("dl-${t.videoId}", ExistingWorkPolicy.KEEP, req)
        }
    }
}
