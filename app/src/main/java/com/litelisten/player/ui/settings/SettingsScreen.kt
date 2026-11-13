package com.litelisten.player.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.litelisten.player.BuildConfig
import com.litelisten.player.R
import com.litelisten.player.core.datastore.SettingsKeys
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.SectionHeader
import com.litelisten.player.core.model.ThemeMode
import com.litelisten.player.ui.navigation.Routes

@Composable
fun SettingsScreen(
    onMessage: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    val clearedText = stringResource(R.string.settings_cache_cleared)
    val reindexText = stringResource(R.string.library_reindex_done)

    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()
    val deniedText = stringResource(R.string.permission_denied)
    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.scanNow() else onMessage(deniedText)
    }
    fun startScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        scanPermissionLauncher.launch(permission)
    }

    androidx.compose.runtime.LaunchedEffect(cacheCleared) {
        when {
            cacheCleared == null -> Unit
            cacheCleared == -1L -> onMessage(reindexText)
            else -> onMessage(clearedText.format((cacheCleared ?: 0L) / 1024L))
        }
        if (cacheCleared != null) viewModel.consumeCacheCleared()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        LiteListenTopBar(title = stringResource(R.string.settings_title))

        SectionHeader(title = stringResource(R.string.settings_group_appearance))
        ValueRow(stringResource(R.string.settings_theme), stringResource(themeMode.labelRes())) {
            dialog = "theme"
        }
        SwitchRow(
            stringResource(R.string.settings_dynamic_color),
            prefs[SettingsKeys.DYNAMIC_COLOR_KEY] ?: false,
        ) { viewModel.set(SettingsKeys.DYNAMIC_COLOR_KEY, it) }

        SectionHeader(title = stringResource(R.string.settings_group_playback))
        SwitchRow(
            stringResource(R.string.settings_headset_unplug),
            prefs[SettingsKeys.HEADSET_UNPLUG_PAUSE_KEY] ?: true,
        ) { viewModel.set(SettingsKeys.HEADSET_UNPLUG_PAUSE_KEY, it) }
        SwitchRow(
            stringResource(R.string.settings_fade_enabled),
            prefs[SettingsKeys.FADE_ENABLED_KEY] ?: true,
        ) { viewModel.set(SettingsKeys.FADE_ENABLED_KEY, it) }
        SwitchRow(
            stringResource(R.string.settings_resume_on_start),
            prefs[SettingsKeys.RESUME_ON_START_KEY] ?: true,
        ) { viewModel.set(SettingsKeys.RESUME_ON_START_KEY, it) }
        SwitchRow(
            stringResource(R.string.settings_auto_play_on_start),
            prefs[SettingsKeys.AUTO_PLAY_ON_START_KEY] ?: false,
        ) { viewModel.set(SettingsKeys.AUTO_PLAY_ON_START_KEY, it) }
        SwitchRow(
            stringResource(R.string.settings_skip_on_error),
            prefs[SettingsKeys.SKIP_ON_ERROR_KEY] ?: true,
        ) { viewModel.set(SettingsKeys.SKIP_ON_ERROR_KEY, it) }
        ValueRow(
            stringResource(R.string.settings_playback_speed),
            stringResource(
                R.string.player_speed_value,
                prefs[SettingsKeys.PLAYBACK_SPEED_KEY] ?: 1f,
            ),
        ) { dialog = "speed" }

        SectionHeader(title = stringResource(R.string.settings_group_audio))
        ValueRow(stringResource(R.string.settings_equalizer), "") { onNavigate(Routes.EQUALIZER) }

        SectionHeader(title = stringResource(R.string.settings_group_library))
        ValueRow(stringResource(R.string.settings_scan_now), "") { startScan() }
        ValueRow(stringResource(R.string.settings_clear_artwork_cache), "") { viewModel.clearArtworkCache() }
        ValueRow(stringResource(R.string.settings_rebuild_index), "") { dialog = "reindex" }
        SwitchRow(
            stringResource(R.string.settings_show_short_audio),
            prefs[SettingsKeys.SHOW_SHORT_AUDIO_KEY] ?: false,
        ) { viewModel.set(SettingsKeys.SHOW_SHORT_AUDIO_KEY, it) }

        SectionHeader(title = stringResource(R.string.settings_group_network))
        SwitchRow(
            stringResource(R.string.settings_wifi_only),
            prefs[SettingsKeys.WIFI_ONLY_KEY] ?: false,
        ) { viewModel.set(SettingsKeys.WIFI_ONLY_KEY, it) }
        ValueRow(
            stringResource(R.string.settings_connect_timeout),
            stringResource(
                R.string.settings_timeout_seconds,
                prefs[SettingsKeys.CONNECT_TIMEOUT_S_KEY] ?: 10,
            ),
        ) { dialog = "timeout" }
        ValueRow(
            stringResource(R.string.settings_cache_limit),
            stringResource(
                R.string.settings_cache_mb,
                prefs[SettingsKeys.CACHE_LIMIT_MB_KEY] ?: 512,
            ),
        ) { dialog = "cache" }
        ValueRow(
            stringResource(R.string.settings_buffer_seconds),
            stringResource(
                R.string.settings_buffer_value,
                prefs[SettingsKeys.BUFFER_SECONDS_KEY] ?: 15,
            ),
        ) { dialog = "buffer" }

        SectionHeader(title = stringResource(R.string.settings_group_about))
        ValueRow(stringResource(R.string.app_name), stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME)) {}
        ValueRow(stringResource(R.string.settings_open_licenses), "") { dialog = "licenses" }
        ValueRow(stringResource(R.string.settings_battery_settings), "") {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
            context.startActivity(intent)
        }
        ValueRow(stringResource(R.string.settings_export_diagnostics), "") {
            val file = java.io.File(context.cacheDir, "diagnostics.txt")
            file.writeText("LiteListen ${BuildConfig.VERSION_NAME}\n")
            onMessage(file.absolutePath)
        }
        ValueRow(stringResource(R.string.settings_notifications_off), "") {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
        }
    }

    when (dialog) {
        "theme" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.theme_dialog_title)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        ListItem(
                            headlineContent = { Text(stringResource(mode.labelRes())) },
                            leadingContent = {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode); dialog = null },
                                )
                            },
                            modifier = Modifier.clickable { viewModel.setThemeMode(mode); dialog = null },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        "speed" -> ChoiceDialog(
            title = stringResource(R.string.settings_playback_speed),
            options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map {
                stringResource(R.string.player_speed_value, it) to it
            },
            onDismiss = { dialog = null },
            onSelect = { value ->
                viewModel.set(SettingsKeys.PLAYBACK_SPEED_KEY, value as Float)
                dialog = null
            },
        )
        "timeout" -> ChoiceDialog(
            title = stringResource(R.string.settings_connect_timeout),
            options = listOf(5, 10, 15).map { stringResource(R.string.settings_timeout_seconds, it) to it },
            onDismiss = { dialog = null },
            onSelect = { value ->
                viewModel.set(SettingsKeys.CONNECT_TIMEOUT_S_KEY, value as Int)
                dialog = null
            },
        )
        "cache" -> ChoiceDialog(
            title = stringResource(R.string.settings_cache_limit),
            options = listOf(256, 512, 1024).map { stringResource(R.string.settings_cache_mb, it) to it },
            onDismiss = { dialog = null },
            onSelect = { value ->
                viewModel.set(SettingsKeys.CACHE_LIMIT_MB_KEY, value as Int)
                dialog = null
            },
        )
        "buffer" -> ChoiceDialog(
            title = stringResource(R.string.settings_buffer_seconds),
            options = listOf(5, 10, 15, 20).map { stringResource(R.string.settings_buffer_value, it) to it },
            onDismiss = { dialog = null },
            onSelect = { value ->
                viewModel.set(SettingsKeys.BUFFER_SECONDS_KEY, value as Int)
                dialog = null
            },
        )
        "reindex" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_rebuild_index)) },
            text = { Text(stringResource(R.string.confirm_rebuild_index)) },
            confirmButton = {
                TextButton(onClick = { viewModel.rebuildIndex(); dialog = null }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        "licenses" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.licenses_title)) },
            text = { Text(stringResource(R.string.licenses_body)) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(title) },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(title) },
            trailingContent = {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable(onClick = onClick),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, *>>,
    onDismiss: () -> Unit,
    onSelect: (Any?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    TextButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.BLACK -> R.string.settings_theme_black
}
