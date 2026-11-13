package com.litelisten.player.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.litelisten.player.core.designsystem.component.LiteListenBottomBar
import com.litelisten.player.core.designsystem.component.MiniPlayer
import com.litelisten.player.core.designsystem.component.TopLevelDestination
import kotlinx.coroutines.launch

@Composable
fun LiteListenRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute in Routes.TOP_LEVEL
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showChrome) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    MiniPlayer(onClick = { navController.navigate(Routes.NOW_PLAYING) })
                    LiteListenBottomBar(
                        selected = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
                            ?: TopLevelDestination.LIBRARY,
                        onSelect = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(Routes.LIBRARY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            LiteListenNavHost(
                navController = navController,
                onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
