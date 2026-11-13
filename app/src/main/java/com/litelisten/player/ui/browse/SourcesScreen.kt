package com.litelisten.player.ui.browse

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import com.litelisten.player.core.designsystem.component.SectionHeader
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.scan.ScanState
import com.litelisten.player.ui.navigation.Routes

@Composable
fun SourcesScreen(
    onMessage: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val discovering by viewModel.discovering.collectAsStateWithLifecycle()
    val count by viewModel.trackCount.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val deniedText = stringResource(R.string.permission_denied)
    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.scanLocal() else onMessage(deniedText)
    }
    fun startScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        scanPermissionLauncher.launch(permission)
    }

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
                        onClick = { menuExpanded = false; onNavigate(Routes.ADD_SMB) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sources_add_webdav)) },
                        onClick = { menuExpanded = false; onNavigate(Routes.ADD_WEBDAV) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sources_scan_dlna)) },
                        onClick = { menuExpanded = false; viewModel.discover() },
                    )
                }
            },
        )

        if (scanState is ScanState.Running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        SectionHeader(title = stringResource(R.string.sources_group_local))
        OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l)) {
            Column(modifier = Modifier.padding(spacing.l)) {
                Text(stringResource(R.string.sources_local_card), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.sources_local_card_sub, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { startScan() }, modifier = Modifier.padding(top = spacing.s)) {
                    Text(stringResource(R.string.action_scan_now))
                }
            }
        }

        SectionHeader(title = stringResource(R.string.sources_group_servers))
        if (sources.isEmpty()) {
            Text(
                text = stringResource(R.string.sources_empty_server),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.m),
            )
        } else {
            sources.forEach { source ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l, vertical = spacing.s)) {
                    Column(modifier = Modifier.padding(spacing.l)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Cloud, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(start = spacing.m)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    source.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    stringResource(R.string.source_tracks, source.trackCount),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Row {
                            TextButton(onClick = { onNavigate(Routes.browser(source.id)) }) {
                                Text(stringResource(R.string.action_browse))
                            }
                            TextButton(onClick = { pendingDelete = source.id }) {
                                Text(stringResource(R.string.action_delete_source))
                            }
                        }
                    }
                }
            }
        }

        SectionHeader(
            title = stringResource(R.string.sources_group_discovered),
            action = { TextButton(onClick = { viewModel.discover() }) { Text(stringResource(R.string.action_refresh)) } },
        )
        if (discovering) {
            Text(
                stringResource(R.string.sources_discovering),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
        } else if (devices.isEmpty()) {
            Text(
                stringResource(R.string.sources_no_device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.m),
            )
        } else {
            devices.forEach { device ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l, vertical = spacing.s)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(spacing.l),
                    ) {
                        Text(device.name, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = {
                            viewModel.addDevice(device) { id -> onNavigate(Routes.browser(id)) }
                        }) { Text(stringResource(R.string.action_connect)) }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        val name = sources.firstOrNull { it.id == pendingDelete }?.name.orEmpty()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.action_delete_source)) },
            text = { Text(stringResource(R.string.confirm_delete_source, name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { viewModel.remove(it) }
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
