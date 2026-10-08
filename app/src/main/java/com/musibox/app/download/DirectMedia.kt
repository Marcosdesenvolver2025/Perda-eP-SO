package com.musibox.app.download

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Fotos: lê a imagem pública de uma página (og:image, a mesma que aparece na prévia de links)
 * e baixa arquivos diretos. Não usa login nem cookies; conteúdo privado continua inacessível.
 */
object DirectMedia {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

    private val metaTag = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attr = Regex("""([a-zA-Z:_-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
    private val titleTag = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)

    /** Monta opções de foto a partir das imagens públicas da página, ou null se não houver. */
    fun photosFromPage(pageUrl: String): MediaInfo? {
        val html = runCatching { fetchText(pageUrl) }.getOrNull() ?: return null
        val metas = metaTag.findAll(html).map { tag ->
            attr.findAll(tag.value).associate { m ->
                m.groupValues[1].lowercase(Locale.ROOT) to unescape(m.groupValues[3].ifEmpty { m.groupValues[4] })
            }
        }.toList()
        fun metaValues(vararg names: String): List<String> = metas.mapNotNull { m ->
            val key = m["property"] ?: m["name"] ?: return@mapNotNull null
            if (key.lowercase(Locale.ROOT) in names) m["content"] else null
        }
        val images = metaValues("og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src")
            .filter { it.startsWith("http") }
            .distinct()
            .take(10)
        if (images.isEmpty()) return null
        val title = metaValues("og:title", "twitter:title").firstOrNull()
            ?: titleTag.find(html)?.groupValues?.get(1)?.let { unescape(it).trim() }
            ?: "Foto"
        val platform = LinkUtils.platformOf(pageUrl)
        return MediaInfo(
            url = pageUrl,
            webpageUrl = pageUrl,
            title = title.take(120).ifBlank { "Foto" },
            thumbnail = images.first(),
            durationSec = 0,
            uploader = null,
            platform = platform,
            options = FormatParser.photoOptions(images),
            moreOptions = emptyList(),
        )
    }

    /** Baixa um arquivo direto para [dir]. Devolve o arquivo salvo. */
    fun download(url: String, dir: File, fallbackExt: String, isCanceled: () -> Boolean, onProgress: (Float, Long) -> Unit): File {
        dir.mkdirs()
        val conn = open(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw DownloadError("Não foi possível baixar a imagem (erro $code).", retryable = code >= 500)
            val type = conn.contentType.orEmpty().lowercase(Locale.ROOT)
            val ext = when {
                "jpeg" in type || "jpg" in type -> "jpg"
                "png" in type -> "png"
                "webp" in type -> "webp"
                "gif" in type -> "gif"
                "heic" in type -> "heic"
                "mp4" in type -> "mp4"
                "mpeg" in type -> "mp3"
                else -> fallbackExt.ifBlank { "jpg" }
            }
            val total = conn.contentLengthLong
            val out = File(dir, "media.$ext")
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCanceled()) throw IOException("cancelado")
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done * 100f / total, total)
                    }
                }
            }
            if (out.length() <= 0) throw DownloadError("A imagem baixada está vazia.")
            return out
        } catch (e: DownloadError) {
            throw e
        } catch (e: IOException) {
            throw DownloadError("Falha na conexão ao baixar a imagem.", retryable = true, detail = e.message)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        }

    private fun fetchText(url: String): String {
        val conn = open(url)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val bytes = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(32 * 1024)
                while (buf.size() < 3_000_000) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
                buf.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\u0026", "&")
}
