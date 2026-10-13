package com.musibox.app.ui.main

import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * As cinco telas principais ficam lado a lado (deslize para trocar, como no Telegram).
 * Qualquer parte do app pede para abrir uma delas por aqui.
 */
object MainTabs {
    const val HOME = 0
    const val BROWSER = 1
    const val DOWNLOAD = 2
    const val VAULT = 3
    const val MORE = 4
    const val COUNT = 5

    data class DownloadRequest(val tab: Int, val url: String?, val id: Long = System.nanoTime())

    private val _pendingPage = MutableStateFlow<Int?>(null)
    val pendingPage: StateFlow<Int?> = _pendingPage.asStateFlow()

    private val _download = MutableStateFlow<DownloadRequest?>(null)
    val downloadRequest: StateFlow<DownloadRequest?> = _download.asStateFlow()

    /** Abre a tela principal correspondente a uma rota antiga ("home", "vault", "download?url=…"). */
    fun open(route: String) {
        val uri = Uri.parse("musibox://$route")
        val page = when (uri.host) {
            "browser_home" -> BROWSER
            "download" -> DOWNLOAD
            "vault" -> VAULT
            "more" -> MORE
            else -> HOME
        }
        if (page == DOWNLOAD) {
            val tab = uri.getQueryParameter("tab")?.toIntOrNull() ?: 0
            val url = uri.getQueryParameter("url")
            if (tab != 0 || !url.isNullOrBlank()) _download.value = DownloadRequest(tab, url)
        }
        _pendingPage.value = page
    }

    fun go(page: Int) {
        _pendingPage.value = page
    }

    fun consumePage() {
        _pendingPage.value = null
    }

    fun consumeDownload() {
        _download.value = null
    }
}

/** true quando a página do pager está parada na tela (não só passando durante o deslize). */
val LocalPageActive = compositionLocalOf { true }
