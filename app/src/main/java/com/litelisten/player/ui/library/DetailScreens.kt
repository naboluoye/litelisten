package com.litelisten.player.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.common.FolderPaths
import com.litelisten.player.core.designsystem.component.AddToPlaylistSheet
import com.litelisten.player.core.designsystem.component.EmptyState
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.TrackActionSheet
import com.litelisten.player.core.designsystem.component.TrackRow
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.Track

@Composable
private fun TrackListPage(
    title: String,
    tracks: List<Track>,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: LibraryViewModel,
    addAllLabel: Int? = null,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTarget by remember { mutableStateOf<List<Track>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LiteListenTopBar(title = title, onNavigateUp = onBack)
        if (tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.LibraryMusic,
                title = stringResource(R.string.folder_empty),
                description = stringResource(R.string.empty_library_desc),
            )
        } else {
            Text(
                text = stringResource(R.string.track_count, tracks.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.l),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                Button(
                    onClick = { viewModel.playAll(tracks) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_play_all))
                }
                if (addAllLabel != null) {
                    OutlinedButton(
                        onClick = { playlistTarget = tracks },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(addAllLabel), maxLines = 1)
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        playing = false,
                        onClick = { viewModel.play(track, tracks) },
                        onMore = { actionTrack = track },
                    )
                }
            }
        }
    }
    actionTrack?.let { track ->
        TrackActionSheet(
            track = track,
            onDismiss = { actionTrack = null },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onFavorite = { viewModel.favorite(track) },
            onInfo = { onMessage(track.path) },
            onRemove = { viewModel.remove(track) },
            onAddToPlaylist = { playlistTarget = listOf(track) },
            onQuickFavorite = {
                viewModel.quickFavorite(track) { added ->
                    onMessage(
                        context.getString(
                            if (added > 0) R.string.playlist_quick_done else R.string.playlist_quick_duplicate
                        )
                    )
                }
            },
        )
    }

    if (playlistTarget.isNotEmpty()) {
        val target = playlistTarget
        AddToPlaylistSheet(
            playlists = playlists,
            trackCount = target.size,
            onDismiss = { playlistTarget = emptyList() },
            onPick = { playlist ->
                playlistTarget = emptyList()
                viewModel.addToPlaylist(playlist.id, target) { added ->
                    onMessage(
                        if (added > 0) context.getString(R.string.playlist_added, added)
                        else context.getString(R.string.playlist_added_none),
                    )
                }
            },
            onCreate = { name ->
                playlistTarget = emptyList()
                viewModel.createPlaylist(name, target) {
                    onMessage(context.getString(R.string.playlist_created, name))
                }
            },
        )
    }
}

@Composable
fun AlbumDetailScreen(
    name: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val tracks = songs.filter { it.album == name }
    TrackListPage(name, tracks, onBack, onMessage, viewModel, R.string.album_add_to_playlist)
}

@Composable
fun ArtistDetailScreen(
    name: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val tracks = songs.filter { it.artist == name }
    TrackListPage(name, tracks, onBack, onMessage, viewModel, R.string.album_add_to_playlist)
}

@Composable
fun FolderDetailScreen(
    name: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val tracks = songs.filter { FolderPaths.isUnder(name, it.path) }
    TrackListPage(name, tracks, onBack, onMessage, viewModel, R.string.folder_add_to_playlist)
}
