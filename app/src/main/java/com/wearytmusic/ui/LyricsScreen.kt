package com.wearytmusic.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

@Composable
fun LyricsScreen(state: LyricsState, position: () -> Long, onRetry: () -> Unit) {
    val lyrics = state.lyrics
    when {
        state.loading -> Center { CircularProgressIndicator(Modifier.size(28.dp)) }
        state.error != null -> Center {
            Text(state.error, textAlign = TextAlign.Center, color = MaterialTheme.colors.error)
            CompactChip(onClick = onRetry, label = { Text("重试") })
        }
        lyrics == null || lyrics.isEmpty -> Center { Text("暂无歌词", color = MaterialTheme.colors.onSurfaceVariant) }
        lyrics.synced.isNotEmpty() -> SyncedLyrics(lyrics.synced, position)
        else -> {
            val listState = rememberScalingLazyListState()
            ScalingLazyColumn(Modifier.fillMaxWidth(), state = listState) {
                lyrics.plain!!.lines().forEach { line ->
                    item { Text(line, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<com.wearytmusic.data.LyricLine>, position: () -> Long) {
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = position()
            delay(250)
        }
    }
    val current = lines.indexOfLast { it.timeMs <= pos + 200 }.coerceAtLeast(0)
    val listState = rememberScalingLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem(current) }
    ScalingLazyColumn(Modifier.fillMaxWidth(), state = listState) {
        itemsIndexed(lines) { i, line ->
            Text(
                line.text,
                textAlign = TextAlign.Center,
                style = if (i == current) MaterialTheme.typography.title3 else MaterialTheme.typography.body2,
                color = if (i == current) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun Center(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        content = content,
    )
}
