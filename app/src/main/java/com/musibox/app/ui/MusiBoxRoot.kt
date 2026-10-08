package com.musibox.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.LocalSnackbar
import com.musibox.app.ui.download.DownloadScreen
import com.musibox.app.ui.history.HistoryScreen
import com.musibox.app.ui.home.HomeScreen
import com.musibox.app.ui.library.LibraryScreen
import com.musibox.app.ui.library.PlaylistScreen
import com.musibox.app.ui.library.SearchScreen
import com.musibox.app.ui.library.SongListScreen
import com.musibox.app.ui.more.MoreScreen
import com.musibox.app.ui.navigation.BottomBar
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.navigation.navigateTopLevel
import com.musibox.app.ui.onboarding.OnboardingScreen
import com.musibox.app.ui.player.MiniPlayer
import com.musibox.app.ui.player.PlayerScreen
import com.musibox.app.ui.player.QueueScreen
import com.musibox.app.ui.settings.AccountScreen
import com.musibox.app.ui.settings.SettingsScreen
import com.musibox.app.ui.settings.StorageScreen
import com.musibox.app.ui.settings.SyncResolutionHost
import com.musibox.app.ui.vault.VaultBackupScreen
import com.musibox.app.ui.vault.VaultChangePinScreen
import com.musibox.app.ui.vault.VaultPlayerScreen
import com.musibox.app.ui.vault.VaultScreen
import com.musibox.app.ui.vault.VaultSettingsScreen
import com.musibox.app.ui.vault.VaultViewerScreen
import kotlinx.coroutines.flow.Flow

@Composable
fun MusiBoxRoot(startOnboarding: Boolean, navRequests: Flow<String>) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val showBottom = route in Routes.topLevel
    val hideMiniOn = setOf(Routes.PLAYER, Routes.QUEUE, Routes.ONBOARDING, Routes.VAULT_PLAYER, Routes.VAULT_VIEWER)
    val showMini = playerState.current != null && route != null && route !in hideMiniOn

    LaunchedEffect(navRequests) {
        navRequests.collect { target ->
            if (navController.currentDestination?.route == Routes.ONBOARDING) return@collect
            if (target.startsWith("download") || target == Routes.VAULT || target == Routes.HOME) {
                navController.navigateTopLevel(target, restore = false)
            } else {
                navController.navigate(target) { launchSingleTop = true }
            }
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(showMini, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                        MiniPlayer(
                            state = playerState,
                            onOpen = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                            onPlayPause = { container.player.togglePlayPause() },
                            onNext = { container.player.next() },
                        )
                    }
                    if (showBottom) {
                        BottomBar(route) { navController.navigateTopLevel(it) }
                    } else if (route != Routes.PLAYER && route != Routes.VAULT_PLAYER && route != Routes.ONBOARDING) {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.HOME,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onFinished = {
                        navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.HOME) { HomeScreen(navController) }
                composable(
                    Routes.LIBRARY,
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry -> LibraryScreen(navController, entry.arguments?.getInt("tab") ?: 0) }
                composable(
                    Routes.DOWNLOAD,
                    arguments = listOf(
                        navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    DownloadScreen(navController, entry.arguments?.getInt("tab") ?: 0, entry.arguments?.getString("url"))
                }
                composable(Routes.VAULT) { VaultScreen(navController) }
                composable(Routes.MORE) { MoreScreen(navController) }
                composable(
                    Routes.PLAYER,
                    enterTransition = { slideInVertically(tween(300)) { it } },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { slideOutVertically(tween(260)) { it } },
                ) { PlayerScreen(navController) }
                composable(Routes.QUEUE) { QueueScreen(navController) }
                composable(Routes.SEARCH) { SearchScreen(navController) }
                composable(Routes.SETTINGS) { SettingsScreen(navController) }
                composable(Routes.ACCOUNT) { AccountScreen(navController) }
                composable(Routes.STORAGE) { StorageScreen(navController) }
                composable(
                    Routes.HISTORY,
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry -> HistoryScreen(navController, entry.arguments?.getInt("tab") ?: 0) }
                composable(Routes.SONGS) { entry ->
                    SongListScreen(
                        navController,
                        entry.arguments?.getString("type").orEmpty(),
                        entry.arguments?.getString("value").orEmpty(),
                    )
                }
                composable(Routes.PLAYLIST) { entry -> PlaylistScreen(navController, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.VAULT_SETTINGS) { VaultSettingsScreen(navController) }
                composable(Routes.VAULT_VIEWER) { entry -> VaultViewerScreen(navController, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.VAULT_PLAYER) { entry -> VaultPlayerScreen(navController, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.VAULT_BACKUP) { VaultBackupScreen(navController) }
                composable(Routes.VAULT_CHANGE_PIN) { VaultChangePinScreen(navController) }
            }
        }
        SyncResolutionHost()
    }
}
