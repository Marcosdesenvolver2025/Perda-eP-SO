package com.musibox.app.ui.navigation

import android.net.Uri
import androidx.navigation.NavController

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIBRARY = "library?tab={tab}"
    const val DOWNLOAD = "download?tab={tab}&url={url}"
    const val VAULT = "vault"
    const val MORE = "more"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val STORAGE = "storage"
    const val HISTORY = "history?tab={tab}"
    const val SONGS = "songs/{type}/{value}"
    const val PLAYLIST = "playlist/{id}"
    const val VAULT_SETTINGS = "vault_settings"
    const val VAULT_VIEWER = "vault_view/{id}"
    const val VAULT_PLAYER = "vault_play/{id}"
    const val VAULT_BACKUP = "vault_backup"
    const val VAULT_CHANGE_PIN = "vault_change_pin"
    const val VAULT_RECOVER = "vault_recover"
    const val DOWNLOADS = "downloads"
    const val BROWSER = "browser?url={url}"

    fun library(tab: Int = 0) = "library?tab=$tab"
    fun download(tab: Int = 0, url: String? = null) =
        "download?tab=$tab" + (url?.let { "&url=${Uri.encode(it)}" } ?: "")
    fun history(tab: Int = 0) = "history?tab=$tab"
    fun browser(url: String) = "browser?url=${Uri.encode(url)}"
    fun songs(type: String, value: String) = "songs/$type/${Uri.encode(value)}"
    fun playlist(id: String) = "playlist/$id"
    fun vaultViewer(id: String) = "vault_view/$id"
    fun vaultPlayer(id: String) = "vault_play/$id"

    val topLevel = setOf(HOME, LIBRARY, DOWNLOAD, VAULT, MORE)
    val vaultRoutes = setOf(VAULT, VAULT_SETTINGS, VAULT_VIEWER, VAULT_PLAYER, VAULT_BACKUP, VAULT_CHANGE_PIN)
}

/** Navega para uma aba principal mantendo o estado de cada aba. */
fun NavController.navigateTopLevel(route: String, restore: Boolean = true) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = restore }
        launchSingleTop = true
        restoreState = restore
    }
}
