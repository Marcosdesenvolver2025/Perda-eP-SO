package com.musibox.app.ui.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.data.db.DownloadStatus
import com.musibox.app.ui.browser.BrowserHomeScreen
import com.musibox.app.ui.components.BrandHeader
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.download.DownloadScreen
import com.musibox.app.ui.home.HomeScreen
import com.musibox.app.ui.more.MoreScreen
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.vault.VaultScreen
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * As abas principais lado a lado: deslize para a esquerda/direita para trocar de tela
 * (como no Telegram) ou toque na barra de baixo.
 */
@Composable
fun MainScreen(nav: NavController, pager: PagerState) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by MainTabs.pendingPage.collectAsStateWithLifecycle()
    val downloadRequest by MainTabs.downloadRequest.collectAsStateWithLifecycle()
    val downloads by container.downloads.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val prefs = remember { context.getSharedPreferences("ui_state", Context.MODE_PRIVATE) }
    var seenAt by remember { mutableLongStateOf(prefs.getLong("downloads_seen", 0L)) }
    val bellDot = downloads.any {
        it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED ||
            ((it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED) && it.updatedAt > seenAt)
    }

    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        if (abs(p - pager.currentPage) > 1) pager.scrollToPage(p) else pager.animateScrollToPage(p)
        MainTabs.consumePage()
    }
    BackHandler(enabled = pager.currentPage != MainTabs.HOME) {
        scope.launch { pager.animateScrollToPage(MainTabs.HOME) }
    }

    Column(Modifier.fillMaxSize()) {
        BrandHeader(
            onSearch = {
                if (pager.currentPage == MainTabs.BROWSER || pager.currentPage == MainTabs.DOWNLOAD) {
                    nav.navigate(Routes.browser("https://m.youtube.com"))
                } else {
                    nav.navigate(Routes.SEARCH)
                }
            },
            onBell = {
                val now = System.currentTimeMillis()
                prefs.edit().putLong("downloads_seen", now).apply()
                seenAt = now
                nav.navigate(Routes.DOWNLOADS)
            },
            bellDot = bellDot,
            onSettings = { nav.navigate(Routes.SETTINGS) },
        )
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .consumeWindowInsets(WindowInsets.statusBars),
            key = { it },
        ) { page ->
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
                    when (page) {
                        MainTabs.HOME -> HomeScreen(nav)
                        MainTabs.BROWSER -> BrowserHomeScreen(nav)
                        MainTabs.DOWNLOAD -> DownloadScreen(nav, downloadRequest) { MainTabs.consumeDownload() }
                        MainTabs.VAULT -> VaultScreen(nav)
                        else -> MoreScreen(nav)
                    }
                }
            }
        }
    }
}
