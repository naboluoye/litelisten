package com.litelisten.player.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.SectionHeader
import com.litelisten.player.core.designsystem.theme.LocalSpacing

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val supported by viewModel.supported.collectAsStateWithLifecycle()
    val bands by viewModel.bands.collectAsStateWithLifecycle()
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val bass by viewModel.bass.collectAsStateWithLifecycle()
    val bandRange by viewModel.bandRange.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.init(sessionId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        LiteListenTopBar(title = stringResource(R.string.equalizer_title), onNavigateUp = onBack)
        if (!supported) {
            Text(
                stringResource(R.string.equalizer_unsupported),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(spacing.l),
            )
            return@Column
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l),
        ) {
            Text(stringResource(R.string.equalizer_enable), modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) })
        }
        SectionHeader(title = stringResource(R.string.equalizer_preset))
        Row(modifier = Modifier.padding(horizontal = spacing.l)) {
            presets.take(6).forEachIndexed { index, name ->
                AssistChip(
                    onClick = { viewModel.setPreset(index); viewModel.setEnabled(true) },
                    label = { Text(name) },
                    modifier = Modifier.padding(end = spacing.s),
                )
            }
        }
        SectionHeader(title = stringResource(R.string.equalizer_title))
        bands.forEachIndexed { index, band ->
            val level = levels.getOrNull(index) ?: 0
            Column(modifier = Modifier.padding(horizontal = spacing.l)) {
                Text(
                    stringResource(R.string.equalizer_band_label, band.first, level / 100f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = level.toFloat().coerceIn(bandRange.first.toFloat(), bandRange.last.toFloat()),
                    valueRange = bandRange.first.toFloat()..bandRange.last.toFloat(),
                    onValueChange = { viewModel.setBand(index, it.toInt()) },
                    enabled = enabled,
                )
            }
        }
        SectionHeader(title = stringResource(R.string.equalizer_bass))
        Slider(
            value = bass.toFloat(),
            valueRange = 0f..1000f,
            onValueChange = { viewModel.setBass(it.toInt()) },
            enabled = enabled,
            modifier = Modifier.padding(horizontal = spacing.l),
        )
        Text(
            stringResource(R.string.equalizer_bluetooth_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(spacing.l),
        )
    }
}
