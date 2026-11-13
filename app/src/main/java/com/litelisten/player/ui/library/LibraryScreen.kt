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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import com.litelisten.player.core.designsystem.component.EmptyState
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.SourceBadge
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
    val count by viewModel.count.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    val deniedText = stringResource(R.string.permission_denied)
    val playNextText = stringResource(R.string.action_play_next)
    val queuedText = stringResource(R.string.action_add_to_queue)
    val removedText = stringResource(R.string.action_remove_from_library)
    val favoritesText = stringResource(R.string.action_favorite)

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
                IconButton(onClick = { treeLauncher.launch(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_folder))
                }
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
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

        if (songs.isEmpty()) {
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
            when (selectedTab) {
                0 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(songs, key = { it.id }, contentType = { "track" }) { track ->
                        TrackRow(
                            track = track,
                            playing = false,
                            onClick = { viewModel.play(track, songs) },
                            onMore = { actionTrack = track },
                        )
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
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(folders, key = { it }) { folder ->
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
        )
    }
}
