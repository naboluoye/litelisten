package com.litelisten.player.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.SectionHeader
import com.litelisten.player.core.designsystem.component.TrackRowPlaceholder
import com.litelisten.player.core.designsystem.theme.LocalSpacing

@Composable
fun SourcesScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val spacing = LocalSpacing.current
    val pending = stringResource(R.string.snack_p3_pending)
    val pendingP4 = stringResource(R.string.snack_p4_pending)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        LiteListenTopBar(
            title = stringResource(R.string.sources_title),
            actions = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sources_add_smb)) },
                        onClick = { menuExpanded = false; onMessage(pending) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sources_add_webdav)) },
                        onClick = { menuExpanded = false; onMessage(pendingP4) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sources_scan_dlna)) },
                        onClick = { menuExpanded = false; onMessage(pendingP4) },
                    )
                }
            },
        )

        SourceGroup(title = stringResource(R.string.sources_group_local)) {
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l)) {
                Text(
                    text = stringResource(R.string.sources_local_card),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(spacing.l),
                )
                Text(
                    text = stringResource(R.string.sources_local_card_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
                )
            }
        }
        SourceGroup(title = stringResource(R.string.sources_group_servers)) {
            repeat(2) { TrackRowPlaceholder() }
        }
        SourceGroup(title = stringResource(R.string.sources_group_discovered)) {
            TrackRowPlaceholder()
        }
    }
}

@Composable
private fun SourceGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    SectionHeader(title = title)
    content()
}
