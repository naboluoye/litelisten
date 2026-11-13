package com.litelisten.player.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.RemoteEntry

@Composable
fun FileBrowserScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addedText = stringResource(R.string.source_scan_result)

    BackHandler(enabled = state.path.trim('/').isNotEmpty()) {
        val parent = state.path.trim('/').substringBeforeLast('/', "")
        viewModel.load(if (parent.isBlank()) "" else "/$parent")
    }

    LaunchedEffect(state.scanResult) {
        state.scanResult?.let {
            onMessage(addedText.format(it))
            viewModel.consumeScanResult()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LiteListenTopBar(
            title = stringResource(R.string.browser_title),
            onNavigateUp = onBack,
            actions = {
                TextButton(onClick = { viewModel.addToLibrary() }) {
                    Text(stringResource(R.string.action_add_to_library))
                }
                TextButton(onClick = { viewModel.load(state.path) }) {
                    Text(stringResource(R.string.action_refresh))
                }
            },
        )
        Text(
            text = if (state.path.isBlank()) stringResource(R.string.browser_root) else state.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = spacing.l),
        )
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.messageRes?.let { res ->
            Text(
                text = stringResource(res, state.messageArg),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
            )
            TextButton(onClick = { viewModel.load(state.path) }, modifier = Modifier.padding(horizontal = spacing.l)) {
                Text(stringResource(R.string.action_retry))
            }
        }
        if (state.entries.isEmpty() && !state.loading) {
            Text(
                stringResource(R.string.browser_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(spacing.l),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.entries, key = { it.path }) { entry ->
                val supported = entry.isDirectory ||
                    MimeTypes.isSupported(entry.name) || entry.playableUri != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = supported) {
                            if (entry.isDirectory) viewModel.load(entry.path) else viewModel.play(entry)
                        }
                        .padding(horizontal = spacing.l, vertical = spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.AudioFile,
                        contentDescription = null,
                        tint = if (supported) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = spacing.m)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        if (!entry.isDirectory) {
                            Text(
                                text = entry.sizeBytes?.let {
                                    stringResource(R.string.browser_size_kb, it / 1024)
                                } ?: stringResource(R.string.browser_unsupported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!supported) {
                        Text(
                            stringResource(R.string.browser_unsupported),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
