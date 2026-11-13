package com.litelisten.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.TrackArtwork
import com.litelisten.player.core.designsystem.theme.LocalSpacing

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val currentId = state.current?.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LiteListenTopBar(title = stringResource(R.string.queue_title), onNavigateUp = onBack)
        if (queue.isEmpty()) {
            Text(
                stringResource(R.string.queue_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(spacing.l),
            )
            return@Column
        }
        Text(
            stringResource(R.string.queue_next, queue.size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(queue, key = { index, track -> "$index-${track.id}" }) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.play(track, queue) }
                        .padding(horizontal = spacing.l, vertical = spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArtwork(track.artworkKey, sizeDp = 44)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = spacing.m)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (track.id == currentId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            track.displayArtist ?: stringResource(R.string.player_unknown_artist),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { viewModel.removeFromQueue(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(spacing.l)) {
            TextButton(onClick = { viewModel.shuffle(!state.shuffle) }) {
                Text(stringResource(R.string.action_shuffle_queue))
            }
            TextButton(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.action_clear_queue))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.action_clear_queue)) },
            text = { Text(stringResource(R.string.confirm_clear_queue)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearQueue(); confirmClear = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
