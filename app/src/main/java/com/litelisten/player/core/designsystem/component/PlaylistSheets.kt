package com.litelisten.player.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.Playlist

/**
 * 「加入歌单」面板：选已有歌单，或当场新建。
 * 传进来的 [trackCount] 大于 1 时用于文件夹整体加入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    trackCount: Int,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = spacing.l),
        ) {
            Text(
                text = stringResource(R.string.playlist_add_to),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.playlist_add_count, trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.l, vertical = spacing.m),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.l),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { if (playlists.isEmpty()) onDismiss() else creating = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = { onCreate(name.trim()) },
                        modifier = Modifier.padding(start = spacing.s),
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name, maxLines = 1) },
                            supportingContent = {
                                Text(stringResource(R.string.track_count, playlist.trackCount))
                            },
                            modifier = Modifier.clickable { onPick(playlist) },
                        )
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.playlist_new)) },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.clickable { creating = true },
                )
            }
        }
    }
}

/** 播放前选歌单：用整个歌单替换当前队列。 */
@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.queue_load_playlist)) },
        text = {
            if (playlists.isEmpty()) {
                Text(stringResource(R.string.playlist_empty_desc))
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.queue_load_playlist_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name, maxLines = 1) },
                            supportingContent = {
                                Text(stringResource(R.string.track_count, playlist.trackCount))
                            },
                            leadingContent = {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onPick(playlist) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
