package com.musibox.app.ui.library

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.contentUri
import com.musibox.app.storage.DeleteResult
import com.musibox.app.storage.MediaStoreSaver
import com.musibox.app.themes.AppTheme
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.rememberSnack
import kotlinx.coroutines.launch

/** Pedido de "Excluir música" vindo do menu de qualquer lista. Quem mostra a confirmação é o [SongDeleteHost]. */
object SongDeletion {
    var asking by mutableStateOf<List<Song>?>(null)
        private set

    fun ask(songs: List<Song>) {
        if (songs.isNotEmpty()) asking = songs
    }

    internal fun clear() {
        asking = null
    }
}

/**
 * Confirmação e exclusão do arquivo no aparelho. Fica uma vez só na raiz do app.
 * No Android 11+ o próprio sistema mostra a janela pedindo permissão para apagar.
 */
@Composable
fun SongDeleteHost() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val snack = rememberSnack()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<Song>>(emptyList()) }

    fun done(songs: List<Song>) {
        snack(if (songs.size == 1) "Música apagada do celular." else "${songs.size} músicas apagadas do celular.")
        container.player.removeSongs(songs.map { it.id }.toSet())
        scope.launch { runCatching { container.music.rescan() } }
        pending = emptyList()
    }

    lateinit var run: (List<Song>) -> Unit
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val songs = pending
        if (res.resultCode == Activity.RESULT_OK && songs.isNotEmpty()) {
            // Android 11+: o sistema já apagou. No Android 10, tenta de novo depois da permissão.
            if (Build.VERSION.SDK_INT >= 30) done(songs) else run(songs)
        } else {
            pending = emptyList()
        }
    }
    run = { songs ->
        when (val r = MediaStoreSaver.delete(context, songs.map { it.contentUri() })) {
            DeleteResult.Deleted -> done(songs)
            is DeleteResult.NeedsUserConfirmation -> {
                pending = songs
                runCatching { launcher.launch(IntentSenderRequest.Builder(r.intentSender).build()) }
                    .onFailure { pending = emptyList(); snack("Não foi possível apagar a música.") }
            }
            is DeleteResult.Failed -> snack(r.message)
        }
    }

    val asking = SongDeletion.asking ?: return
    val n = asking.size
    AlertDialog(
        onDismissRequest = { SongDeletion.clear() },
        title = { Text(if (n == 1) "Excluir música?" else "Excluir $n músicas?") },
        text = {
            Text(
                (if (n == 1) "\"${asking.first().title}\" será apagada deste celular. " else "As músicas serão apagadas deste celular. ") +
                    "Isso não pode ser desfeito.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                SongDeletion.clear()
                run(asking)
            }) { Text("Excluir", color = Color(0xFFFF5A5A)) }
        },
        dismissButton = { TextButton(onClick = { SongDeletion.clear() }) { Text("Cancelar") } },
        containerColor = AppTheme.card,
    )
}
