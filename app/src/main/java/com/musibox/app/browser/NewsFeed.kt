package com.musibox.app.browser

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** Notícia da página inicial do navegador (título, link, imagem e fonte, vindas do RSS público dos sites). */
data class NewsItem(
    val title: String,
    val link: String,
    val image: String?,
    val source: String,
    val time: Long,
)

/** Abas de notícias, como no navegador de exemplo. */
enum class NewsTopic(val label: String, val feeds: List<String>) {
    FOR_YOU("Para você", emptyList()),
    HEADLINES("Manchetes", listOf("https://g1.globo.com/rss/g1/", "https://www.cnnbrasil.com.br/feed/")),
    FOOTBALL("Futebol", listOf("https://ge.globo.com/rss/ge/futebol/", "https://www.cnnbrasil.com.br/esportes/futebol/feed/")),
    SPORTS("Esportes", listOf("https://ge.globo.com/rss/ge/", "https://www.cnnbrasil.com.br/esportes/feed/")),
    TECH("Tecnologia", listOf("https://g1.globo.com/rss/g1/tecnologia/", "https://www.cnnbrasil.com.br/tecnologia/feed/")),
    FUN("Entretenimento", listOf("https://g1.globo.com/rss/g1/pop-arte/", "https://www.cnnbrasil.com.br/entretenimento/feed/")),
    MONEY("Economia", listOf("https://g1.globo.com/rss/g1/economia/", "https://www.cnnbrasil.com.br/economia/feed/")),
    TRAVEL("Viagens", listOf("https://g1.globo.com/rss/g1/turismo-e-viagem/", "https://www.cnnbrasil.com.br/viagemegastronomia/feed/")),
    ;

    companion object {
        val tabs = listOf(FOR_YOU, HEADLINES, FOOTBALL, SPORTS, TECH, FUN)

        /** Interesses escolhidos em "Configurar seu navegador" → temas de "Para você". */
        fun forInterests(interests: Set<String>): List<NewsTopic> {
            val list = interests.mapNotNull {
                when (it) {
                    "Entretenimento" -> FUN
                    "IA" -> TECH
                    "Esportes" -> FOOTBALL
                    "Compras" -> MONEY
                    "Viagens" -> TRAVEL
                    else -> null
                }
            }
            return (listOf(HEADLINES) + list).distinct()
        }
    }
}

class NewsFeed private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("news_cache", Context.MODE_PRIVATE)
    private val memory = mutableMapOf<String, Pair<Long, List<NewsItem>>>()

    /** Notícias de um tema (usa o cache de até 20 min; sem internet, mostra o último salvo). */
    suspend fun load(topic: NewsTopic, interests: Set<String>, force: Boolean = false): List<NewsItem> {
        if (topic == NewsTopic.FOR_YOU) {
            val parts = coroutineScope {
                NewsTopic.forInterests(interests).map { t -> async { load(t, interests, force).take(8) } }.awaitAll()
            }
            // intercala os temas para a lista ficar variada
            val mixed = mutableListOf<NewsItem>()
            val max = parts.maxOfOrNull { it.size } ?: 0
            for (i in 0 until max) parts.forEach { p -> p.getOrNull(i)?.let { mixed += it } }
            return mixed.distinctBy { it.link }
        }
        val key = topic.name
        val now = System.currentTimeMillis()
        if (!force) memory[key]?.let { (t, items) -> if (now - t < 20 * 60_000 && items.isNotEmpty()) return items }
        val fresh = withContext(Dispatchers.IO) {
            var result = emptyList<NewsItem>()
            for (url in topic.feeds) {
                result = runCatching { fetch(url) }.getOrDefault(emptyList())
                if (result.size >= 3) break
            }
            result
        }
        return if (fresh.isNotEmpty()) {
            memory[key] = now to fresh
            save(key, fresh)
            fresh
        } else {
            memory[key]?.second ?: restore(key)
        }
    }

    private fun fetch(url: String): List<NewsItem> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) MusiBox")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            return conn.inputStream.use { parse(it, LinkUtils.hostOf(url)) }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(input: java.io.InputStream, host: String): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val items = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""
        var link = ""
        var image: String? = null
        var date = 0L
        var desc = ""
        val source = sourceName(host)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && items.size < 30) {
            val name = parser.name?.lowercase(Locale.ROOT)
            when (event) {
                XmlPullParser.START_TAG -> when {
                    name == "item" || name == "entry" -> {
                        inItem = true; title = ""; link = ""; image = null; date = 0L; desc = ""
                    }
                    !inItem -> Unit
                    name == "title" -> title = parser.nextText().trim()
                    name == "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        link = href ?: parser.nextText().trim()
                    }
                    name == "pubdate" || name == "published" || name == "dc:date" -> date = parseDate(parser.nextText().trim())
                    name == "media:content" || name == "media:thumbnail" -> {
                        val u = parser.getAttributeValue(null, "url")
                        val medium = parser.getAttributeValue(null, "medium")
                        val type = parser.getAttributeValue(null, "type")
                        if (image == null && !u.isNullOrBlank() && (medium == null || medium == "image") && (type == null || type.startsWith("image"))) image = u
                    }
                    name == "enclosure" -> {
                        val u = parser.getAttributeValue(null, "url")
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        if (image == null && !u.isNullOrBlank() && type.startsWith("image")) image = u
                    }
                    name == "description" || name == "content:encoded" || name == "summary" -> desc += parser.nextText()
                }
                XmlPullParser.END_TAG -> if ((name == "item" || name == "entry") && inItem) {
                    inItem = false
                    if (image == null) image = IMG_RE.find(desc)?.groupValues?.get(1)
                    if (title.isNotBlank() && link.startsWith("http")) {
                        items += NewsItem(clean(title), link, image?.replace("&amp;", "&"), source, date)
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun clean(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim()

    private fun sourceName(host: String) = when {
        host.startsWith("ge.") -> "ge"
        host.contains("g1") -> "g1"
        host.contains("cnnbrasil") -> "CNN Brasil"
        else -> host
    }

    private fun parseDate(s: String): Long {
        val formats = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        for (f in formats) {
            runCatching { return SimpleDateFormat(f, Locale.US).parse(s)!!.time }
        }
        return 0L
    }

    private fun save(key: String, items: List<NewsItem>) {
        val arr = JSONArray()
        items.take(30).forEach {
            arr.put(JSONObject().put("t", it.title).put("l", it.link).put("i", it.image ?: "").put("s", it.source).put("w", it.time))
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun restore(key: String): List<NewsItem> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NewsItem(o.getString("t"), o.getString("l"), o.optString("i").ifBlank { null }, o.optString("s"), o.optLong("w"))
        }
    }.getOrDefault(emptyList())

    companion object {
        private val IMG_RE = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        @Volatile private var instance: NewsFeed? = null

        fun get(context: Context): NewsFeed =
            instance ?: synchronized(this) { instance ?: NewsFeed(context.applicationContext).also { instance = it } }

        /** "há 5 min", "há 2 h", "ontem"… */
        fun ago(time: Long): String {
            if (time <= 0) return ""
            val m = (System.currentTimeMillis() - time) / 60_000
            return when {
                m < 1 -> "agora"
                m < 60 -> "há $m min"
                m < 24 * 60 -> "há ${m / 60} h"
                m < 48 * 60 -> "ontem"
                else -> "há ${m / (24 * 60)} dias"
            }
        }
    }
}
