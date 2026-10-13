package com.musibox.app.download

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Histórico, favoritos e atalhos do navegador do MusiBox (ficam só no aparelho). */
class BrowserStore private constructor(context: Context) {
    data class Entry(val title: String, val url: String, val time: Long)

    private val prefs = context.getSharedPreferences("browser_store", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load(KEY_HISTORY))
    val history: StateFlow<List<Entry>> = _history.asStateFlow()

    private val _bookmarks = MutableStateFlow(load(KEY_BOOKMARKS))
    val bookmarks: StateFlow<List<Entry>> = _bookmarks.asStateFlow()

    private val _shortcuts = MutableStateFlow(load(KEY_SHORTCUTS))
    val shortcuts: StateFlow<List<Entry>> = _shortcuts.asStateFlow()

    private val _lastUrl = MutableStateFlow(prefs.getString(KEY_LAST, null))
    val lastUrl: StateFlow<String?> = _lastUrl.asStateFlow()

    fun recordVisit(url: String, title: String?) {
        if (!url.startsWith("http")) return
        val clean = title?.trim().orEmpty().ifBlank { hostOf(url) }
        val now = System.currentTimeMillis()
        val list = _history.value.filterNot { same(it.url, url) }
        val updated = (listOf(Entry(clean, url, now)) + list).take(MAX_HISTORY)
        _history.value = updated
        save(KEY_HISTORY, updated)
        _lastUrl.value = url
        prefs.edit().putString(KEY_LAST, url).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        save(KEY_HISTORY, emptyList())
    }

    fun removeHistory(url: String) {
        val updated = _history.value.filterNot { it.url == url }
        _history.value = updated
        save(KEY_HISTORY, updated)
    }

    fun isBookmarked(url: String): Boolean = _bookmarks.value.any { same(it.url, url) }

    /** Salva ou remove dos favoritos. Retorna true se ficou salvo. */
    fun toggleBookmark(url: String, title: String?): Boolean {
        val exists = isBookmarked(url)
        val updated = if (exists) {
            _bookmarks.value.filterNot { same(it.url, url) }
        } else {
            listOf(Entry(title?.trim().orEmpty().ifBlank { hostOf(url) }, url, System.currentTimeMillis())) + _bookmarks.value
        }
        _bookmarks.value = updated
        save(KEY_BOOKMARKS, updated)
        return !exists
    }

    fun addShortcut(name: String, url: String) {
        val updated = _shortcuts.value.filterNot { same(it.url, url) } + Entry(name.trim().ifBlank { hostOf(url) }, url, System.currentTimeMillis())
        _shortcuts.value = updated
        save(KEY_SHORTCUTS, updated)
    }

    fun removeShortcut(url: String) {
        val updated = _shortcuts.value.filterNot { it.url == url }
        _shortcuts.value = updated
        save(KEY_SHORTCUTS, updated)
    }

    private fun same(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')

    private fun load(key: String): List<Entry> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optString("t"), o.getString("u"), o.optLong("w"))
        }
    }.getOrDefault(emptyList())

    private fun save(key: String, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url).put("w", it.time)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_SHORTCUTS = "shortcuts"
        private const val KEY_LAST = "last_url"
        private const val MAX_HISTORY = 80

        @Volatile private var instance: BrowserStore? = null

        fun get(context: Context): BrowserStore =
            instance ?: synchronized(this) { instance ?: BrowserStore(context.applicationContext).also { instance = it } }

        fun hostOf(url: String): String = runCatching { android.net.Uri.parse(url).host.orEmpty().removePrefix("www.").removePrefix("m.") }.getOrDefault(url)
    }
}
