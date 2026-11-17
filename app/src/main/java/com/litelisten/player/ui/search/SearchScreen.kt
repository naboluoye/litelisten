package com.litelisten.player.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.AddToPlaylistSheet
import com.litelisten.player.core.designsystem.component.SectionHeader
import com.litelisten.player.core.designsystem.component.TrackArtwork
import com.litelisten.player.core.designsystem.component.TrackRow
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.ui.navigation.Routes

@Composable
fun SearchScreen(
    onMessage: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val selectMode by viewModel.selectMode.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistList.collectAsStateWithLifecycle()
    val noResultText = stringResource(R.string.search_no_result, query)
    val context = LocalContext.current
    var addingTo by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.setQuery(it) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) viewModel.submit(query) }),
                modifier = Modifier.weight(1f).padding(vertical = spacing.s),
            )
            if (query.isNotBlank()) {
                TextButton(onClick = { viewModel.toggleSelectMode(!selectMode) }) {
                    Text(
                        stringResource(
                            if (selectMode) R.string.search_exit_select else R.string.search_select_mode,
                        ),
                    )
                }
            }
        }

        if (query.isBlank()) {
            Column(modifier = Modifier.padding(horizontal = spacing.l)) {
                SectionHeader(
                    title = stringResource(R.string.search_recent),
                    action = {
                        if (recent.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearRecent() }) {
                                Text(stringResource(R.string.search_clear))
                            }
                        }
                    },
                )
                recent.forEach { item ->
                    AssistChip(
                        onClick = { viewModel.submit(item) },
                        label = { Text(item) },
                        modifier = Modifier.padding(end = spacing.s),
                    )
                }
                Text(
                    stringResource(R.string.search_hint_example),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xl),
                )
            }
            return@Column
        }

        val hasResults = result.localTracks.isNotEmpty() ||
            result.localAlbums.isNotEmpty() || result.localArtists.isNotEmpty()
        if (!hasResults) {
            Text(
                noResultText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.xl),
            )
            return@Column
        }

        if (selectMode) {
            Text(
                stringResource(R.string.search_select_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.xs),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (result.localTracks.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.search_group_songs)) }
                    items(result.localTracks, key = { it.id }) { track ->
                        if (selectMode) {
                            SelectableRow(
                                title = track.title,
                                subtitle = track.displayArtist,
                                checked = viewModel.isSelected(track.id),
                                artworkKey = track.artworkKey,
                                onToggle = { viewModel.toggleTrack(track) },
                            )
                        } else {
                            TrackRow(
                                track = track,
                                playing = false,
                                onClick = { viewModel.play(track, result.localTracks); viewModel.submit(query) },
                            )
                        }
                    }
                }
                if (selectMode && result.localAlbums.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.search_group_albums)) }
                    items(result.localAlbums, key = { it.key }) { album ->
                        SelectableRow(
                            title = album.name,
                            subtitle = album.artist,
                            checked = result.localTracks.any { it.album == album.name && viewModel.isSelected(it.id) },
                            artworkKey = album.artworkKey,
                            onToggle = {
                                val all = result.localTracks.filter { it.album == album.name }
                                val select = all.none { viewModel.isSelected(it.id) }
                                if (all.isNotEmpty()) viewModel.setTracksSelected(all, select)
                                else viewModel.selectGroup(SearchViewModel.GroupKind.ALBUM, album.name, select)
                            },
                        )
                    }
                }
                if (selectMode && result.localArtists.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.search_group_artists)) }
                    items(result.localArtists, key = { it.key }) { artist ->
                        SelectableRow(
                            title = artist.name,
                            subtitle = stringResource(R.string.album_count, artist.albumCount, artist.trackCount),
                            checked = result.localTracks.any { it.artist == artist.name && viewModel.isSelected(it.id) },
                            artworkKey = artist.artworkKey,
                            onToggle = {
                                val all = result.localTracks.filter { it.artist == artist.name }
                                val select = all.none { viewModel.isSelected(it.id) }
                                if (all.isNotEmpty()) viewModel.setTracksSelected(all, select)
                                else viewModel.selectGroup(SearchViewModel.GroupKind.ARTIST, artist.name, select)
                            },
                        )
                    }
                }
                if (!selectMode) {
                    if (result.localAlbums.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.search_group_albums)) }
                        items(result.localAlbums, key = { it.key }) { album ->
                            Text(
                                album.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(Routes.album(album.name)) }
                                    .padding(horizontal = spacing.l, vertical = spacing.m),
                            )
                        }
                    }
                    if (result.localArtists.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.search_group_artists)) }
                        items(result.localArtists, key = { it.key }) { artist ->
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(Routes.artist(artist.name)) }
                                    .padding(horizontal = spacing.l, vertical = spacing.m),
                            )
                        }
                    }
                }
            }
        }

        if (selectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .navigationBarsPadding()
                    .padding(horizontal = spacing.l, vertical = spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    viewModel.selectAll(result.localTracks, selected.size != result.localTracks.size)
                }) {
                    Text(
                        stringResource(
                            if (selected.size == result.localTracks.size && result.localTracks.isNotEmpty())
                                R.string.search_deselect_all else R.string.search_select_all,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.search_selected_count, selected.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).padding(horizontal = spacing.s),
                )
                Button(onClick = { addingTo = true }, enabled = selected.isNotEmpty()) {
                    Text(stringResource(R.string.search_add_selected))
                }
            }
        }
    }

    if (addingTo) {
        AddToPlaylistSheet(
            playlists = playlists,
            trackCount = selected.size,
            onDismiss = { addingTo = false },
            onPick = { playlist ->
                addingTo = false
                viewModel.addSelectedToPlaylist(playlist.id) { count ->
                    onMessage(context.getString(R.string.search_added_to_playlist, playlist.name, count))
                }
            },
            onCreate = { name ->
                addingTo = false
                viewModel.createPlaylistAndAdd(name) { count ->
                    onMessage(context.getString(R.string.search_added_to_playlist, name, count))
                }
            },
        )
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    artworkKey: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        TrackArtwork(artworkKey, sizeDp = 44)
        Column(modifier = Modifier.weight(1f).padding(horizontal = LocalSpacing.current.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
