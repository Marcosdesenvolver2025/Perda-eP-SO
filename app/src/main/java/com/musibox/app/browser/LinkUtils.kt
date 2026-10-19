package com.musibox.app.browser

import java.util.Locale

/** Utilidades de endereços para o navegador (achar link num texto, normalizar, extrair o site). */
object LinkUtils {
    private val urlRegex = Regex("""https?://[^\s<>"'“”]+""", RegexOption.IGNORE_CASE)

    /** Encontra o primeiro link dentro de um texto (ex.: "Veja isso! https://..."). */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = urlRegex.find(text)?.value ?: return null
        return match.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
    }

    fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringAfter('@')
            .substringBefore(':').lowercase(Locale.ROOT).removePrefix("www.").removePrefix("m.")

    fun isValidUrl(text: String?): Boolean = extractUrl(text) != null

    /** O texto parece um endereço (com ou sem http)? Ex.: "g1.globo.com/economia" */
    fun looksLikeAddress(text: String): Boolean {
        val t = text.trim()
        if (t.contains(' ')) return false
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return true
        return Regex("""^[\w-]+(\.[\w-]+)+(/.*)?$""").matches(t)
    }

    /** Normaliza um endereço digitado ("shopee.com.br" → "https://shopee.com.br"). */
    fun normalizeAddress(text: String): String {
        val t = text.trim()
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }
}
