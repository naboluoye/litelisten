package com.litelisten.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.ui.player.PlayerViewModel

@Composable
fun TrackRowPlaceholder(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** 常驻 MiniPlayer：显示真实播放状态，点击进播放页。 */
@Composable
fun MiniPlayer(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track = state.current
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s)
            .height(64.dp)
            .clickable(onClickLabel = stringResource(R.string.cd_mini_player), onClick = onClick),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.m),
            ) {
                if (track == null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = stringResource(R.string.cd_cover),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    TrackArtwork(track.artworkKey, sizeDp = 44)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.m),
                ) {
                    Text(
                        text = track?.title ?: stringResource(R.string.player_not_playing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.displayArtist ?: stringResource(R.string.player_unknown_artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { viewModel.toggle() }, enabled = track != null) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.player_pause else R.string.player_play
                        ),
                    )
                }
                IconButton(onClick = { viewModel.next() }, enabled = track != null) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next))
                }
            }
            if (track != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }
}
