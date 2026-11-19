package com.musibox.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Modo de capturas para a página da loja. Só existe em builds feitos com -Pmusibox.shots=true
 * (nunca no app publicado): pula a apresentação, libera os recursos e prepara músicas de exemplo.
 */
object ShotsMode {
    val on: Boolean get() = BuildConfig.SHOTS

    fun seed(app: MusiBoxApp) {
        if (!on) return
        val c = app.container
        c.appScope.launch {
            c.settings.setOnboardingDone(true)
            c.settings.setLoginSkipped(true)
            com.musibox.app.themes.ThemeStore.get(app).finishBrowserSetup()
            var songs = c.music.songs.first()
            repeat(40) {
                if (songs.size >= 8) return@repeat
                runCatching { c.music.rescan() }
                delay(1500)
                songs = c.music.songs.first()
            }
            if (songs.isEmpty()) return@launch
            val sorted = songs.sortedBy { it.title }
            if (c.library.playlists.first().isEmpty()) {
                val id = c.library.createPlaylist("Para relaxar")
                c.library.addToPlaylist(id, sorted.filterIndexed { i, _ -> i % 2 == 0 }.take(9))
                val id2 = c.library.createPlaylist("Treino")
                c.library.addToPlaylist(id2, sorted.filterIndexed { i, _ -> i % 2 == 1 }.take(7))
                val id3 = c.library.createPlaylist("Viagem de carro")
                c.library.addToPlaylist(id3, sorted.takeLast(6))
                // Mais ouvidas e Histórico
                val counts = listOf(24, 19, 15, 12, 10, 8, 6, 5, 3, 2)
                sorted.shuffled(java.util.Random(7)).take(counts.size).forEachIndexed { i, s ->
                    repeat(counts[i]) { c.history.recordPlay(s.contentKey, s.looseKey, s.id, s.title, s.artist, s.album, s.durationMs) }
                }
                sorted.take(5).forEach { runCatching { c.library.toggleFavorite(it) } }
            }
        }
    }
}
