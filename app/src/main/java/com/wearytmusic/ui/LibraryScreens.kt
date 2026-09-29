package com.wearytmusic.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.wearytmusic.data.PlaylistSummary
import com.wearytmusic.data.Track

@Composable
fun HomeScreen(
    now: NowPlaying,
    loggedIn: Boolean,
    downloadCount: Int,
    onSearch: () -> Unit,
    onNowPlaying: () -> Unit,
    onLibrary: () -> Unit,
    onDownloads: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    ListScaffold(centerFirst = true) {
        if (now.hasItem) item { MenuChip("正在播放", now.title, onNowPlaying, primary = true) }
        item { MenuChip("搜索", null, onSearch) }
        if (loggedIn) item { MenuChip("我的歌单", null, onLibrary) }
        item { MenuChip("已下载", if (downloadCount > 0) "$downloadCount 首" else null, onDownloads) }
        if (loggedIn) item { MenuChip("退出登录", null, onLogout) } else item { MenuChip("登录 YouTube Music", null, onLogin) }
    }
}

@Composable
private fun MenuChip(label: String, secondary: String?, onClick: () -> Unit, primary: Boolean = false) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Message(text: String, error: Boolean = false) =
    Text(text, textAlign = TextAlign.Center,
        color = if (error) MaterialTheme.colors.error else MaterialTheme.colors.onSurfaceVariant)

@Composable
fun LibraryScreen(state: LibraryState, onRetry: () -> Unit, onOpen: (PlaylistSummary) -> Unit) {
    ListScaffold {
        item { Text("我的歌单", style = MaterialTheme.typography.title3) }
        if (state.loading) item { CircularProgressIndicator(Modifier.size(28.dp)) }
        state.error?.let { msg ->
            item { Message(msg, error = true) }
            item { MenuChip("重试", null, onRetry) }
        }
        itemsIndexed(state.playlists) { _, p ->
            Chip(
                onClick = { onOpen(p) },
                label = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = p.subtitle.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                icon = p.artworkUrl?.let { url ->
                    { AsyncImage(url, null, Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop) }
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PlaylistScreen(state: PlaylistState, onPlay: (Int) -> Unit, onDownloadAll: () -> Unit) {
    ListScaffold {
        item { Text(state.title, style = MaterialTheme.typography.title3, maxLines = 2, textAlign = TextAlign.Center) }
        if (state.loading) item { CircularProgressIndicator(Modifier.size(28.dp)) }
        state.error?.let { item { Message(it, error = true) } }
        if (state.tracks.isNotEmpty()) {
            item { MenuChip("播放全部", "${state.tracks.size} 首", { onPlay(0) }, primary = true) }
            item { MenuChip("全部下载", null, onDownloadAll) }
        }
        itemsIndexed(state.tracks) { i, t -> TrackChip(t) { onPlay(i) } }
    }
}

@Composable
fun DownloadsScreen(items: List<Track>, onPlay: (Int) -> Unit, onClear: () -> Unit) {
    ListScaffold {
        item { Text("已下载", style = MaterialTheme.typography.title3) }
        if (items.isEmpty()) item { Message("暂无下载\n在播放页点“下载”") }
        itemsIndexed(items) { i, t -> TrackChip(t) { onPlay(i) } }
        if (items.isNotEmpty()) item { MenuChip("清空全部下载", null, onClear) }
    }
}
