package com.musibox.app.ui.main

import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * As telas principais ficam lado a lado (deslize para trocar, como no Telegram).
 * Qualquer parte do app pede para abrir uma delas por aqui.
 */
object MainTabs {
    const val HOME = 0
    const val BROWSER = 1
    /** Loja de temas (papéis de parede). O cofre saiu da barra e abre pelo ícone do topo. */
    const val THEMES = 2
    const val MORE = 3
    const val COUNT = 4

    private val _pendingPage = MutableStateFlow<Int?>(null)
    val pendingPage: StateFlow<Int?> = _pendingPage.asStateFlow()

    /** Abre a tela principal correspondente a uma rota ("home", "browser_home", "themes", "more"). */
    fun open(route: String) {
        val uri = Uri.parse("musibox://$route")
        _pendingPage.value = when (uri.host) {
            "browser_home" -> BROWSER
            "themes", "wallpapers" -> THEMES
            "more" -> MORE
            else -> HOME
        }
    }

    fun go(page: Int) {
        _pendingPage.value = page
    }

    fun consumePage() {
        _pendingPage.value = null
    }
}

/** true quando a página do pager está parada na tela (não só passando durante o deslize). */
val LocalPageActive = compositionLocalOf { true }
