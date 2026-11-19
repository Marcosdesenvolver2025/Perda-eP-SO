package com.musibox.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.ui.MusiBoxRoot
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.MbPalette
import com.musibox.app.ui.theme.MusiBoxTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : FragmentActivity() {

    private val navRequests = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    private val container get() = (application as MusiBoxApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings: AppSettings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            if (s == null) {
                Box(Modifier.fillMaxSize().background(MbPalette.Bg))
            } else {
                val startOnboarding = remember { !s.onboardingDone }
                MusiBoxTheme(s.themeMode) {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        MusiBoxRoot(startOnboarding = startOnboarding, navRequests = navRequests)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.player.connect()
    }

    override fun onResume() {
        super.onResume()
        // Confere as compras de novo (ex.: pagamento pendente aprovado, assinatura cancelada)
        com.musibox.app.billing.PlayBilling.refresh()
    }

    override fun onStop() {
        super.onStop()
        container.player.release()
    }

    override fun onDestroy() {
        if (isFinishing) container.vaultLock.onAppClosed()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (ShotsMode.on) handleShot(intent)
        val target = when {
            intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false) -> Routes.PLAYER
            intent.getBooleanExtra(EXTRA_OPEN_VAULT, false) -> Routes.VAULT
            intent.getBooleanExtra(EXTRA_OPEN_PRO, false) -> Routes.PRO
            else -> null
        } ?: return
        navRequests.tryEmit(target)
        intent.removeExtra(EXTRA_OPEN_PLAYER)
        intent.removeExtra(EXTRA_OPEN_VAULT)
        intent.removeExtra(EXTRA_OPEN_PRO)
    }

    /** Comandos do modo de capturas (só em build de capturas): tema, tocar e abrir telas. */
    private fun handleShot(intent: Intent) {
        val wall = intent.getStringExtra("shot_wall")
        val route = intent.getStringExtra("shot_route")
        val play = intent.getBooleanExtra("shot_play", false)
        if (wall != null) com.musibox.app.themes.ThemeStore.get(this).setAppWallpaper(wall.takeUnless { it.isEmpty() || it == "none" })
        intent.getStringExtra("shot_bwall")?.let { com.musibox.app.themes.ThemeStore.get(this).setBrowserWallpaper(it) }
        lifecycleScope.launch {
            if (play) {
                val songs = container.music.songs.first().sortedBy { it.title }
                if (songs.isNotEmpty()) container.player.playSongs(songs, songs.size / 3)
            }
            when (route) {
                null -> Unit
                "themes", "more" -> {
                    navRequests.tryEmit(Routes.HOME)
                    kotlinx.coroutines.delay(600)
                    com.musibox.app.ui.main.MainTabs.go(
                        if (route == "themes") com.musibox.app.ui.main.MainTabs.THEMES else com.musibox.app.ui.main.MainTabs.MORE,
                    )
                }
                "home" -> navRequests.tryEmit(Routes.HOME)
                "vault" -> {
                    container.vaultLock.unlock()
                    navRequests.tryEmit(Routes.VAULT)
                }
                "shot_playlist" -> {
                    val id = container.library.playlists.first().firstOrNull()?.playlist?.id
                    if (id != null) navRequests.tryEmit("playlist/$id")
                }
                else -> navRequests.tryEmit(route)
            }
        }
        intent.removeExtra("shot_wall")
        intent.removeExtra("shot_route")
        intent.removeExtra("shot_play")
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_OPEN_VAULT = "open_vault"
        const val EXTRA_OPEN_PRO = "open_pro"
    }
}
