package com.musibox.app.themes

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado visual atual do app (lido pelo Compose): cor de destaque e papel de parede do app.
 * Trocar aqui recompõe a interface na hora.
 */
object AppTheme {
    val DefaultAccent = Color(0xFF1FDF67)

    var accent by mutableStateOf(DefaultAccent)
        internal set

    /** Papel de parede de fundo do app (null = fundo preto padrão). */
    var appWallpaper by mutableStateOf<Wallpaper?>(null)
        internal set

    /** Papel de parede da página inicial do navegador. */
    var browserWallpaper by mutableStateOf(Wallpapers.get(Wallpapers.DEFAULT_BROWSER)!!)
        internal set
}

/** Preferências de tema e da página inicial do navegador (ficam no aparelho). */
class ThemeStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("themes", Context.MODE_PRIVATE)

    private val _interests = MutableStateFlow(prefs.getStringSet(KEY_INTERESTS, null)?.toSet() ?: setOf("Esportes", "IA"))
    val interests: StateFlow<Set<String>> = _interests.asStateFlow()

    private val _setupDone = MutableStateFlow(prefs.getBoolean(KEY_SETUP, false))
    val browserSetupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    init {
        apply(prefs.getString(KEY_APP, null), prefs.getString(KEY_BROWSER, Wallpapers.DEFAULT_BROWSER))
    }

    private fun apply(appId: String?, browserId: String?) {
        val app = Wallpapers.get(appId)
        AppTheme.appWallpaper = app
        AppTheme.accent = app?.accent ?: AppTheme.DefaultAccent
        AppTheme.browserWallpaper = Wallpapers.get(browserId) ?: Wallpapers.get(Wallpapers.DEFAULT_BROWSER)!!
    }

    fun appWallpaperId(): String? = prefs.getString(KEY_APP, null)

    fun browserWallpaperId(): String = prefs.getString(KEY_BROWSER, Wallpapers.DEFAULT_BROWSER) ?: Wallpapers.DEFAULT_BROWSER

    /** Usa o papel no fundo do app (null volta ao preto padrão). */
    fun setAppWallpaper(id: String?) {
        prefs.edit().putString(KEY_APP, id).apply()
        apply(id, browserWallpaperId())
    }

    fun setBrowserWallpaper(id: String) {
        prefs.edit().putString(KEY_BROWSER, id).apply()
        apply(appWallpaperId(), id)
    }

    fun setInterests(values: Set<String>) {
        _interests.value = values
        prefs.edit().putStringSet(KEY_INTERESTS, values).apply()
    }

    fun finishBrowserSetup() {
        _setupDone.value = true
        prefs.edit().putBoolean(KEY_SETUP, true).apply()
    }

    /** Se a pessoa perdeu um papel pago (ex.: reembolso, fim do Pro), volta ao padrão. */
    fun revalidate(isAllowed: (Wallpaper) -> Boolean) {
        val app = Wallpapers.get(appWallpaperId())
        if (app != null && !isAllowed(app)) setAppWallpaper(null)
        val browser = Wallpapers.get(browserWallpaperId())
        if (browser != null && !isAllowed(browser)) setBrowserWallpaper(Wallpapers.DEFAULT_BROWSER)
    }

    companion object {
        private const val KEY_APP = "app_wallpaper"
        private const val KEY_BROWSER = "browser_wallpaper"
        private const val KEY_INTERESTS = "interests"
        private const val KEY_SETUP = "browser_setup_done"

        val ALL_INTERESTS = listOf("Entretenimento", "IA", "Esportes", "Compras", "Viagens")

        @Volatile private var instance: ThemeStore? = null

        fun get(context: Context): ThemeStore =
            instance ?: synchronized(this) { instance ?: ThemeStore(context.applicationContext).also { instance = it } }
    }
}
