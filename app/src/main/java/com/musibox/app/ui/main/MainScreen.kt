package com.musibox.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.musibox.app.ui.navigation.LocalBottomBarSpace
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.ui.browser.BrowserHomeScreen
import com.musibox.app.ui.components.BrandHeader
import com.musibox.app.ui.home.HomeScreen
import com.musibox.app.ui.more.MoreScreen
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.themes.WallpaperStoreContent
import androidx.compose.ui.unit.dp
import com.musibox.app.themes.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * As abas principais lado a lado: deslize para a esquerda/direita para trocar de tela
 * (como no Telegram) ou toque na barra de baixo. O navegador tem a própria página inicial
 * (logo, busca, atalhos e notícias), sem o cabeçalho do app.
 */
@Composable
fun MainScreen(nav: NavController, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val pending by MainTabs.pendingPage.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        if (abs(p - pager.currentPage) > 1) pager.scrollToPage(p) else pager.animateScrollToPage(p)
        MainTabs.consumePage()
    }
    BackHandler(enabled = pager.currentPage != MainTabs.HOME) {
        scope.launch { pager.animateScrollToPage(MainTabs.HOME) }
    }

    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        key = { it },
    ) { page ->
        // Temas e Mais ficam sempre no visual padrão: liga quando a aba fica na frente (mais da metade na tela)
        if (page == MainTabs.THEMES || page == MainTabs.MORE) {
            val front = pager.currentPage == page
            DisposableEffect(front) {
                if (front) AppTheme.useDefaultLook(true)
                onDispose { if (front) AppTheme.useDefaultLook(false) }
            }
        }
        CompositionLocalProvider(LocalPageActive provides (pager.settledPage == page && !pager.isScrollInProgress)) {
            // Transição 3D: a página que sai gira levemente, diminui e escurece.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        val a = abs(offset)
                        alpha = 1f - a * 0.45f
                        val sc = 1f - a * 0.1f
                        scaleX = sc
                        scaleY = sc
                        rotationY = offset * 14f
                        cameraDistance = 18f * density
                    },
            ) {
                if (page == MainTabs.BROWSER) {
                    BrowserHomeScreen(nav)
                } else {
                    Column(Modifier.fillMaxSize()) {
                        BrandHeader(
                            onSearch = { nav.navigate(Routes.SEARCH) },
                            onVault = { nav.navigate(Routes.VAULT) { launchSingleTop = true } },
                            onSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                            when (page) {
                                MainTabs.HOME -> HomeScreen(nav)
                                MainTabs.THEMES -> WallpaperStoreContent(nav, bottomPadding = 24.dp + LocalBottomBarSpace.current, title = true)
                                else -> MoreScreen(nav)
                            }
                        }
                    }
                }
            }
        }
    }
}
