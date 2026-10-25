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

    // Paleta do app inteiro (vem do papel de parede escolhido; sem papel = preto e verde padrão)
    val DefaultBg = Color(0xFF121212)
    val DefaultSurface = Color(0xFF181818)
    val DefaultCard = Color(0xFF1E1E1E)
    val DefaultCardHigh = Color(0xFF282828)
    val DefaultLine = Color(0xFF2E2E2E)

    var accent2 by mutableStateOf(Color(0xFF3BEA80))
        internal set
    var bg by mutableStateOf(DefaultBg)
        internal set
    var surface by mutableStateOf(DefaultSurface)
        internal set
    var card by mutableStateOf(DefaultCard)
        internal set
    var cardHigh by mutableStateOf(DefaultCardHigh)
        internal set
    var line by mutableStateOf(DefaultLine)
        internal set

    /** Força da camada escura sobre o papel de parede (calculada pela claridade de cada imagem). */
    var scrim by mutableStateOf(0.42f)
        internal set

    /** Há papel de parede no fundo agora (textos ganham sombra e ficam mais claros). */
    val themed: Boolean get() = paletteKey.isNotEmpty()

    /** true quando as cores vêm de um tema (para o Compose recalcular o esquema). */
    var paletteKey by mutableStateOf("")
        internal set

    /** true nas telas "Mais" e "Planos": elas ficam sempre no visual padrão (sem papel de parede e sem paleta do tema). */
    var neutral by mutableStateOf(false)
        private set

    // Contador: telas "Mais" e "Planos" podem se sobrepor na troca de tela (uma entra antes da outra sair).
    private var neutralRefs = 0

    fun useDefaultLook(on: Boolean) {
        neutralRefs = (neutralRefs + if (on) 1 else -1).coerceAtLeast(0)
        val n = neutralRefs > 0
        if (neutral == n) return
        neutral = n
        applyPalette(appWallpaper)
    }

    internal fun applyPalette(themeWallpaper: Wallpaper?) {
        val w = if (neutral) null else themeWallpaper
        val p = w?.let { WallpaperPalettes[it.id] }
        if (w == null || p == null || p.size < 6) {
            accent = w?.accent ?: DefaultAccent
            accent2 = Color(0xFF3BEA80)
            bg = DefaultBg; surface = DefaultSurface; card = DefaultCard; cardHigh = DefaultCardHigh; line = DefaultLine
            scrim = 0.42f
            paletteKey = w?.id.orEmpty()
            return
        }
        accent = w.accent
        accent2 = Color(p[0])
        bg = Color(p[1]); surface = Color(p[2]); card = Color(p[3]); cardHigh = Color(p[4]); line = Color(p[5])
        scrim = if (p.size >= 7) ((p[6] ushr 24) and 0xFF).toFloat() / 255f else 0.42f
        paletteKey = w.id
    }

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

    /** Widget de notícias na página inicial do navegador (a pessoa escolhe na primeira vez). */
    private val _newsOn = MutableStateFlow(prefs.getBoolean(KEY_NEWS_ON, true))
    val newsEnabled: StateFlow<Boolean> = _newsOn.asStateFlow()
    private val _newsAsked = MutableStateFlow(prefs.getBoolean(KEY_NEWS_ASKED, false))
    val newsAsked: StateFlow<Boolean> = _newsAsked.asStateFlow()

    fun setNewsEnabled(on: Boolean) {
        _newsOn.value = on
        _newsAsked.value = true
        prefs.edit().putBoolean(KEY_NEWS_ON, on).putBoolean(KEY_NEWS_ASKED, true).apply()
    }

    init {
        apply(prefs.getString(KEY_APP, null), prefs.getString(KEY_BROWSER, Wallpapers.DEFAULT_BROWSER))
    }

    private fun apply(appId: String?, browserId: String?) {
        val app = Wallpapers.get(appId)
        AppTheme.appWallpaper = app
        AppTheme.applyPalette(app)
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
        private const val KEY_NEWS_ON = "news_enabled"
        private const val KEY_NEWS_ASKED = "news_asked"

        val ALL_INTERESTS = listOf("Entretenimento", "IA", "Esportes", "Compras", "Viagens")

        @Volatile private var instance: ThemeStore? = null

        fun get(context: Context): ThemeStore =
            instance ?: synchronized(this) { instance ?: ThemeStore(context.applicationContext).also { instance = it } }
    }
}
