package com.litelisten.player.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.TrackArtwork
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.player.LyricLine

@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by playerViewModel.state.collectAsStateWithLifecycle()
    val track = state.current
    val manual by lyricsViewModel.manual.collectAsStateWithLifecycle()
    val failedText = stringResource(R.string.lyrics_pick_failed)

    var auto by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(track?.id) { auto = lyricsViewModel.auto(track) }
    val lines = track?.id?.let { manual[it] }?.takeIf { it.isNotEmpty() } ?: auto

    val pickLyrics = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        track?.let { current ->
            lyricsViewModel.attach(current.id, uri) { ok ->
                if (!ok) onMessage(failedText)
            }
        }
    }

    val listState = rememberLazyListState()
    val currentIndex = lines.indexOfLast { it.timeMs <= state.positionMs }.coerceAtLeast(0)

    LaunchedEffect(currentIndex, lines.size) {
        if (lines.isNotEmpty()) runCatching { listState.animateScrollToItem(currentIndex) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LiteListenTopBar(title = stringResource(R.string.player_lyrics), onNavigateUp = onBack)
        if (lines.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TrackArtwork(track?.artworkKey, sizeDp = 160)
                Text(
                    stringResource(R.string.lyrics_none),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = spacing.l),
                )
                Text(
                    stringResource(R.string.lyrics_none_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { pickLyrics.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(top = spacing.l),
                ) {
                    Text(stringResource(R.string.lyrics_pick))
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
                    Text(
                        text = line.text,
                        style = if (index == currentIndex) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                        color = if (index == currentIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playerViewModel.seek(line.timeMs) }
                            .padding(horizontal = spacing.l, vertical = spacing.m),
                    )
                }
            }
        }
    }
}
