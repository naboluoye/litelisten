package com.litelisten.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.SectionHeader
import com.litelisten.player.core.model.ThemeMode

@Composable
fun SettingsScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    val pending = stringResource(R.string.snack_p5_pending)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        LiteListenTopBar(title = stringResource(R.string.settings_title))

        SectionHeader(title = stringResource(R.string.settings_group_appearance))
        SettingsRow(
            title = stringResource(R.string.settings_theme),
            value = stringResource(themeMode.labelRes()),
            onClick = { showThemeDialog = true },
        )
        SettingsRow(
            title = stringResource(R.string.settings_dynamic_color),
            value = stringResource(R.string.settings_value_default),
            onClick = { onMessage(pending) },
        )

        SectionHeader(title = stringResource(R.string.settings_group_playback))
        SettingsRow(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_value_default)) { onMessage(pending) }

        SectionHeader(title = stringResource(R.string.settings_group_audio))
        SettingsRow(stringResource(R.string.settings_group_audio), stringResource(R.string.settings_value_default)) { onMessage(pending) }

        SectionHeader(title = stringResource(R.string.settings_group_library))
        SettingsRow(stringResource(R.string.settings_group_library), stringResource(R.string.settings_value_default)) { onMessage(pending) }

        SectionHeader(title = stringResource(R.string.settings_group_network))
        SettingsRow(stringResource(R.string.settings_group_network), stringResource(R.string.settings_value_default)) { onMessage(pending) }

        SectionHeader(title = stringResource(R.string.settings_group_about))
        SettingsRow(stringResource(R.string.app_name), "v1.0.0") { onMessage(pending) }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme_dialog_title)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        ListItem(
                            headlineContent = { Text(stringResource(mode.labelRes())) },
                            leadingContent = {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode); showThemeDialog = false },
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.setThemeMode(mode)
                                showThemeDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(title) },
            trailingContent = {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable(onClick = onClick),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.BLACK -> R.string.settings_theme_black
}
