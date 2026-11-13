package com.litelisten.player.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.EmptyState
import com.litelisten.player.core.designsystem.component.SectionHeader
import com.litelisten.player.core.designsystem.theme.LocalSpacing

@Composable
fun SearchScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val spacing = LocalSpacing.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.l),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(top = spacing.s),
        )
        SectionHeader(title = stringResource(R.string.search_recent))
        EmptyState(
            icon = Icons.Filled.History,
            title = stringResource(R.string.search_empty_title),
            description = stringResource(R.string.search_empty_desc),
        )
        if (query.isNotBlank()) {
            Text(
                text = stringResource(R.string.snack_p1_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(spacing.l),
            )
            LaunchedEffect(query) { onMessage(query) }
        }
    }
}
