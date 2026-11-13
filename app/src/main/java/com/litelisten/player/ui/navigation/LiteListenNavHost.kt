package com.litelisten.player.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.litelisten.player.ui.browse.AddSourceScreen
import com.litelisten.player.ui.browse.FileBrowserScreen
import com.litelisten.player.ui.browse.SourcesScreen
import com.litelisten.player.ui.library.AlbumDetailScreen
import com.litelisten.player.ui.library.ArtistDetailScreen
import com.litelisten.player.ui.library.FolderDetailScreen
import com.litelisten.player.ui.library.LibraryScreen
import com.litelisten.player.ui.player.EqualizerScreen
import com.litelisten.player.ui.player.LyricsScreen
import com.litelisten.player.ui.player.NowPlayingScreen
import com.litelisten.player.ui.player.QueueScreen
import com.litelisten.player.ui.search.SearchScreen
import com.litelisten.player.ui.settings.SettingsScreen

@Composable
fun LiteListenNavHost(
    navController: NavHostController,
    onMessage: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(180)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) },
        popEnterTransition = { fadeIn(animationSpec = tween(180)) },
        popExitTransition = { fadeOut(animationSpec = tween(180)) },
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(onMessage = onMessage, onNavigate = onNavigate)
        }
        composable(Routes.SOURCES) {
            SourcesScreen(onMessage = onMessage, onNavigate = onNavigate)
        }
        composable(Routes.SEARCH) {
            SearchScreen(onMessage = onMessage, onNavigate = onNavigate)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onMessage = onMessage, onNavigate = onNavigate)
        }
        composable(
            route = Routes.NOW_PLAYING,
            enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
            exitTransition = { slideOutVertically(animationSpec = tween(320)) { it } },
        ) {
            NowPlayingScreen(onBack = onBack, onNavigate = onNavigate, onMessage = onMessage)
        }
        composable(Routes.ALBUM) { entry ->
            AlbumDetailScreen(
                name = entry.arguments?.getString("key").orEmpty().substringBefore("|"),
                onBack = onBack,
                onMessage = onMessage,
            )
        }
        composable(Routes.ARTIST) { entry ->
            ArtistDetailScreen(
                name = entry.arguments?.getString("key").orEmpty(),
                onBack = onBack,
                onMessage = onMessage,
            )
        }
        composable(Routes.FOLDER) { entry ->
            FolderDetailScreen(
                name = entry.arguments?.getString("key").orEmpty(),
                onBack = onBack,
                onMessage = onMessage,
            )
        }
        composable(
            route = Routes.BROWSER,
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            FileBrowserScreen(onBack = onBack, onMessage = onMessage)
        }
        composable(
            route = Routes.ADD_SMB,
            arguments = listOf(
                navArgument("host") { type = NavType.StringType; defaultValue = "" },
                navArgument("port") { type = NavType.IntType; defaultValue = 445 },
            ),
        ) { entry ->
            AddSourceScreen(
                type = com.litelisten.player.core.model.SourceType.SMB,
                onBack = onBack,
                onSaved = { id -> onNavigate(Routes.browser(id)) },
                initialHost = entry.arguments?.getString("host").orEmpty(),
                initialPort = entry.arguments?.getInt("port") ?: 445,
            )
        }
        composable(
            route = Routes.ADD_WEBDAV,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            AddSourceScreen(
                type = com.litelisten.player.core.model.SourceType.WEBDAV,
                onBack = onBack,
                onSaved = { id -> onNavigate(Routes.browser(id)) },
                initialUrl = entry.arguments?.getString("url").orEmpty(),
            )
        }
        composable(
            route = Routes.ADD_JELLYFIN,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            AddSourceScreen(
                type = com.litelisten.player.core.model.SourceType.JELLYFIN,
                onBack = onBack,
                onSaved = { id -> onNavigate(Routes.browser(id)) },
                initialUrl = entry.arguments?.getString("url").orEmpty(),
            )
        }
        composable(Routes.QUEUE) { QueueScreen(onBack = onBack, onMessage = onMessage) }
        composable(Routes.LYRICS) { LyricsScreen(onBack = onBack, onMessage = onMessage) }
        composable(Routes.EQUALIZER) { EqualizerScreen(onBack = onBack) }
    }
}
