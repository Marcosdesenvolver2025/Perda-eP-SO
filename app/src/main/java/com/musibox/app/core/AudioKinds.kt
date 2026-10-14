package com.musibox.app.core

import android.content.Context
import com.musibox.app.data.db.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Separa músicas de outros áudios (aulas, gravações, podcasts, áudios do WhatsApp…).
 * A pessoa pode corrigir pelo menu da música ("Mover para Áudios" / "É uma música").
 */
class AudioKinds private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("audio_kinds", Context.MODE_PRIVATE)

    private val _overrides = MutableStateFlow(load())
    /** contentKey -> true (música) / false (áudio) escolhido pela pessoa. */
    val overrides: StateFlow<Map<String, Boolean>> = _overrides.asStateFlow()

    fun isMusic(song: Song, overrides: Map<String, Boolean> = _overrides.value): Boolean =
        overrides[song.contentKey] ?: guessIsMusic(song)

    fun set(song: Song, music: Boolean) {
        val updated = _overrides.value + (song.contentKey to music)
        _overrides.value = updated
        prefs.edit().putString(song.contentKey, if (music) "m" else "a").apply()
    }

    private fun load(): Map<String, Boolean> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to (it == "m") } }.toMap()

    companion object {
        @Volatile private var instance: AudioKinds? = null

        fun get(context: Context): AudioKinds =
            instance ?: synchronized(this) { instance ?: AudioKinds(context.applicationContext).also { instance = it } }

        private val NON_MUSIC_DIRS = listOf(
            "recordings", "recorder", "callrecord", "call recordings", "call_recordings", "gravações", "gravacoes",
            "gravador", "audiobooks", "podcasts", "voice", "notes",
        )

        private val NON_MUSIC_NAME = Regex(
            "(^|[^\\p{L}])(aulas?|lesson|modulo|módulo|mod\\d+|podcast|epis[oó]dio|palestra|serm[aã]o|prega[cç][aã]o|" +
                "audiobook|audiolivro|grava[cç][aã]o|recording|entrevista|webinar|curso|apostila)([^\\p{L}]|$)" +
                "|^aud-\\d|^ptt-|^rec[_ -]?\\d|^voice|voice[ _-]?(note|message|memo)|_audio$|^audio[ _-]?\\d|whatsapp audio|áudio do whatsapp",
        )

        /** Palpite automático: o que parece música fica em "Todas"; o resto vai para "Áudios". */
        fun guessIsMusic(song: Song): Boolean {
            val path = song.folderPath.lowercase()
            if (NON_MUSIC_DIRS.any { dir -> path.split('/').any { it == dir || it.startsWith("$dir ") || it.endsWith(" $dir") } }) return false
            val name = song.title.lowercase().trim()
            if (NON_MUSIC_NAME.containsMatchIn(name)) return false
            val unknownArtist = song.artist.isBlank() || song.artist == "<unknown>"
            // Áudio sem artista e muito longo (mais de 20 min) costuma ser aula, palestra ou gravação.
            if (unknownArtist && song.durationMs > 20 * 60_000L) return false
            return true
        }

        /** "Kendrick Lamar, SZA" -> ["Kendrick Lamar", "SZA"] (cada cantor ganha sua pasta). */
        fun singers(artist: String): List<String> {
            if (artist.isBlank() || artist == "<unknown>") return emptyList()
            return artist
                // Só separa por marcas claras de parceria; duplas como "Henrique e Juliano" ficam juntas.
                .split(Regex("\\s*(,|;|/|\\bfeat\\.|\\bfeat\\b|\\bft\\.|\\bpart\\.)\\s*", RegexOption.IGNORE_CASE))
                .map { it.trim().trim('(', ')', '[', ']').trim() }
                .filter { it.length >= 2 }
                .distinctBy { it.lowercase() }
                .ifEmpty { listOf(artist.trim()) }
        }
    }
}
