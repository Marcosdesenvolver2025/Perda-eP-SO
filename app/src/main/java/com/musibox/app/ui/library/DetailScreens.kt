package com.musibox.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.displayAlbum
import com.musibox.app.data.repo.displayArtist
import com.musibox.app.ui.components.AddToPlaylistDialog
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.OverflowMenu
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.TextInputDialog
import com.musibox.app.ui.components.TrackRow
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb
import kotlinx.coroutines.launch

/** Músicas de um artista, álbum ou pasta. */
@Composable
fun SongListScreen(nav: NavController, type: String, value: String) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "lib") { LibraryViewModel(it) }
    val all by vm.songs.collectAsStateWithLifecycle()
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    var playlistSongs by remember { mutableStateOf<List<Song>?>(null) }

    val songs = remember(all, type, value) {
        all?.filter {
            when (type) {
                "artist" -> it.displayArtist() == value
                "album" -> it.albumId.toString() == value
                "folder" -> it.folderPath == value
                else -> false
            }
        }?.let { list -> if (type == "artist") list.sortedBy { it.title.lowercase() } else list }
    }
    val title = when (type) {
        "album" -> songs?.firstOrNull()?.displayAlbum() ?: "Álbum"
        "folder" -> value.substringAfterLast('/').ifBlank { "Pasta" }
        else -> value
    }

    Column(Modifier.fillMaxSize()) {
        MbTopBar(title, onBack = { nav.popBackStack() }, subtitle = songs?.let { Fmt.plural(it.size, "música", "músicas") })
        val list = songs
        when {
            list == null -> LoadingState("Carregando…")
            list.isEmpty() -> StateMessage(Icons.Rounded.MusicOff, "Nenhuma música encontrada.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HeaderButton(Icons.Rounded.PlayArrow, "Tocar tudo", Modifier.weight(1f), primary = true) {
                            container.player.playSongs(list, 0)
                        }
                        HeaderButton(Icons.Rounded.Shuffle, "Aleatório", Modifier.weight(1f)) {
                            container.player.playSongs(list, 0, shuffle = true)
                        }
                    }
                }
                items(list, key = { it.id }) { song ->
                    SongItem(song, list, player, song.contentKey in favorites, if (type == "artist") null else nav, { playlistSongs = listOf(it) })
                }
            }
        }
    }
    playlistSongs?.let { AddToPlaylistDialog(it) { playlistSongs = null } }
}

@Composable
fun PlaylistScreen(nav: NavController, id: String) {
    val container = LocalAppContainer.current
    val playlist by container.library.playlist(id).collectAsStateWithLifecycle(initialValue = null)
    val tracks by container.library.playlistTracks(id).collectAsStateWithLifecycle(initialValue = null)
    val vm = appViewModel(key = "lib") { LibraryViewModel(it) }
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var playlistSongs by remember { mutableStateOf<List<Song>?>(null) }

    Column(Modifier.fillMaxSize()) {
        MbTopBar(
            playlist?.name ?: "Playlist",
            onBack = { nav.popBackStack() },
            subtitle = tracks?.let { Fmt.plural(it.size, "música", "músicas") },
        ) {
            OverflowMenu(
                listOf(
                    MenuAction("Renomear", Icons.Rounded.Edit) { renaming = true },
                    MenuAction("Excluir playlist", Icons.Rounded.DeleteOutline, destructive = true) { deleting = true },
                ),
            )
        }
        val list = tracks
        when {
            list == null -> LoadingState("Carregando…")
            list.isEmpty() -> StateMessage(
                Icons.AutoMirrored.Rounded.QueueMusic,
                "Playlist vazia",
                "Adicione músicas pelo menu ⋮ de cada música em Músicas.",
                actionText = "Ir para Músicas",
                onAction = { nav.navigate(Routes.library()) },
            )
            else -> {
                val available = list.mapNotNull { it.track.song }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (available.isNotEmpty()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HeaderButton(Icons.Rounded.PlayArrow, "Tocar", Modifier.weight(1f), primary = true) {
                                    container.player.playSongs(available, 0)
                                }
                                HeaderButton(Icons.Rounded.Shuffle, "Aleatório", Modifier.weight(1f)) {
                                    container.player.playSongs(available, 0, shuffle = true)
                                }
                            }
                        }
                    }
                    if (available.size < list.size) {
                        item {
                            Text(
                                "${list.size - available.size} música(s) desta playlist não estão neste aparelho. " +
                                    "Elas voltam a tocar quando o arquivo for encontrado.",
                                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Mb.colors.warning,
                            )
                        }
                    }
                    itemsIndexed(list, key = { _, t -> t.item.id }) { index, t ->
                        val song = t.track.song
                        val moveMenu = buildList {
                            if (index > 0) add(MenuAction("Mover para cima", Icons.Rounded.ArrowUpward) {
                                scope.launch { container.library.moveInPlaylist(id, index, index - 1) }
                            })
                            if (index < list.lastIndex) add(MenuAction("Mover para baixo", Icons.Rounded.ArrowDownward) {
                                scope.launch { container.library.moveInPlaylist(id, index, index + 1) }
                            })
                            add(MenuAction("Remover da playlist", Icons.Rounded.RemoveCircleOutline, destructive = true) {
                                scope.launch { container.library.removeFromPlaylist(id, t.item.id) }
                            })
                        }
                        if (song != null) {
                            SongItem(song, available, player, song.contentKey in favorites, nav, { playlistSongs = listOf(it) }, extraMenu = moveMenu)
                        } else {
                            TrackRow(
                                title = t.track.title,
                                subtitle = "Arquivo não encontrado neste dispositivo.",
                                artModel = null,
                                available = false,
                                modifier = Modifier.padding(horizontal = 6.dp),
                                onClick = { snack("Arquivo não encontrado neste dispositivo.") },
                                menu = moveMenu,
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog("Renomear playlist", playlist?.name.orEmpty(), "Nome", "Salvar", onConfirm = { name ->
            renaming = false
            scope.launch { container.library.renamePlaylist(id, name) }
        }, onDismiss = { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(
            "Excluir playlist?",
            "A playlist \"${playlist?.name.orEmpty()}\" será excluída. As músicas continuam no aparelho.",
            "Excluir",
            onConfirm = {
                deleting = false
                scope.launch {
                    container.library.deletePlaylist(id)
                    nav.popBackStack()
                }
            },
            onDismiss = { deleting = false },
            destructive = true,
        )
    }
    playlistSongs?.let { AddToPlaylistDialog(it) { playlistSongs = null } }
}

/** Pesquisa instantânea na biblioteca. */
@Composable
fun SearchScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "lib") { LibraryViewModel(it) }
    val all by vm.songs.collectAsStateWithLifecycle()
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var playlistSongs by remember { mutableStateOf<List<Song>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val normalized = SongKeys.normalize(query)
    val results = remember(all, normalized) {
        if (normalized.isBlank()) emptyList()
        else all.orEmpty().filter {
            SongKeys.normalize(it.title).contains(normalized) ||
                SongKeys.normalize(it.artist).contains(normalized) ||
                SongKeys.normalize(it.album).contains(normalized)
        }
    }
    val artists = remember(results) { results.map { it.displayArtist() }.distinct().take(8) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MbIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", { nav.popBackStack() }, background = Color.Transparent)
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Músicas, artistas ou álbuns") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Limpar") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Mb.colors.card,
                    unfocusedContainerColor = Mb.colors.card,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (query.isNotBlank()) {
                item {
                    MbCard(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        onClick = { nav.navigate(Routes.download(tab = 1, url = query.trim())) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.size(12.dp))
                            Text("Buscar \"${query.trim()}\" para baixar", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (artists.isNotEmpty()) {
                item {
                    Text("Artistas", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
                }
                items(artists, key = { "a_$it" }) { a ->
                    TrackRow(
                        title = a,
                        subtitle = "Artista",
                        artModel = null,
                        modifier = Modifier.padding(horizontal = 6.dp),
                        onClick = { nav.navigate(Routes.songs("artist", a)) },
                    )
                }
            }
            if (results.isNotEmpty()) {
                item {
                    Text("Músicas", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
                }
                items(results.take(200), key = { it.id }) { song ->
                    SongItem(song, results, player, song.contentKey in favorites, nav, { playlistSongs = listOf(it) })
                }
            }
            if (query.isNotBlank() && results.isEmpty() && all != null) {
                item { StateMessage(Icons.Rounded.SearchOff, "Nada encontrado para \"${query.trim()}\"", "Tente outro nome ou busque para baixar.") }
            }
            if (query.isBlank()) {
                item {
                    Column(Modifier.padding(top = 40.dp)) {
                        StateMessage(Icons.Rounded.Search, "Pesquise na sua biblioteca", "Digite o nome de uma música, artista ou álbum.")
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
    playlistSongs?.let { AddToPlaylistDialog(it) { playlistSongs = null } }
}
