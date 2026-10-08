package com.musibox.app.core

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Identifica uma música de forma independente do aparelho, para que playlists,
 * favoritos e histórico sincronizados encontrem o mesmo arquivo em outro celular.
 *
 * - contentKey: título + artista + duração (em blocos de 2 s)
 * - looseKey: título + artista (usado quando a duração difere um pouco)
 */
object SongKeys {
    private val marks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    fun normalize(value: String?): String {
        val raw = value?.takeUnless { it.isBlank() || it == "<unknown>" } ?: return ""
        val lower = raw.lowercase(Locale.ROOT)
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(marks, "")
        return noAccents.replace(nonAlnum, " ").trim()
    }

    fun contentKey(title: String?, artist: String?, durationMs: Long): String =
        sha1("${normalize(title)}|${normalize(artist)}|${durationMs / 2000}")

    fun looseKey(title: String?, artist: String?): String =
        sha1("${normalize(title)}|${normalize(artist)}")

    fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(String.format(Locale.ROOT, "%02x", b))
        return sb.toString()
    }

    fun displayArtist(artist: String?): String =
        artist?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Artista desconhecido"

    fun displayAlbum(album: String?): String =
        album?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Álbum desconhecido"
}
