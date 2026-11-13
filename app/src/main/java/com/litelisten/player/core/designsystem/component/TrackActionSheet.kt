package com.litelisten.player.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = spacing.l),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
                maxLines = 1,
            )
            ActionItem(R.string.action_play_next, onDismiss, onPlayNext)
            ActionItem(R.string.action_add_to_queue, onDismiss, onAddToQueue)
            ActionItem(
                if (track.isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                onDismiss, onFavorite,
            )
            ActionItem(R.string.action_track_info, onDismiss, onInfo)
            ActionItem(R.string.action_remove_from_library, onDismiss, onRemove)
        }
    }
}

@Composable
private fun ActionItem(labelRes: Int, onDismiss: () -> Unit, action: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        modifier = Modifier.clickable {
            onDismiss()
            action()
        },
    )
}
