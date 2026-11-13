package com.litelisten.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.SourceType

@Composable
fun SourceBadge(type: SourceType, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (type) {
            SourceType.LOCAL -> R.string.badge_local
            SourceType.SAF -> R.string.badge_saf
            SourceType.SMB -> R.string.badge_smb
            SourceType.WEBDAV -> R.string.badge_webdav
            SourceType.DLNA -> R.string.badge_dlna
            SourceType.JELLYFIN -> R.string.badge_jellyfin
        }
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = LocalSpacing.current.s, vertical = LocalSpacing.current.xs),
    )
}
