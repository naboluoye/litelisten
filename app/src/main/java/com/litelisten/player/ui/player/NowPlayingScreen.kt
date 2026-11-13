package com.litelisten.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.theme.LocalSpacing

/** P0 最小播放页：一屏一件事，控件全部禁用态，返回不停止播放。 */
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.nav_close))
        }
        Spacer(Modifier.weight(0.6f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = stringResource(R.string.cd_cover),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.weight(0.4f))
        Text(
            text = stringResource(R.string.player_not_playing),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.player_unknown_artist),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(value = 0f, onValueChange = {}, enabled = false, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("00:00", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.l),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}, enabled = false) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_previous))
            }
            FilledIconButton(onClick = {}, enabled = false, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.player_pause))
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.l),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val disabled = listOf(
                Icons.AutoMirrored.Filled.List to R.string.player_queue,
                Icons.Filled.GraphicEq to R.string.player_lyrics,
                Icons.Filled.GraphicEq to R.string.player_equalizer,
                Icons.Filled.Timer to R.string.player_sleep_timer,
                Icons.Filled.MoreHoriz to R.string.player_more,
            )
            disabled.forEach { (icon, label) ->
                IconButton(onClick = {}, enabled = false) {
                    Icon(icon, contentDescription = stringResource(label))
                }
            }
        }
    }
}
