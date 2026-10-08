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
        val target = when {
            intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false) -> Routes.PLAYER
            intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) -> Routes.DOWNLOADS
            intent.getBooleanExtra(EXTRA_OPEN_VAULT, false) -> Routes.VAULT
            intent.getStringExtra(EXTRA_DOWNLOAD_URL) != null -> Routes.download(0, intent.getStringExtra(EXTRA_DOWNLOAD_URL))
            else -> null
        } ?: return
        navRequests.tryEmit(target)
        intent.removeExtra(EXTRA_OPEN_PLAYER)
        intent.removeExtra(EXTRA_OPEN_DOWNLOADS)
        intent.removeExtra(EXTRA_OPEN_VAULT)
        intent.removeExtra(EXTRA_DOWNLOAD_URL)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
        const val EXTRA_OPEN_VAULT = "open_vault"
        const val EXTRA_DOWNLOAD_URL = "download_url"
    }
}
