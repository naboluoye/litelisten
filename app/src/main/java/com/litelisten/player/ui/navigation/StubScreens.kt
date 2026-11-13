package com.litelisten.player.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.LiteListenTopBar

/** 子路由统一占位：保证每个页面都能返回，不出现导航死角。 */
@Composable
fun StubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.statusBarsPadding()) {
        LiteListenTopBar(title = title, onNavigateUp = onBack)
        Text(
            text = stringResource(R.string.stub_screen_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier,
        )
    }
}
