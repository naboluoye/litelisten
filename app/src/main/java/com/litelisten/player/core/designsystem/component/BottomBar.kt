package com.litelisten.player.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.litelisten.player.R
import com.litelisten.player.ui.navigation.Routes

enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    LIBRARY(
        Routes.LIBRARY, R.string.tab_library,
        Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic,
    ),
    SOURCES(
        Routes.SOURCES, R.string.tab_sources,
        Icons.Filled.Storage, Icons.Outlined.Storage,
    ),
    SEARCH(
        Routes.SEARCH, R.string.tab_search,
        Icons.Filled.Search, Icons.Outlined.Search,
    ),
    SETTINGS(
        Routes.SETTINGS, R.string.tab_settings,
        Icons.Filled.Settings, Icons.Outlined.Settings,
    ),
}

@Composable
fun LiteListenBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                alwaysShowLabel = true,
                icon = {
                    Icon(
                        imageVector = if (selected == destination) destination.selectedIcon
                        else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
