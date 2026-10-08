package com.musibox.app.download

import java.util.Locale

enum class Platform(val label: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    TWITTER("X / Twitter"),
    OTHER("Link"),
    LOCAL("Arquivo do aparelho"),
}

object LinkUtils {
    private val urlRegex = Regex("""https?://[^\s<>"'“”]+""", RegexOption.IGNORE_CASE)

    /** Encontra o primeiro link dentro de um texto compartilhado (ex.: "Veja isso! https://..."). */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = urlRegex.find(text)?.value ?: return null
        return match.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
    }

    fun platformOf(url: String?, extractor: String? = null): Platform {
        val e = extractor?.lowercase(Locale.ROOT).orEmpty()
        val host = url?.let { hostOf(it) }.orEmpty()
        return when {
            e.startsWith("youtube") || host.endsWith("youtube.com") || host.endsWith("youtu.be") -> Platform.YOUTUBE
            e.startsWith("tiktok") || host.endsWith("tiktok.com") -> Platform.TIKTOK
            e.startsWith("instagram") || host.endsWith("instagram.com") -> Platform.INSTAGRAM
            e.startsWith("facebook") || host.endsWith("facebook.com") || host.endsWith("fb.watch") -> Platform.FACEBOOK
            e.startsWith("twitter") || host.endsWith("twitter.com") || host == "x.com" || host.endsWith(".x.com") -> Platform.TWITTER
            else -> Platform.OTHER
        }
    }

    fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringAfter('@')
            .substringBefore(':').lowercase(Locale.ROOT).removePrefix("www.").removePrefix("m.")

    fun isValidUrl(text: String?): Boolean = extractUrl(text) != null
}
