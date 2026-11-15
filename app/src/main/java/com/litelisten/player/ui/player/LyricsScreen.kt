package com.litelisten.player.ui.player

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.ai.AiErrorCode
import com.litelisten.player.core.ai.AiLyricsState
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.component.TrackArtwork
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.player.LoadedLyrics
import com.litelisten.player.core.player.LyricLine
import com.litelisten.player.core.player.LyricsSource
import kotlinx.coroutines.delay

/**
 * 歌词页：逐字染色 + 跟唱模式（单句循环 / 慢速 / 偏移微调）。
 * 进度按 60ms 采样，普通歌词也按行时长做渐进染色，整句变色会显得迟滞。
 */
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val state by playerViewModel.state.collectAsStateWithLifecycle()
    val track = state.current
    val manual by lyricsViewModel.manual.collectAsStateWithLifecycle()
    val offsetMs by lyricsViewModel.offsetMs.collectAsState(initial = 0)
    val singAlong by lyricsViewModel.singAlong.collectAsState(initial = false)
    val loopLineEnabled by lyricsViewModel.loopLine.collectAsState(initial = true)
    val aiState by playerViewModel.aiLyricsState.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(LoadedLyrics.EMPTY) }
    LaunchedEffect(track?.id, manual) { loaded = lyricsViewModel.load(track) }

    val lines = loaded.lines
    val hasManual = track?.id?.let { manual[it] }?.isNotEmpty() == true

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(track?.id) {
        while (true) {
            positionMs = playerViewModel.controller.currentPositionMs()
            delay(if (playerViewModel.state.value.isPlaying) 60L else 240L)
        }
    }
    val effectivePosition = positionMs - offsetMs
    val currentIndex = remember(lines, effectivePosition) {
        if (lines.isEmpty()) 0 else lines.indexOfLast { it.timeMs <= effectivePosition }.coerceAtLeast(0)
    }

    var loopIndex by remember { mutableIntStateOf(-1) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(singAlong) { if (!singAlong) loopIndex = -1 }

    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex, lines.size) {
        if (lines.isNotEmpty()) runCatching { listState.animateScrollToItem(currentIndex) }
    }

    // 单句循环：唱到行尾就跳回行首。用常驻协程监听进度，不做每帧重启。
    LaunchedEffect(singAlong, loopLineEnabled, loopIndex, offsetMs, track?.id, lines) {
        if (!singAlong || !loopLineEnabled || loopIndex < 0) return@LaunchedEffect
        val line = lines.getOrNull(loopIndex) ?: return@LaunchedEffect
        val start = (line.timeMs + offsetMs).coerceAtLeast(0L)
        val end = line.effectiveEndMs + offsetMs
        snapshotFlow { positionMs }.collect { pos ->
            if (pos - offsetMs >= end) {
                val now = SystemClock.elapsedRealtime()
                // 跳转后进度更新有延迟，加一道时间闸门避免连续 seek。
                if (now - lastSeekAt > 700L) {
                    lastSeekAt = now
                    playerViewModel.seek(start)
                }
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = track ?: return@rememberLauncherForActivityResult
        lyricsViewModel.attach(current.id, uri) { ok ->
            if (!ok) onMessage(context.getString(R.string.lyrics_pick_failed))
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        lyricsViewModel.importFolder(uri) { result ->
            onMessage(
                if (result.matched > 0) {
                    context.getString(R.string.lyrics_import_result, result.matched, result.scanned)
                } else {
                    context.getString(R.string.lyrics_import_empty)
                },
            )
        }
    }

    var showPaste by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LiteListenTopBar(
            title = stringResource(R.string.player_lyrics),
            onNavigateUp = onBack,
        ) {
            if (track != null) {
                TextButton(onClick = { lyricsViewModel.setSingAlong(!singAlong) }) {
                    Text(
                        text = stringResource(R.string.lyrics_sing_along),
                        color = if (singAlong) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (singAlong) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (playerViewModel.aiEnabled) {
                    IconButton(
                        onClick = {
                            // 没有歌词原文也能问，AI 会退化成讲大意，明确不让它编句子。
                            playerViewModel.requestLyricsExplanation(
                                track,
                                lines.joinToString("\n") { it.text },
                            )
                        },
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = stringResource(R.string.ai_lyrics_button),
                        )
                    }
                }
            }
        }

        if (lines.isEmpty()) {
            EmptyLyrics(
                artworkKey = track?.artworkKey,
                hasManual = hasManual,
                onPickFile = { pickFile.launch(arrayOf("*/*")) },
                onPickFolder = { pickFolder.launch(null) },
                onPaste = { showPaste = true },
                onRemoveManual = { track?.id?.let { lyricsViewModel.removeManual(it) } },
            )
        } else {
            Text(
                text = sourceLabel(loaded.source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.xs),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = spacing.l,
                    vertical = spacing.m,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                itemsIndexed(lines, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
                    val isCurrent = index == currentIndex
                    val isLooping = singAlong && index == loopIndex
                    Text(
                        text = styledLyric(line, isCurrent, effectivePosition),
                        style = if (isCurrent) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isLooping) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable {
                                if (singAlong) loopIndex = index
                                playerViewModel.seek((line.timeMs + offsetMs).coerceAtLeast(0L))
                            }
                            .padding(horizontal = spacing.xs, vertical = spacing.s),
                    )
                }
            }

            if (singAlong) {
                SingAlongBar(
                    offsetMs = offsetMs,
                    loopEnabled = loopLineEnabled,
                    speed = state.speed,
                    onToggleLoop = { lyricsViewModel.setLoopLine(!loopLineEnabled) },
                    onSpeed = { playerViewModel.speed(it) },
                    onNudge = { lyricsViewModel.nudgeOffset(it) },
                    onResetOffset = { lyricsViewModel.resetOffset() },
                )
            }
        }
    }

    if (showPaste) {
        PasteLyricsDialog(
            onDismiss = { showPaste = false },
            onConfirm = { text ->
                showPaste = false
                val current = track
                if (current != null) {
                    lyricsViewModel.attachText(current.id, text) { ok ->
                        if (!ok) onMessage(context.getString(R.string.lyrics_paste_failed))
                    }
                }
            },
        )
    }

    LyricsAiDialog(state = aiState, onDismiss = { playerViewModel.resetLyricsExplanation() })
}

@Composable
private fun sourceLabel(source: LyricsSource): String = when (source) {
    LyricsSource.MANUAL -> stringResource(R.string.lyrics_source_manual)
    LyricsSource.SIDE_CAR -> stringResource(R.string.lyrics_source_side_car)
    LyricsSource.SHARED_SIDE_CAR -> stringResource(R.string.lyrics_source_shared)
    LyricsSource.EMBEDDED -> stringResource(R.string.lyrics_source_embedded)
    LyricsSource.NONE -> ""
}

/**
 * 染色规则：已唱过的字用主色，正在唱的那个字加粗，还没唱的字淡化。
 * 普通 LRC 没有逐字时间轴，按行时长与字符数估算进度，也能得到渐进效果。
 */
@Composable
private fun styledLyric(line: LyricLine, isCurrent: Boolean, positionMs: Long): AnnotatedString {
    val sung = MaterialTheme.colorScheme.primary
    val pending = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    if (!isCurrent) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = pending)) { append(line.text) }
        }
    }
    val words = line.words
    if (words.isNotEmpty()) {
        return buildAnnotatedString {
            words.forEach { word ->
                val done = positionMs >= word.startMs
                val active = done && positionMs < word.endMs
                withStyle(
                    SpanStyle(
                        color = if (done) sung else pending,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    ),
                ) {
                    append(word.text)
                }
            }
        }
    }
    val start = line.timeMs
    val end = line.effectiveEndMs
    val text = line.text
    if (end <= start || text.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = sung)) { append(text) }
        }
    }
    val ratio = ((positionMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    val cut = (text.length * ratio).toInt().coerceIn(0, text.length)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = sung)) { append(text.substring(0, cut)) }
        withStyle(SpanStyle(color = pending)) { append(text.substring(cut)) }
    }
}

@Composable
private fun EmptyLyrics(
    artworkKey: String?,
    hasManual: Boolean,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
    onPaste: () -> Unit,
    onRemoveManual: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackArtwork(artworkKey, sizeDp = 120)
        Text(
            stringResource(R.string.lyrics_none),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = spacing.l),
        )
        Text(
            stringResource(R.string.lyrics_none_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.s),
        )
        OutlinedButton(onClick = onPickFile, modifier = Modifier.padding(top = spacing.l)) {
            Text(stringResource(R.string.lyrics_pick))
        }
        Row(
            modifier = Modifier.padding(top = spacing.s),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            OutlinedButton(onClick = onPickFolder) {
                Text(stringResource(R.string.lyrics_import_folder))
            }
            OutlinedButton(onClick = onPaste) {
                Text(stringResource(R.string.lyrics_paste))
            }
        }
        if (hasManual) {
            TextButton(onClick = onRemoveManual, modifier = Modifier.padding(top = spacing.xs)) {
                Text(stringResource(R.string.lyrics_remove_manual))
            }
        }
    }
}

@Composable
private fun SingAlongBar(
    offsetMs: Int,
    loopEnabled: Boolean,
    speed: Float,
    onToggleLoop: () -> Unit,
    onSpeed: (Float) -> Unit,
    onNudge: (Int) -> Unit,
    onResetOffset: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = spacing.m, vertical = spacing.s),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            FilterChip(
                selected = loopEnabled,
                onClick = onToggleLoop,
                label = { Text(stringResource(R.string.lyrics_loop_line)) },
            )
            Spacer(Modifier.width(spacing.xs))
            SpeedChip(stringResource(R.string.lyrics_speed_slow), speed, 0.75f, onSpeed)
            SpeedChip(stringResource(R.string.lyrics_speed_mid), speed, 0.9f, onSpeed)
            SpeedChip(stringResource(R.string.lyrics_speed_normal), speed, 1f, onSpeed)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            TextButton(onClick = { onNudge(-100) }) {
                Text(stringResource(R.string.lyrics_offset_minus))
            }
            Text(
                text = stringResource(R.string.lyrics_offset_value, offsetMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onNudge(100) }) {
                Text(stringResource(R.string.lyrics_offset_plus))
            }
            if (offsetMs != 0) {
                TextButton(onClick = onResetOffset) {
                    Text(stringResource(R.string.lyrics_offset_reset))
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(label: String, current: Float, target: Float, onSpeed: (Float) -> Unit) {
    FilterChip(
        selected = kotlin.math.abs(current - target) < 0.01f,
        onClick = { onSpeed(target) },
        label = { Text(label) },
    )
}

@Composable
private fun PasteLyricsDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_paste)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.lyrics_paste_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(top = 12.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text(stringResource(R.string.lyrics_paste_placeholder)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.lyrics_paste_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.lyrics_paste_cancel))
            }
        },
    )
}

@Composable
private fun LyricsAiDialog(state: AiLyricsState, onDismiss: () -> Unit) {
    if (state is AiLyricsState.Idle) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_lyrics_title)) },
        text = {
            when (state) {
                is AiLyricsState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.ai_intro_loading))
                }
                is AiLyricsState.Success -> Text(state.text)
                is AiLyricsState.Failure -> Text(stringResource(aiErrorRes(state.code)))
                else -> Unit
            }
        },
        confirmButton = {
            if (state !is AiLyricsState.Loading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ai_action_close))
                }
            }
        },
    )
}

private fun aiErrorRes(code: AiErrorCode): Int = when (code) {
    AiErrorCode.AUTH -> R.string.ai_error_auth
    AiErrorCode.RATE -> R.string.ai_error_rate
    AiErrorCode.BAD_REQUEST -> R.string.ai_error_bad_request
    AiErrorCode.SERVER -> R.string.ai_error_server
    AiErrorCode.EMPTY -> R.string.ai_error_empty
    AiErrorCode.NETWORK -> R.string.ai_error_network
    AiErrorCode.DISABLED -> R.string.ai_error_disabled
}
