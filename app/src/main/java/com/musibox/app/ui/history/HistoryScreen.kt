package com.musibox.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.contentUri
import com.musibox.app.media.AudioCover
import com.musibox.app.storage.DeleteResult
import com.musibox.app.ui.components.AddToPlaylistDialog
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.ConfirmWithOptionDialog
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.TrackRow
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.theme.Mb
import kotlinx.coroutines.launch

private enum class PlayFilter(val label: String, val days: Int) { ALL("Tudo", 0), TODAY("Hoje", 1), WEEK("7 dias", 7), MONTH("30 dias", 30) }

@Composable
fun HistoryScreen(nav: NavController, initialTab: Int) {
    val container = LocalAppContainer.current
    val plays by container.history.recentPlays(1000).collectAsStateWithLifecycle(initialValue = null)
    val player by container.player.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    @Suppress("UNUSED_VARIABLE") val unusedTab = initialTab
    val tab = 0
    var query by rememberSaveable { mutableStateOf("") }
    var playFilter by rememberSaveable { mutableStateOf(PlayFilter.ALL) }
    var clearDialog by remember { mutableStateOf(false) }
    var playlistSong by remember { mutableStateOf<Song?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        MbTopBar("Histórico", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { clearDialog = true }) { Icon(Icons.Rounded.DeleteSweep, "Limpar histórico") }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Pesquisar no histórico") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Limpar") } },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Mb.colors.card,
                unfocusedContainerColor = Mb.colors.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PlayFilter.entries) { f -> FilterChip(selected = playFilter == f, onClick = { playFilter = f }, label = { Text(f.label) }) }
        }

        val q = SongKeys.normalize(query)
        if (tab == 0) {
            val list = plays
            val now = System.currentTimeMillis()
            val filtered = list?.filter { item ->
                (playFilter.days == 0 || now - item.entry.playedAt <= playFilter.days * 86_400_000L) &&
                    (q.isBlank() || SongKeys.normalize(item.entry.title).contains(q) || SongKeys.normalize(item.entry.artist).contains(q))
            }
            when {
                filtered == null -> LoadingState("Carregando…")
                filtered.isEmpty() -> StateMessage(Icons.Rounded.History, "Nenhuma reprodução encontrada", "As músicas que você tocar aparecem aqui.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    var lastHeader = ""
                    filtered.forEach { entryItem ->
                        val header = Fmt.dayHeader(entryItem.entry.playedAt)
                        if (header != lastHeader) {
                            lastHeader = header
                            item(key = "h_${entryItem.entry.id}") {
                                Text(header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
                            }
                        }
                        item(key = entryItem.entry.id) {
                            val song = entryItem.song
                            TrackRow(
                                title = entryItem.entry.title,
                                subtitle = if (song != null) "${SongKeys.displayArtist(entryItem.entry.artist)} • ${Fmt.dateTime(entryItem.entry.playedAt)}"
                                else "Arquivo não encontrado neste dispositivo.",
                                artModel = song?.let { AudioCover(it.contentUri()) },
                                available = song != null,
                                isCurrent = song != null && player.current?.songId == song.id,
                                isPlaying = player.isPlaying,
                                modifier = Modifier.padding(horizontal = 6.dp),
                                onClick = {
                                    if (song != null) container.player.playSong(song) else snack("Arquivo não encontrado neste dispositivo.")
                                },
                                menu = buildList {
                                    if (song != null) add(MenuAction("Adicionar à playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { playlistSong = song })
                                    add(MenuAction("Remover do histórico", Icons.Rounded.DeleteOutline) {
                                        scope.launch { container.history.remove(entryItem.entry.id) }
                                    })
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (clearDialog) {
        if (tab == 0) {
            ConfirmDialog(
                title = "Limpar histórico de reproduções?",
                message = "O histórico será apagado. Suas músicas continuam no aparelho.",
                confirmText = "Limpar",
                onConfirm = {
                    clearDialog = false
                    scope.launch {
                        container.history.clearAll()
                        snack("Histórico de reproduções limpo.")
                    }
                },
                onDismiss = { clearDialog = false },
            )
        }
    }
    playlistSong?.let { AddToPlaylistDialog(listOf(it)) { playlistSong = null } }
}
