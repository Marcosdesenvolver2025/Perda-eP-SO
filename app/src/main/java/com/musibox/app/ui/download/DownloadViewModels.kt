package com.musibox.app.ui.download

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musibox.app.AppContainer
import com.musibox.app.data.db.DownloadEntity
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.data.prefs.AudioFormat
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.download.DownloadOption
import com.musibox.app.download.FormatParser
import com.musibox.app.download.LinkUtils
import com.musibox.app.download.MediaInfo
import com.musibox.app.download.OptionKind
import com.musibox.app.download.SearchResult
import com.musibox.app.storage.DeleteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalyzeState {
    data object Idle : AnalyzeState
    data class Loading(val url: String, val firstRun: Boolean) : AnalyzeState
    data class Ready(val info: MediaInfo) : AnalyzeState
    data class Error(
        val url: String?,
        val message: String,
        val offline: Boolean = false,
        val detail: String? = null,
    ) : AnalyzeState
}

/** Lê um link e prepara o download (usado na tela Baixar e no menu Compartilhar). */
class LinkDownloadViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<AnalyzeState>(AnalyzeState.Idle)
    val state: StateFlow<AnalyzeState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Etapa do motor ("Atualizando o motor…", "Lendo o link…"). */
    val stage: StateFlow<String?> = c.ytdlp.stage

    var link by mutableStateOf("")
    var selected by mutableStateOf<DownloadOption?>(null)
    var destination by mutableStateOf<SaveDestination?>(null)
    var fileName by mutableStateOf("")
    var enqueueing by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var lastUrl: String? = null

    fun analyze(raw: String, preferAudio: Boolean = false) {
        val url = LinkUtils.extractUrl(raw)
        if (url == null) {
            _state.value = AnalyzeState.Error(null, "Link inválido. Copie o link completo do vídeo ou música.")
            return
        }
        link = url
        lastUrl = url
        job?.cancel()
        _state.value = AnalyzeState.Loading(url, firstRun = !c.ytdlp.isReady)
        job = viewModelScope.launch {
            if (!c.connectivity.isOnlineNow()) {
                _state.value = AnalyzeState.Error(url, "Sem conexão com a internet.", offline = true)
                return@launch
            }
            try {
                val s = c.settings.snapshot()
                val info = c.ytdlp.fetchInfo(url, s.audioBitrate)
                fileName = info.title
                selected = FormatParser.defaultOption(info, preferAudio, s.videoQuality, s.audioFormat == AudioFormat.MP3)
                destination = if (s.askDestination) null else s.defaultDestination
                _state.value = AnalyzeState.Ready(info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AnalyzeState.Error(
                    url,
                    e.message ?: "Não foi possível ler este link.",
                    detail = (e as? com.musibox.app.download.DownloadError)?.detail,
                )
            }
        }
    }

    fun retry() {
        lastUrl?.let { analyze(it) }
    }

    fun reset() {
        job?.cancel()
        link = ""
        selected = null
        fileName = ""
        _state.value = AnalyzeState.Idle
    }

    /** Escolhe a melhor opção de um tipo (áudio ou vídeo) conforme as preferências. */
    fun chooseKind(kind: OptionKind) {
        val info = (state.value as? AnalyzeState.Ready)?.info ?: return
        val s = settings.value
        selected = FormatParser.defaultOption(info, kind == OptionKind.AUDIO, s.videoQuality, s.audioFormat == AudioFormat.MP3)
    }

    fun enqueue(onDone: (String) -> Unit, onError: (String) -> Unit) {
        val info = (state.value as? AnalyzeState.Ready)?.info ?: return
        val option = selected ?: return onError("Escolha um formato.")
        val dest = destination ?: return onError("Escolha onde salvar.")
        enqueueing = true
        viewModelScope.launch {
            try {
                c.downloads.enqueue(info, option, fileName.ifBlank { info.title }, dest)
                val where = if (dest == SaveDestination.VAULT) "no cofre" else "no aparelho"
                onDone("Download iniciado. O arquivo será salvo $where.")
            } catch (e: Exception) {
                onError(e.message ?: "Não foi possível iniciar o download.")
            } finally {
                enqueueing = false
            }
        }
    }
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val query: String, val items: List<SearchResult>) : SearchState
    data class Error(val message: String) : SearchState
}

/** Gerenciador de downloads, pesquisa e importação de arquivos. */
class DownloadCenterViewModel(private val c: AppContainer) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>?> =
        c.downloads.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val online: StateFlow<Boolean> = c.connectivity.online

    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search.asStateFlow()
    var query by mutableStateOf("")
    private var searchJob: Job? = null

    /** Último link da área de transferência que o usuário já viu/dispensou. */
    var handledClip by mutableStateOf<String?>(null)

    fun clearSearch() {
        searchJob?.cancel()
        query = ""
        _search.value = SearchState.Idle
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        _search.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            if (!c.connectivity.isOnlineNow()) {
                _search.value = SearchState.Error("Sem conexão com a internet.")
                return@launch
            }
            try {
                _search.value = SearchState.Results(q, c.ytdlp.search(q))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.value = SearchState.Error(e.message ?: "Não foi possível pesquisar.")
            }
        }
    }

    fun pause(id: String) = viewModelScope.launch { c.downloads.pause(id) }
    fun resume(id: String) = viewModelScope.launch { c.downloads.resume(id) }
    fun cancel(id: String) = viewModelScope.launch { c.downloads.cancel(id) }
    fun retry(id: String) = viewModelScope.launch { c.downloads.retry(id) }

    fun remove(id: String, deleteFile: Boolean, onResult: (DeleteResult) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { c.downloads.remove(id, deleteFile) }
                .getOrElse { DeleteResult.Failed(it.message ?: "Não foi possível excluir.") }
            onResult(result)
        }
    }

    fun forgetRecord(id: String) = viewModelScope.launch { c.downloads.removeRecordOnly(id) }

    fun moveToVault(id: String, onResult: (Result<DeleteResult>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { c.downloads.moveToVault(id) }) }
    }

    fun importFiles(uris: List<Uri>, destination: SaveDestination, onResult: (String) -> Unit) {
        viewModelScope.launch {
            var ok = 0
            var lastError: String? = null
            var lastMessage = ""
            for (uri in uris) {
                try {
                    lastMessage = c.downloads.importLocal(uri, destination)
                    ok++
                } catch (e: Exception) {
                    lastError = e.message ?: "Formato não suportado."
                }
            }
            onResult(
                when {
                    ok == uris.size && ok == 1 -> lastMessage
                    ok == uris.size -> "$ok arquivos importados."
                    ok == 0 -> lastError ?: "Não foi possível importar."
                    else -> "$ok de ${uris.size} arquivos importados. ${lastError.orEmpty()}"
                },
            )
        }
    }
}
