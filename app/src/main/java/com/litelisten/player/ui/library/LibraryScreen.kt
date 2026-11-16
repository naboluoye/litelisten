package com.litelisten.player.ui.library

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.AddToPlaylistSheet
import com.litelisten.player.core.designsystem.component.EmptyState
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.TrackActionSheet
import com.litelisten.player.core.designsystem.component.TrackArtwork
import com.litelisten.player.core.designsystem.component.TrackRow
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.SortKey
import com.litelisten.player.core.model.SortOrder
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.scan.ScanState
import com.litelisten.player.ui.navigation.Routes

@Composable
fun LibraryScreen(
    onMessage: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTarget by remember { mutableStateOf<List<Track>>(emptyList()) }
    var confirmReset by remember { mutableStateOf(false) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val deniedText = stringResource(R.string.permission_denied)
    val playNextText = stringResource(R.string.action_play_next)
    val queuedText = stringResource(R.string.action_add_to_queue)
    val removedText = stringResource(R.string.action_remove_from_library)
    val favoritesText = stringResource(R.string.action_favorite)
    val alreadyText = stringResource(R.string.playlist_added_none)
    val quickDoneText = stringResource(R.string.playlist_quick_done)
    val quickDupText = stringResource(R.string.playlist_quick_duplicate)
    val resetDoneText = stringResource(R.string.library_reset_done)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.scanLocal() else onMessage(deniedText)
    }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.addFolder(uri)
        }
    }

    // 曲库有歌之后也要能重新扫描：权限已授予时 RequestPermission 会直接回调 granted=true。
    fun startScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    val tabs = listOf(
        R.string.library_tab_songs,
        R.string.library_tab_albums,
        R.string.library_tab_artists,
        R.string.library_tab_folders,
        R.string.library_tab_playlists,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LiteListenTopBar(
            title = stringResource(R.string.library_title),
            actions = {
                IconButton(onClick = { startScan() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_rescan))
                }
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
                }
                IconButton(onClick = { moreMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_scan_folder)) },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            treeLauncher.launch(null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_reset_library)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            confirmReset = true
                        },
                    )
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf(
                        SortKey.TITLE to R.string.sort_title,
                        SortKey.ARTIST to R.string.sort_artist,
                        SortKey.ALBUM to R.string.sort_album,
                        SortKey.DURATION to R.string.sort_duration,
                        SortKey.DATE_ADDED to R.string.sort_date_added,
                        SortKey.LAST_PLAYED to R.string.sort_last_played,
                    ).forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = { viewModel.setSort(key); sortMenu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (order == SortOrder.ASC) R.string.sort_desc else R.string.sort_asc,
                                ),
                            )
                        },
                        onClick = {
                            viewModel.setOrder(if (order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC)
                            sortMenu = false
                        },
                    )
                }
            },
        )
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = spacing.l,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            tabs.forEachIndexed { index, labelRes ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(labelRes), maxLines = 1) },
                )
            }
        }

        when (val scan = scanState) {
            is ScanState.Running -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.library_scanning, scan.scanned),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
                )
            }
            is ScanState.Done -> Text(
                text = stringResource(R.string.library_scan_done, scan.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
            )
            is ScanState.Failed -> Text(
                text = stringResource(scan.messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
            )
            ScanState.Idle -> Unit
        }

        when (selectedTab) {
            0 -> if (songs.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.LibraryMusic,
                    title = stringResource(R.string.empty_library_title),
                    description = stringResource(R.string.empty_library_desc),
                    primaryAction = stringResource(R.string.action_scan_local) to { startScan() },
                    secondaryAction = stringResource(R.string.action_add_server) to {
                        onNavigate(Routes.SOURCES)
                    },
                )
            } else {
                Text(
                    text = stringResource(R.string.library_songs_count, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(songs, key = { it.id }, contentType = { "track" }) { track ->
                        TrackRow(
                            track = track,
                            playing = false,
                            onClick = { viewModel.play(track, songs) },
                            onMore = { actionTrack = track },
                        )
                    }
                }
            }

            1 -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.key }) { album ->
                    OutlinedCard(
                        onClick = { onNavigate(Routes.album(album.name)) },
                        modifier = Modifier.padding(spacing.s),
                    ) {
                        Column(modifier = Modifier.padding(spacing.m)) {
                            TrackArtwork(album.artworkKey, sizeDp = 96)
                            Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                stringResource(R.string.track_count, album.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            2 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(artists, key = { it.key }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.l, vertical = spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    ) {
                        TrackArtwork(artist.artworkKey, sizeDp = 48)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                stringResource(R.string.album_count, artist.albumCount, artist.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onNavigate(Routes.artist(artist.name)) }) {
                            Text(stringResource(R.string.action_browse))
                        }
                    }
                }
            }

            3 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (folders.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.library_folders_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(spacing.l),
                        )
                    }
                }
                items(folders, key = { "folder-${it}" }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.l, vertical = spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Text(folder, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { onNavigate(Routes.folder(folder)) }) {
                            Text(stringResource(R.string.action_browse))
                        }
                    }
                }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s)) {
                        Text(
                            text = stringResource(R.string.playlist_count, playlists.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { newPlaylistName = ""; creatingPlaylist = true },
                            modifier = Modifier.padding(top = spacing.s),
                        ) {
                            Text(stringResource(R.string.playlist_new))
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.playlist_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(spacing.l),
                        )
                    }
                }
                items(playlists, key = { "playlist-${it.id}" }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.l, vertical = spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    ) {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                stringResource(R.string.track_count, playlist.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onNavigate(Routes.playlist(playlist.id)) }) {
                            Text(stringResource(R.string.action_browse))
                        }
                    }
                }
            }
        }
    }

    actionTrack?.let { track ->
        TrackActionSheet(
            track = track,
            onDismiss = { actionTrack = null },
            onPlayNext = { viewModel.playNext(track); onMessage(playNextText) },
            onAddToQueue = { viewModel.addToQueue(track); onMessage(queuedText) },
            onFavorite = { viewModel.favorite(track); onMessage(favoritesText) },
            onInfo = {
                onMessage(track.displayArtist?.let { "${track.title} · $it" } ?: track.title)
            },
            onRemove = { viewModel.remove(track); onMessage(removedText) },
            onAddToPlaylist = { playlistTarget = listOf(track) },
            onQuickFavorite = {
                viewModel.quickFavorite(track) { added ->
                    onMessage(if (added > 0) quickDoneText else quickDupText)
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
                        if (added > 0) context.getString(R.string.playlist_added, added) else alreadyText,
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

    if (creatingPlaylist) {
        AlertDialog(
            onDismissRequest = { creatingPlaylist = false },
            title = { Text(stringResource(R.string.playlist_new)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it.take(40) },
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        val name = newPlaylistName.trim()
                        creatingPlaylist = false
                        viewModel.createPlaylist(name) {
                            onMessage(context.getString(R.string.playlist_created, name))
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { creatingPlaylist = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_library_title)) },
            text = { Text(stringResource(R.string.reset_library_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetLibrary { onMessage(resetDoneText) }
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
