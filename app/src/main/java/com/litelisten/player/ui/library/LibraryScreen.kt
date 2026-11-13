package com.litelisten.player.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.EmptyState
import com.litelisten.player.core.designsystem.component.LiteListenTopBar

@Composable
fun LibraryScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.library_tab_songs,
        R.string.library_tab_albums,
        R.string.library_tab_artists,
        R.string.library_tab_folders,
    )
    val pending = stringResource(R.string.snack_p1_pending)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        LiteListenTopBar(
            title = stringResource(R.string.library_title),
            actions = {
                IconButton(onClick = { onMessage(pending) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                }
                IconButton(onClick = { onMessage(pending) }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                }
            },
        )
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
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
        EmptyState(
            icon = Icons.Filled.LibraryMusic,
            title = stringResource(R.string.empty_library_title),
            description = stringResource(R.string.empty_library_desc),
            primaryAction = stringResource(R.string.action_scan_local) to { onMessage(pending) },
            secondaryAction = stringResource(R.string.action_add_server) to { onMessage(pending) },
        )
    }
}
