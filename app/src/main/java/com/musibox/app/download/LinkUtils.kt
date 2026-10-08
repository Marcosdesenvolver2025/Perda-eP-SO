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

    /** O texto parece um endereço (com ou sem http)? Ex.: "youtube.com/watch?v=..." */
    fun looksLikeAddress(text: String): Boolean {
        val t = text.trim()
        if (t.contains(' ')) return false
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return true
        return Regex("""^[\w-]+(\.[\w-]+)+(/.*)?$""").matches(t)
    }

    /** Normaliza um endereço digitado ("youtube.com" → "https://youtube.com"). */
    fun normalizeAddress(text: String): String {
        val t = text.trim()
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }

    /**
     * A página aberta no navegador é de um vídeo/música que pode ser baixado?
     * (Usado para mostrar o botão "Baixar" no navegador.)
     */
    fun isMediaPage(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = hostOf(url)
        val path = url.substringAfter("://", "").substringAfter('/', "").substringBefore('#').lowercase(Locale.ROOT)
        val segments = path.substringBefore('?').split('/').filter { it.isNotBlank() }
        return when {
            host == "youtu.be" -> segments.isNotEmpty()
            host.endsWith("youtube.com") ->
                path.startsWith("watch") || path.startsWith("shorts/") || path.startsWith("live/")
            host.endsWith("tiktok.com") ->
                "/video/" in "/$path" || "/photo/" in "/$path" || host.startsWith("vm.") || host.startsWith("vt.")
            host.endsWith("instagram.com") ->
                segments.firstOrNull() in setOf("reel", "reels", "p", "tv") && segments.size >= 2
            host == "fb.watch" -> segments.isNotEmpty()
            host.endsWith("facebook.com") ->
                path.startsWith("watch") || "reel" in path || "/videos/" in "/$path" || path.startsWith("story.php")
            host == "x.com" || host.endsWith("twitter.com") -> "/status/" in "/$path"
            host.endsWith("soundcloud.com") ->
                segments.size >= 2 && segments[0] !in setOf("discover", "search", "you", "stream", "charts", "upload", "settings", "messages", "notifications")
            host.endsWith("vimeo.com") -> segments.any { seg -> seg.all { it.isDigit() } }
            host.endsWith("dailymotion.com") -> "/video/" in "/$path"
            host.endsWith("pinterest.com") || host.endsWith("pinterest.com.br") || host == "pin.it" ->
                "/pin/" in "/$path" || host == "pin.it"
            host.endsWith("kwai.com") -> "/video/" in "/$path" || "/photo/" in "/$path"
            host.endsWith("twitch.tv") -> "/videos/" in "/$path" || "/clip/" in "/$path" || host.startsWith("clips.")
            host.endsWith("bandcamp.com") -> "/track/" in "/$path"
            else -> false
        }
    }
}
