package com.musibox.app.ui.vault

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musibox.app.AppContainer
import com.musibox.app.data.db.VaultItemEntity
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.storage.DeleteResult
import com.musibox.app.storage.MediaStoreSaver
import com.musibox.app.ui.components.ExternalActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Progress(val done: Int, val total: Int, val label: String)

class VaultViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<VaultItemEntity>?> = c.vault.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    var progress by mutableStateOf<Progress?>(null)
        private set

    fun expectExternal() = c.vaultLock.expectExternalActivity()
    fun lockNow() = c.vaultLock.lock()

    /** Copia os arquivos escolhidos para o cofre e confirma cada um. */
    fun import(uris: List<Uri>, folder: String, onDone: (imported: List<Uri>, message: String) -> Unit) {
        viewModelScope.launch {
            val ok = ArrayList<Uri>()
            var lastError: String? = null
            uris.forEachIndexed { i, uri ->
                progress = Progress(i, uris.size, "Salvando no cofre…")
                try {
                    val item = c.vault.importUri(uri, source = "import", folder = folder)
                    c.downloads.logImport(item.name, item.mimeType, item.size, SaveDestination.VAULT, null, item.id, null)
                    ok += uri
                } catch (e: Exception) {
                    lastError = e.message ?: "Formato não suportado."
                }
            }
            progress = null
            val msg = when {
                ok.size == uris.size -> if (ok.size == 1) "Arquivo salvo no cofre." else "${ok.size} arquivos salvos no cofre."
                ok.isEmpty() -> lastError ?: "Não foi possível salvar no cofre."
                else -> "${ok.size} de ${uris.size} arquivos salvos no cofre. ${lastError.orEmpty()}"
            }
            onDone(ok, msg)
        }
    }

    /** Depois de copiar, pede ao Android para apagar os originais (o usuário confirma). */
    fun deleteOriginals(uris: List<Uri>): DeleteResult = MediaStoreSaver.deleteOriginals(c.app, uris)

    fun export(ids: List<String>, removeAfter: Boolean, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val folder = c.settings.snapshot().downloadFolder
            var ok = 0
            var lastError: String? = null
            ids.forEachIndexed { i, id ->
                progress = Progress(i, ids.size, if (removeAfter) "Removendo do cofre…" else "Exportando…")
                try {
                    c.vault.export(id, folder)
                    if (removeAfter) c.vault.delete(listOf(id))
                    ok++
                } catch (e: Exception) {
                    lastError = e.message
                }
            }
            progress = null
            c.music.requestRescan()
            onDone(
                when {
                    ok == ids.size && removeAfter -> if (ok == 1) "Removido do cofre e salvo no aparelho." else "$ok itens removidos do cofre e salvos no aparelho."
                    ok == ids.size -> if (ok == 1) "Exportado para o aparelho." else "$ok itens exportados para o aparelho."
                    else -> "$ok de ${ids.size} concluídos. ${lastError.orEmpty()}"
                },
            )
        }
    }

    fun delete(ids: List<String>, onDone: (String) -> Unit) {
        viewModelScope.launch {
            c.vault.delete(ids)
            onDone(if (ids.size == 1) "Excluído permanentemente." else "${ids.size} itens excluídos permanentemente.")
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { c.vault.rename(id, name) }
    }

    fun move(ids: List<String>, folder: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            c.vault.move(ids, folder)
            onDone(if (folder.isBlank()) "Movido para \"Sem pasta\"." else "Movido para \"$folder\".")
        }
    }

    /** Prepara uma cópia temporária e entrega a outro app (abrir/compartilhar). */
    fun handOff(id: String, share: Boolean, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                progress = Progress(0, 1, "Preparando arquivo…")
                val item = c.vault.get(id) ?: throw IllegalStateException("Arquivo não encontrado.")
                val file = c.vault.decryptToTemp(id)
                val uri = ExternalActions.fileUri(c.app, file)
                progress = null
                c.vaultLock.expectExternalActivity()
                val ok = if (share) ExternalActions.shareContent(c.app, listOf(uri), item.mimeType)
                else ExternalActions.openContent(c.app, uri, item.mimeType)
                if (!ok) onError("Nenhum aplicativo disponível para este arquivo.")
            } catch (e: Exception) {
                progress = null
                onError(e.message ?: "Não foi possível abrir o arquivo.")
            }
        }
    }
}
