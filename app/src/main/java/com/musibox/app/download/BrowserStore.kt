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
    data class Tab(val id: String, val url: String, val title: String)

    private val prefs = context.getSharedPreferences("browser_store", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load(KEY_HISTORY))
    val history: StateFlow<List<Entry>> = _history.asStateFlow()

    private val _bookmarks = MutableStateFlow(load(KEY_BOOKMARKS))
    val bookmarks: StateFlow<List<Entry>> = _bookmarks.asStateFlow()

    private val _shortcuts = MutableStateFlow(load(KEY_SHORTCUTS))
    val shortcuts: StateFlow<List<Entry>> = _shortcuts.asStateFlow()

    private val _lastUrl = MutableStateFlow(prefs.getString(KEY_LAST, null))
    val lastUrl: StateFlow<String?> = _lastUrl.asStateFlow()

    // ---------------- Abas (como no Chrome) ----------------

    private val _tabs = MutableStateFlow(loadTabs())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _currentTab = MutableStateFlow(prefs.getString(KEY_CURRENT_TAB, null)?.takeIf { id -> _tabs.value.any { it.id == id } })
    val currentTab: StateFlow<String?> = _currentTab.asStateFlow()

    fun current(): Tab? = _tabs.value.firstOrNull { it.id == _currentTab.value }

    /** Aba atual (cria uma com a página inicial se não houver nenhuma). */
    fun ensureCurrentTab(homeUrl: String): Tab = current() ?: _tabs.value.lastOrNull()?.also { selectTab(it.id) } ?: newTab(homeUrl)

    /** Abre o endereço numa aba nova (ou reaproveita a atual se já estiver nele). */
    fun openInNewTab(url: String): Tab {
        val cur = current()
        if (cur != null && same(cur.url, url)) return cur
        return newTab(url)
    }

    fun newTab(url: String): Tab {
        val tab = Tab(java.util.UUID.randomUUID().toString(), url, hostOf(url))
        val updated = (_tabs.value + tab).takeLast(MAX_TABS)
        _tabs.value = updated
        saveTabs(updated)
        selectTab(tab.id)
        return tab
    }

    fun selectTab(id: String) {
        _currentTab.value = id
        prefs.edit().putString(KEY_CURRENT_TAB, id).apply()
    }

    fun updateCurrentTab(url: String, title: String?) {
        val id = _currentTab.value ?: return
        val updated = _tabs.value.map { if (it.id == id) it.copy(url = url, title = title?.ifBlank { null } ?: hostOf(url)) else it }
        _tabs.value = updated
        saveTabs(updated)
    }

    /** Fecha a aba; devolve a aba que fica aberta no lugar (ou null se não sobrou nenhuma). */
    fun closeTab(id: String): Tab? {
        val list = _tabs.value
        val index = list.indexOfFirst { it.id == id }
        val updated = list.filterNot { it.id == id }
        _tabs.value = updated
        saveTabs(updated)
        if (_currentTab.value == id) {
            val next = updated.getOrNull(index.coerceAtMost(updated.lastIndex)) ?: updated.lastOrNull()
            if (next != null) selectTab(next.id) else {
                _currentTab.value = null
                prefs.edit().remove(KEY_CURRENT_TAB).apply()
            }
            return next
        }
        return current()
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        saveTabs(emptyList())
        _currentTab.value = null
        prefs.edit().remove(KEY_CURRENT_TAB).apply()
    }

    private fun loadTabs(): List<Tab> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_TABS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Tab(o.getString("id"), o.getString("u"), o.optString("t"))
        }
    }.getOrDefault(emptyList())

    private fun saveTabs(list: List<Tab>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("u", it.url).put("t", it.title)) }
        prefs.edit().putString(KEY_TABS, arr.toString()).apply()
    }

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
        private const val KEY_TABS = "tabs"
        private const val KEY_CURRENT_TAB = "current_tab"
        private const val MAX_TABS = 30
        const val HOME_URL = "https://www.google.com/"
        const val TABS_URL = "musibox://tabs"

        /** O que a pessoa digitou: endereço vira site, o resto vira pesquisa no Google. */
        fun searchOrUrl(text: String): String {
            val t = text.trim()
            val url = LinkUtils.extractUrl(t)
            return when {
                url != null -> url
                LinkUtils.looksLikeAddress(t) -> LinkUtils.normalizeAddress(t)
                else -> "https://www.google.com/search?q=" + android.net.Uri.encode(t)
            }
        }
        private const val MAX_HISTORY = 80

        @Volatile private var instance: BrowserStore? = null

        fun get(context: Context): BrowserStore =
            instance ?: synchronized(this) { instance ?: BrowserStore(context.applicationContext).also { instance = it } }

        fun hostOf(url: String): String = runCatching { android.net.Uri.parse(url).host.orEmpty().removePrefix("www.").removePrefix("m.") }.getOrDefault(url)
    }
}
