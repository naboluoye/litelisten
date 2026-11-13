package com.litelisten.player.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.litelisten.player.R
import com.litelisten.player.ui.browse.SourcesScreen
import com.litelisten.player.ui.library.LibraryScreen
import com.litelisten.player.ui.player.NowPlayingScreen
import com.litelisten.player.ui.search.SearchScreen
import com.litelisten.player.ui.settings.SettingsScreen

@Composable
fun LiteListenNavHost(
    navController: NavHostController,
    onMessage: (String) -> Unit,
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
            LibraryScreen(onMessage = onMessage)
        }
        composable(Routes.SOURCES) {
            SourcesScreen(onMessage = onMessage)
        }
        composable(Routes.SEARCH) {
            SearchScreen(onMessage = onMessage)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onMessage = onMessage)
        }
        composable(
            route = Routes.NOW_PLAYING,
            enterTransition = { slideInVertically(animationSpec = tween(320)) { it } },
            exitTransition = { slideOutVertically(animationSpec = tween(320)) { it } },
        ) {
            NowPlayingScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ALBUM) { StubScreen(stringResource(R.string.stub_album), { navController.popBackStack() }) }
        composable(Routes.ARTIST) { StubScreen(stringResource(R.string.stub_artist), { navController.popBackStack() }) }
        composable(Routes.BROWSER) { StubScreen(stringResource(R.string.stub_browser), { navController.popBackStack() }) }
        composable(Routes.ADD_SMB) { StubScreen(stringResource(R.string.stub_add_smb), { navController.popBackStack() }) }
        composable(Routes.ADD_WEBDAV) { StubScreen(stringResource(R.string.stub_add_webdav), { navController.popBackStack() }) }
        composable(Routes.QUEUE) { StubScreen(stringResource(R.string.stub_queue), { navController.popBackStack() }) }
        composable(Routes.LYRICS) { StubScreen(stringResource(R.string.stub_lyrics), { navController.popBackStack() }) }
        composable(Routes.EQUALIZER) { StubScreen(stringResource(R.string.stub_equalizer), { navController.popBackStack() }) }
    }
}
