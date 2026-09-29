package com.wearytmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
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

@Composable
fun WearApp(vm: MainViewModel) {
    MaterialTheme {
        val nav = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(navController = nav, startDestination = "search") {
            composable("search") {
                val state by vm.search.collectAsStateWithLifecycle()
                val now by vm.now.collectAsStateWithLifecycle()
                SearchScreen(
                    state = state,
                    now = now,
                    onQueryChange = vm::onQueryChange,
                    onSearch = vm::runSearch,
                    onPlay = { i ->
                        vm.playFrom(i)
                        nav.navigate("player")
                    },
                    onNowPlaying = { nav.navigate("player") },
                )
            }
            composable("player") {
                val now by vm.now.collectAsStateWithLifecycle()
                PlayerScreen(now, vm::previous, vm::togglePlay, vm::next)
            }
        }
    }
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
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
            autoCentering = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
            itemsIndexed(state.results) { index, track ->
                Chip(
                    onClick = { onPlay(index) },
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
        }
    }
}

@Composable
fun PlayerScreen(now: NowPlaying, onPrevious: () -> Unit, onTogglePlay: () -> Unit, onNext: () -> Unit) {
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
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
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
                now.artist,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 10.dp),
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
    }
}
