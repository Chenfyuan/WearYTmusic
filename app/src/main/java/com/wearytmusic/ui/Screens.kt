package com.wearytmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import coil.compose.AsyncImage
import com.wearytmusic.R
import com.wearytmusic.data.Track

@Composable
fun WearApp(vm: MainViewModel) {
    MaterialTheme {
        val nav = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                val now by vm.now.collectAsStateWithLifecycle()
                val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
                val downloads by vm.downloads.collectAsStateWithLifecycle()
                HomeScreen(
                    now = now,
                    loggedIn = loggedIn,
                    downloadCount = downloads.size,
                    onSearch = { nav.navigate("search") },
                    onNowPlaying = { nav.navigate("player") },
                    onLibrary = { vm.loadLibrary(); nav.navigate("library") },
                    onDownloads = { nav.navigate("downloads") },
                    onLogin = { nav.navigate("login") },
                    onLogout = vm::logout,
                )
            }
            composable("search") {
                val state by vm.search.collectAsStateWithLifecycle()
                val now by vm.now.collectAsStateWithLifecycle()
                SearchScreen(
                    state = state,
                    now = now,
                    onQueryChange = vm::onQueryChange,
                    onSearch = vm::runSearch,
                    onPlay = { i -> vm.playSearch(i); nav.navigate("player") },
                    onNowPlaying = { nav.navigate("player") },
                )
            }
            composable("library") {
                val state by vm.library.collectAsStateWithLifecycle()
                LibraryScreen(state, onRetry = vm::loadLibrary, onOpen = { vm.openPlaylist(it); nav.navigate("playlist") })
            }
            composable("playlist") {
                val state by vm.playlist.collectAsStateWithLifecycle()
                PlaylistScreen(
                    state,
                    onPlay = { i -> vm.playTracks(state.tracks, i); nav.navigate("player") },
                    onDownloadAll = { vm.downloadAll(state.tracks) },
                )
            }
            composable("downloads") {
                val items by vm.downloads.collectAsStateWithLifecycle()
                DownloadsScreen(
                    items,
                    onPlay = { i -> vm.playTracks(items, i); nav.navigate("player") },
                    onClear = vm::clearDownloads,
                )
            }
            composable("player") {
                val now by vm.now.collectAsStateWithLifecycle()
                val done by vm.downloads.collectAsStateWithLifecycle()
                val running by vm.downloading.collectAsStateWithLifecycle()
                PlayerScreen(
                    now = now,
                    download = vm.downloadStateOf(now.videoId, done, running),
                    onPrevious = vm::previous,
                    onTogglePlay = vm::togglePlay,
                    onNext = vm::next,
                    onLyrics = { vm.loadLyrics(); nav.navigate("lyrics") },
                    onDownload = vm::toggleDownloadCurrent,
                )
            }
            composable("lyrics") {
                val state by vm.lyrics.collectAsStateWithLifecycle()
                LyricsScreen(state, position = vm::positionMs, onRetry = vm::loadLyrics)
            }
            composable("login") {
                LoginScreen(onCookie = { vm.onLoggedIn(it); nav.popBackStack() })
            }
        }
    }
}

/** Shared list scaffold: time text, vignette, scroll indicator, first item centred. */
@Composable
fun ListScaffold(centerFirst: Boolean = false, content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = if (centerFirst) AutoCenteringParams(itemIndex = 0) else AutoCenteringParams(itemIndex = 1),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
fun TrackChip(track: Track, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = track.artworkUrl?.let { url ->
            {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                )
            }
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SearchScreen(
    state: SearchState,
    now: NowPlaying,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (Int) -> Unit,
    onNowPlaying: () -> Unit,
) {
    ListScaffold(centerFirst = true) {
        item {
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp))
                        Box(Modifier.padding(start = 8.dp)) {
                            if (state.query.isEmpty()) {
                                Text("搜索歌曲", color = MaterialTheme.colors.onSurfaceVariant)
                            }
                            inner()
                        }
                    }
                },
            )
        }
        if (now.hasItem) {
            item {
                Chip(
                    onClick = onNowPlaying,
                    label = { Text("正在播放", maxLines = 1) },
                    secondaryLabel = { Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.loading) item { CircularProgressIndicator(Modifier.size(28.dp)) }
        state.error?.let { msg ->
            item { Text(msg, color = MaterialTheme.colors.error, textAlign = TextAlign.Center) }
        }
        itemsIndexed(state.results) { index, track -> TrackChip(track) { onPlay(index) } }
    }
}

@Composable
fun PlayerScreen(
    now: NowPlaying,
    download: DownloadState,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onLyrics: () -> Unit,
    onDownload: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        now.artworkUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ListScaffold(centerFirst = true) {
            item {
                androidx.compose.foundation.layout.Column(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        now.title,
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        now.error ?: now.artist,
                        style = MaterialTheme.typography.caption1,
                        color = if (now.error != null) MaterialTheme.colors.error else MaterialTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onPrevious, colors = ButtonDefaults.iconButtonColors(), modifier = Modifier.size(40.dp)) {
                        Icon(painterResource(R.drawable.ic_prev), "上一首")
                    }
                    Button(onClick = onTogglePlay, modifier = Modifier.size(52.dp)) {
                        if (now.buffering && !now.isPlaying) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            Icon(
                                painterResource(if (now.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                if (now.isPlaying) "暂停" else "播放",
                            )
                        }
                    }
                    Button(onClick = onNext, colors = ButtonDefaults.iconButtonColors(), modifier = Modifier.size(40.dp)) {
                        Icon(painterResource(R.drawable.ic_next), "下一首")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactChip(onClick = onLyrics, label = { Text("歌词") })
                    CompactChip(
                        onClick = onDownload,
                        label = {
                            Text(
                                when (download) {
                                    DownloadState.NONE -> "下载"
                                    DownloadState.RUNNING -> "下载中"
                                    DownloadState.DONE -> "已下载"
                                },
                            )
                        },
                        colors = if (download == DownloadState.DONE) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
    }
}
