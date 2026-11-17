package com.litelisten.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.BuildConfig
import com.litelisten.player.core.ai.AiErrorCode
import com.litelisten.player.core.ai.AiIntroParser
import com.litelisten.player.core.ai.AiIntroState
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.common.TimeFormat
import com.litelisten.player.core.designsystem.component.TrackArtwork
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.AudioFormatInfo
import com.litelisten.player.core.model.PlaybackError
import com.litelisten.player.core.model.RepeatMode
import com.litelisten.player.ui.navigation.Routes

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aiState by viewModel.aiIntroState.collectAsStateWithLifecycle()
    val track = state.current
    var sliderValue by remember { mutableStateOf<Float?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.nav_close))
            }
            Spacer(Modifier.weight(1f))
            state.sleepTimerRemainingMs?.let { remaining ->
                Text(
                    stringResource(R.string.player_sleep_remaining, (remaining / 60_000L).toInt() + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(spacing.l))
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.l),
        ) {
            // 柔和底色托住封面，营造专辑舞台感（参考主流播放器）。
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(268.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
            )
            TrackArtwork(
                artworkKey = track?.artworkKey,
                sizeDp = 220,
                shape = MaterialTheme.shapes.large,
            )
        }
        Spacer(Modifier.height(spacing.xl))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: stringResource(R.string.player_not_playing),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.displayArtist ?: stringResource(R.string.player_unknown_artist),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                state.audioFormat?.let { info -> AudioQualityLine(info) }
            }
            IconButton(onClick = { track?.let { viewModel.toggleFavorite(it) } }, enabled = track != null) {
                Icon(
                    imageVector = if (track?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite),
                    tint = if (track?.isFavorite == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(spacing.l))
        Slider(
            value = sliderValue ?: progress,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                sliderValue?.let { viewModel.seek((it * state.durationMs).toLong()) }
                sliderValue = null
            },
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                TimeFormat.formatDuration(sliderValue?.let { (it * state.durationMs).toLong() } ?: state.positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                TimeFormat.formatDuration(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error?.let { error ->
            val message = when (error) {
                PlaybackError.UnsupportedFormat -> stringResource(R.string.player_error_unsupported)
                PlaybackError.FileMissing -> stringResource(R.string.player_error_missing)
                PlaybackError.NetworkLost -> stringResource(R.string.player_error_network)
                PlaybackError.AuthFailed -> stringResource(R.string.player_error_auth)
                PlaybackError.PlayerNotReady -> stringResource(R.string.player_error_not_ready)
                is PlaybackError.Other -> stringResource(R.string.player_error_other, error.message)
            }
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { track?.let { viewModel.play(it, listOf(it)) } }) {
                Text(stringResource(R.string.player_error_retry))
            }
        }

        Spacer(Modifier.height(spacing.l))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.shuffle(!state.shuffle) }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.player_shuffle),
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.previous() }, enabled = track != null) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_previous))
            }
            FilledIconButton(
                onClick = { viewModel.toggle() },
                enabled = track != null,
                modifier = Modifier.size(64.dp),
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.player_pause else R.string.player_play
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            IconButton(onClick = { viewModel.next() }, enabled = track != null) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next))
            }
            IconButton(onClick = {
                viewModel.repeat(
                    when (state.repeat) {
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                        RepeatMode.OFF -> RepeatMode.ALL
                    },
                )
            }) {
                Icon(
                    imageVector = if (state.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.player_repeat),
                    tint = if (state.repeat == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(spacing.m))
        Row(modifier = Modifier.fillMaxWidth()) {
            if (viewModel.aiEnabled) {
                PlayerAction(
                    icon = Icons.Filled.AutoAwesome,
                    label = stringResource(R.string.ai_button),
                    enabled = track != null,
                    onClick = { track?.let { viewModel.requestAiIntro(it) } },
                    modifier = Modifier.weight(1f),
                )
            }
            PlayerAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = stringResource(R.string.player_queue),
                onClick = { onNavigate(Routes.QUEUE) },
                modifier = Modifier.weight(1f),
            )
            PlayerAction(
                icon = Icons.Filled.Lyrics,
                label = stringResource(R.string.player_lyrics),
                onClick = { onNavigate(Routes.LYRICS) },
                modifier = Modifier.weight(1f),
            )
            PlayerAction(
                icon = Icons.Filled.GraphicEq,
                label = stringResource(R.string.player_equalizer),
                onClick = { onNavigate(Routes.EQUALIZER) },
                modifier = Modifier.weight(1f),
            )
            PlayerAction(
                icon = Icons.Filled.Timer,
                label = stringResource(R.string.player_sleep_timer),
                onClick = { dialog = "sleep" },
                modifier = Modifier.weight(1f),
            )
            PlayerAction(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.settings_playback_speed),
                onClick = { dialog = "more" },
                modifier = Modifier.weight(1f),
            )
        }
    }

    when (val ai = aiState) {
        AiIntroState.Loading -> AlertDialog(
            onDismissRequest = { viewModel.resetAiIntro() },
            title = { Text(stringResource(R.string.ai_intro_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.ai_intro_loading))
                }
            },
            confirmButton = {},
        )
        is AiIntroState.Success -> AlertDialog(
            onDismissRequest = { viewModel.resetAiIntro() },
            title = { Text(stringResource(R.string.ai_intro_title)) },
            text = { AiIntroContent(ai.sections) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAiIntro() }) {
                    Text(stringResource(R.string.ai_action_close))
                }
            },
        )
        is AiIntroState.Failure -> AlertDialog(
            onDismissRequest = { viewModel.resetAiIntro() },
            title = { Text(stringResource(R.string.ai_intro_title)) },
            text = {
                Text(
                    stringResource(
                        when (ai.code) {
                            AiErrorCode.AUTH -> R.string.ai_error_auth
                            AiErrorCode.RATE -> R.string.ai_error_rate
                            AiErrorCode.BAD_REQUEST -> R.string.ai_error_bad_request
                            AiErrorCode.SERVER -> R.string.ai_error_server
                            AiErrorCode.EMPTY -> R.string.ai_error_empty
                            AiErrorCode.DISABLED -> R.string.ai_error_disabled
                            AiErrorCode.NETWORK -> R.string.ai_error_network
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { track?.let { viewModel.requestAiIntro(it) } }) {
                    Text(stringResource(R.string.ai_action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetAiIntro() }) {
                    Text(stringResource(R.string.ai_action_close))
                }
            },
        )
        else -> Unit
    }

    when (dialog) {
        "sleep" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.player_sleep_title)) },
            text = {
                Column {
                    listOf(
                        15 to R.string.player_sleep_15,
                        30 to R.string.player_sleep_30,
                        45 to R.string.player_sleep_45,
                        60 to R.string.player_sleep_60,
                        90 to R.string.player_sleep_90,
                    ).forEach { (minutes, label) ->
                        TextButton(onClick = { viewModel.sleep(minutes); dialog = null }) {
                            Text(stringResource(label))
                        }
                    }
                    TextButton(onClick = { viewModel.sleep(null); dialog = null }) {
                        Text(stringResource(R.string.player_sleep_cancel))
                    }
                }
            },
            confirmButton = {},
        )
        "more" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.nav_more)) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(onClick = { viewModel.speed(speed); dialog = null }) {
                            Text(stringResource(R.string.player_speed_value, speed))
                        }
                    }
                    track?.let {
                        Text(
                            stringResource(R.string.player_track_info_path, it.path),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start,
                        )
                        Text(
                            stringResource(
                                R.string.player_track_info_duration,
                                TimeFormat.formatDuration(it.durationMs),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/** 播放页功能入口：图标 + 小字，均分宽度，让每个功能一眼可辨。 */
@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            maxLines = 1,
        )
    }
}

/** AI 三段介绍内容；模型没按格式分段时整段展示原文。 */
@Composable
private fun AiIntroContent(sections: AiIntroParser.Sections) {
    if (!sections.isStructured) {
        Text(
            sections.raw,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
        return
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        listOf(
            stringResource(R.string.ai_section_header_background) to sections.background,
            stringResource(R.string.ai_section_header_artist) to sections.artist,
            stringResource(R.string.ai_section_header_lyrics) to sections.lyricsIntro,
        ).forEach { (header, body) ->
            Text(
                header,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/** 播放页音质行：显示解码器实际读到的规格，而不是靠文件名猜。 */
@Composable
private fun AudioQualityLine(info: AudioFormatInfo) {
    val label = MimeTypes.shortLabel(info.mimeType)
        ?: info.mimeType?.substringAfter('/')?.uppercase()
        ?: stringResource(R.string.player_quality_unknown)
    val rate = info.sampleRateHz?.takeIf { it > 0 }
    val depth = info.bitDepth?.takeIf { it > 0 }
    val channels = info.channelCount?.takeIf { it > 0 }
    val spec = when {
        rate != null && depth != null && channels != null ->
            stringResource(R.string.player_quality_spec_full, label, rate / 1000f, depth, channels)
        rate != null && channels != null ->
            stringResource(R.string.player_quality_spec_basic, label, rate / 1000f, channels)
        else -> label
    }
    val hiresText = stringResource(R.string.player_quality_hires)
    val losslessText = stringResource(R.string.player_quality_lossless)
    val floatText = stringResource(R.string.player_quality_float)
    val tag = listOfNotNull(
        when {
            MimeTypes.isHiRes(rate, depth) -> hiresText
            MimeTypes.isLossless(info.mimeType) -> losslessText
            else -> null
        },
        floatText.takeIf { info.floatOutput },
    ).joinToString(" · ")
    Column(modifier = Modifier.padding(top = 2.dp)) {
        Text(
            text = spec,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        if (tag.isNotBlank()) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
