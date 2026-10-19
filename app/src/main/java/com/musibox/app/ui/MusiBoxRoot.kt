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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import com.musibox.app.ui.account.LoginContent
import com.musibox.app.ui.components.SpaceBackdrop
import com.musibox.app.ui.browser.BrowserScreen
import com.musibox.app.ui.browser.BrowserSetupScreen
import com.musibox.app.ui.themes.WallpaperPreviewScreen
import com.musibox.app.ui.themes.WallpaperStoreScreen
import com.musibox.app.ui.history.HistoryScreen
import com.musibox.app.ui.library.LibraryScreen
import com.musibox.app.ui.library.PlaylistScreen
import com.musibox.app.ui.library.SearchScreen
import com.musibox.app.ui.library.SongListScreen
import com.musibox.app.ui.navigation.BottomBar
import com.musibox.app.ui.main.MainScreen
import com.musibox.app.ui.main.MainTabs
import androidx.compose.foundation.pager.rememberPagerState
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
import com.musibox.app.ui.vault.VaultSettingsScreen
import com.musibox.app.ui.vault.VaultRecoverScreen
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

    val showBottom = route == Routes.MAIN
    val pagerState = rememberPagerState { MainTabs.COUNT }
    val rootScope = rememberCoroutineScope()
    val hideMiniOn = setOf(
        Routes.PRO, Routes.PICK_SONGS, Routes.PLAYER, Routes.QUEUE, Routes.ONBOARDING, Routes.VAULT_PLAYER, Routes.VAULT_VIEWER,
        Routes.BROWSER, Routes.LOGIN, Routes.BROWSER_SETUP, Routes.WALLPAPER,
    )
    val fullScreen = route == Routes.PLAYER || route == Routes.VAULT_PLAYER || route == Routes.ONBOARDING ||
        route == Routes.BROWSER_SETUP || route == Routes.WALLPAPER || route == Routes.BROWSER
    val showMini = playerState.current != null && route != null && route !in hideMiniOn

    // Quem ainda não entrou na conta (e não escolheu "Continuar sem conta") vê a tela de entrada uma vez.
    LaunchedEffect(Unit) {
        if (!startOnboarding && container.auth.isConfigured && container.auth.currentUser == null) {
            val s = container.settings.snapshot()
            if (!s.loginSkipped) navController.navigate(Routes.LOGIN) { launchSingleTop = true }
        }
    }

    LaunchedEffect(navRequests) {
        navRequests.collect { target ->
            if (navController.currentDestination?.route == Routes.ONBOARDING) return@collect
            if (target == Routes.VAULT || target == Routes.HOME || target == Routes.BROWSER_HOME) {
                navController.navigateTopLevel(target)
            } else {
                navController.navigate(target) { launchSingleTop = true }
            }
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize()) {
        // Na página do navegador, o fundo vira o papel de parede do navegador (troca durante o deslize)
        SpaceBackdrop(browserAmount = {
            if (navController.currentDestination?.route != Routes.MAIN) {
                0f
            } else {
                val off = (pagerState.currentPage - MainTabs.BROWSER) + pagerState.currentPageOffsetFraction
                1f - kotlin.math.abs(off)
            }
        })
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            // Fundo transparente (para o espaço aparecer), mas texto sempre claro e visível.
            contentColor = MaterialTheme.colorScheme.onBackground,
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
                            onClose = { container.player.clearQueue() },
                        )
                    }
                    if (showBottom) {
                        BottomBar(pagerState.targetPage) { page -> rootScope.launch { pagerState.animateScrollToPage(page) } }
                    } else if (!fullScreen) {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.MAIN,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onFinished = {
                        navController.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.MAIN) { MainScreen(navController, pagerState) }
                composable(
                    Routes.LIBRARY,
                    arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry -> LibraryScreen(navController, entry.arguments?.getInt("tab") ?: 0) }
                composable(Routes.BROWSER_SETUP) { BrowserSetupScreen(navController) }
                composable(Routes.WALLPAPERS) { WallpaperStoreScreen(navController) }
                composable(Routes.WALLPAPER) { entry -> WallpaperPreviewScreen(navController, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.LOGIN) {
                    val scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                        LoginContent(
                            onDone = { navController.popBackStack() },
                            onSkip = {
                                scope.launch { container.settings.setLoginSkipped(true) }
                                navController.popBackStack()
                            },
                        )
                    }
                }
                composable(
                    Routes.BROWSER,
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType; defaultValue = "https://www.google.com/" },
                        navArgument("private") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { entry ->
                    BrowserScreen(
                        navController,
                        entry.arguments?.getString("url") ?: "https://www.google.com/",
                        private = entry.arguments?.getBoolean("private") ?: false,
                    )
                }
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
                composable(Routes.PRO) { com.musibox.app.ui.pro.ProScreen(navController) }
                composable(Routes.PICK_SONGS) { entry ->
                    com.musibox.app.ui.library.SongPickerScreen(navController, entry.arguments?.getString("id").orEmpty())
                }
                composable(Routes.VAULT_SETTINGS) { VaultSettingsScreen(navController) }
                composable(Routes.VAULT_VIEWER) { entry -> VaultViewerScreen(navController, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.VAULT_PLAYER) { entry -> VaultPlayerScreen(navController, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.VAULT_BACKUP) { VaultBackupScreen(navController) }
                composable(Routes.VAULT_CHANGE_PIN) { VaultChangePinScreen(navController) }
                composable(Routes.VAULT_RECOVER) { VaultRecoverScreen(navController) }
            }
        }
        }
        SyncResolutionHost()
    }
}
