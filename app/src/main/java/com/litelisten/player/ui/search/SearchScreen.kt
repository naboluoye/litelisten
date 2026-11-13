package com.litelisten.player.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.SectionHeader
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
    val noResultText = stringResource(R.string.search_no_result, query)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.l),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setQuery(it) },
            singleLine = true,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.s),
        )

        if (query.isBlank()) {
            SectionHeader(
                title = stringResource(R.string.search_recent),
                action = {
                    if (recent.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearRecent() }) {
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
            return@Column
        }

        val hasResults = result.localTracks.isNotEmpty() ||
            result.localAlbums.isNotEmpty() || result.localArtists.isNotEmpty()
        if (!hasResults) {
            Text(
                noResultText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = spacing.xl),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (result.localTracks.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.search_group_songs)) }
                items(result.localTracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        playing = false,
                        onClick = { viewModel.play(track, result.localTracks); viewModel.submit(query) },
                        onMore = { viewModel.submit(query) },
                    )
                }
            }
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
