package com.musibox.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.Song
import com.musibox.app.data.prefs.LibrarySort
import com.musibox.app.data.repo.ScanState
import com.musibox.app.data.repo.TrackRef
import com.musibox.app.data.repo.contentUri
import com.musibox.app.media.AudioCover
import com.musibox.app.ui.components.AddToPlaylistDialog
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.TextInputDialog
import com.musibox.app.ui.components.TrackRow
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberAudioPermission
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb

private val tabs = listOf("Músicas", "Artistas", "Álbuns", "Pastas", "Playlists", "Favoritos", "Recentes", "Mais tocadas")

@Composable
fun LibraryScreen(nav: NavController, initialTab: Int) {
    val container = LocalAppContainer.current
    val vm = appViewModel { LibraryViewModel(it) }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(tabs.indices)) }
    var sortMenu by remember { mutableStateOf(false) }
    var playlistSongs by remember { mutableStateOf<List<Song>?>(null) }
    val permission = rememberAudioPermission(onGranted = { vm.rescan() })

    Column(Modifier.fillMaxSize()) {
        MbTopBar(
            title = "Músicas",
            subtitle = songs?.let { Fmt.plural(it.size, "música", "músicas") },
        ) {
            MbIconButton(Icons.Rounded.Search, "Pesquisar", { nav.navigate(Routes.SEARCH) })
            Box {
                MbIconButton(Icons.AutoMirrored.Rounded.Sort, "Ordenar", { sortMenu = true })
                DropdownMenu(sortMenu, { sortMenu = false }, containerColor = Mb.colors.cardHigh) {
                    LibrarySort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    s.label,
                                    color = if (s == sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = { sortMenu = false; vm.setSort(s) },
                        )
                    }
                }
            }
            MbIconButton(Icons.Rounded.Refresh, "Procurar músicas novamente", { vm.rescan() }, enabled = permission.granted)
        }

        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val needsSongs = tab !in setOf(4, 5)
        when {
            needsSongs && !permission.granted -> PermissionNeeded(permission.permanentlyDenied, permission.request, permission.openSettings)
            needsSongs && songs == null -> LoadingState("Carregando biblioteca…")
            needsSongs && songs!!.isEmpty() -> when (scan) {
                is ScanState.Scanning -> LoadingState("Procurando músicas no aparelho…")
                is ScanState.Error -> StateMessage(
                    Icons.Rounded.MusicOff, "Não foi possível ler suas músicas.", (scan as ScanState.Error).message,
                    actionText = "Tentar novamente", onAction = { vm.rescan() },
                )
                else -> StateMessage(
                    Icons.Rounded.MusicOff,
                    "Nenhuma música encontrada.",
                    "Baixe músicas pelo MusiBox ou copie arquivos de áudio para o aparelho.",
                    actionText = "Procurar novamente",
                    onAction = { vm.rescan() },
                    secondaryText = "Ir para Baixar",
                    onSecondary = { nav.navigate(Routes.download()) },
                )
            }
            else -> when (tab) {
                0 -> SongsTab(songs.orEmpty(), vm, player, nav) { playlistSongs = it }
                1 -> ArtistsTab(songs.orEmpty(), nav)
                2 -> AlbumsTab(songs.orEmpty(), nav)
                3 -> FoldersTab(songs.orEmpty(), nav)
                4 -> PlaylistsTab(vm, nav)
                5 -> FavoritesTab(vm, player, nav) { playlistSongs = it }
                6 -> SongsTab(songs.orEmpty().sortedByDescending { it.dateAddedMs }.take(100), vm, player, nav, showHeader = false) { playlistSongs = it }
                7 -> MostPlayedTab(vm, player, nav) { playlistSongs = it }
            }
        }
    }

    playlistSongs?.let { AddToPlaylistDialog(it) { playlistSongs = null } }
}

@Composable
fun PermissionNeeded(permanentlyDenied: Boolean, onRequest: () -> Unit, onSettings: () -> Unit) {
    StateMessage(
        Icons.Rounded.LibraryMusic,
        "Permissão necessária para acessar suas músicas.",
        if (permanentlyDenied) "O acesso foi negado. Ative \"Músicas e áudio\" nas configurações do Android para ver sua biblioteca. O cofre e os downloads continuam funcionando."
        else "O MusiBox lê somente os arquivos de áudio do aparelho para montar sua biblioteca. Nada é enviado para a internet.",
        actionText = if (permanentlyDenied) "Abrir configurações" else "Permitir acesso",
        onAction = if (permanentlyDenied) onSettings else onRequest,
    )
}

@Composable
private fun PlayAllHeader(count: Int, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderButton(Icons.Rounded.PlayArrow, "Tocar tudo", Modifier.weight(1f), primary = true, onClick = onPlay)
        HeaderButton(Icons.Rounded.Shuffle, "Aleatório", Modifier.weight(1f), onClick = onShuffle)
    }
    Text(
        Fmt.plural(count, "música", "músicas"),
        Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (primary) MaterialTheme.colorScheme.primary else Mb.colors.card)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) Color.White else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    vm: LibraryViewModel,
    player: com.musibox.app.media.PlayerState,
    nav: NavController,
    showHeader: Boolean = true,
    onAddToPlaylist: (List<Song>) -> Unit,
) {
    val container = LocalAppContainer.current
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (showHeader) {
            item {
                PlayAllHeader(
                    songs.size,
                    onPlay = { container.player.playSongs(songs, 0) },
                    onShuffle = { container.player.playSongs(songs, 0, shuffle = true) },
                )
            }
        }
        items(songs, key = { it.id }) { song ->
            SongItem(song, songs, player, song.contentKey in favorites, nav, { onAddToPlaylist(listOf(it)) })
        }
    }
}

@Composable
private fun ArtistsTab(songs: List<Song>, nav: NavController) {
    val container = LocalAppContainer.current
    val artists = remember(songs) { container.music.artists(songs) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(artists, key = { it.name }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.navigate(Routes.songs("artist", a.name)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(AudioCover(a.sample.contentUri()), Modifier.size(54.dp).clip(CircleShape), corner = 27.dp, fallbackIcon = Icons.Rounded.Person)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.plural(a.songCount, "música", "músicas"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(songs: List<Song>, nav: NavController) {
    val container = LocalAppContainer.current
    val albums = remember(songs) { container.music.albums(songs) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { nav.navigate(Routes.songs("album", album.id.toString())) },
            ) {
                Artwork(
                    AudioCover(album.sample.contentUri()),
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    corner = 16.dp,
                    fallbackIcon = Icons.Rounded.Album,
                )
                Spacer(Modifier.height(8.dp))
                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FoldersTab(songs: List<Song>, nav: NavController) {
    val container = LocalAppContainer.current
    val folders = remember(songs) { container.music.folders(songs) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(folders, key = { it.path }) { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.navigate(Routes.songs("folder", f.path)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${f.path.ifBlank { "/" }} • ${Fmt.plural(f.songCount, "música", "músicas")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(vm: LibraryViewModel, nav: NavController) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val list = playlists
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            MbCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                onClick = { creating = true },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Nova playlist", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        when {
            list == null -> item { LoadingState("Carregando playlists…") }
            list.isEmpty() -> item {
                StateMessage(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    "Nenhuma playlist ainda",
                    "Crie uma playlist e adicione músicas pelo menu de cada música.",
                )
            }
            else -> items(list, key = { it.playlist.id }) { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate(Routes.playlist(p.playlist.id)) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Mb.colors.accentGradient),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.plural(p.count, "música", "músicas"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Nova playlist",
            initial = "",
            label = "Nome da playlist",
            confirmText = "Criar",
            onConfirm = { name ->
                creating = false
                vm.createPlaylist(name) { id -> nav.navigate(Routes.playlist(id)) }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun FavoritesTab(
    vm: LibraryViewModel,
    player: com.musibox.app.media.PlayerState,
    nav: NavController,
    onAddToPlaylist: (List<Song>) -> Unit,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val list = favorites
    when {
        list == null -> LoadingState("Carregando favoritos…")
        list.isEmpty() -> StateMessage(
            Icons.Rounded.FavoriteBorder,
            "Nenhum favorito ainda",
            "Toque no coração no player ou use o menu de uma música para favoritar.",
        )
        else -> TrackRefList(list, player, nav, onAddToPlaylist)
    }
}

@Composable
fun TrackRefList(
    tracks: List<TrackRef>,
    player: com.musibox.app.media.PlayerState,
    nav: NavController,
    onAddToPlaylist: (List<Song>) -> Unit,
) {
    val container = LocalAppContainer.current
    val snack = rememberSnack()
    val available = tracks.mapNotNull { it.song }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (available.isNotEmpty()) {
            item {
                PlayAllHeader(
                    available.size,
                    onPlay = { container.player.playSongs(available, 0) },
                    onShuffle = { container.player.playSongs(available, 0, shuffle = true) },
                )
            }
        }
        items(tracks, key = { it.songKey }) { t ->
            val song = t.song
            if (song != null) {
                SongItem(song, available, player, true, nav, { onAddToPlaylist(listOf(it)) })
            } else {
                TrackRow(
                    title = t.title,
                    subtitle = "Arquivo não encontrado neste dispositivo.",
                    artModel = null,
                    available = false,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    onClick = { snack("Arquivo não encontrado neste dispositivo.") },
                )
            }
        }
    }
}

@Composable
private fun MostPlayedTab(
    vm: LibraryViewModel,
    player: com.musibox.app.media.PlayerState,
    nav: NavController,
    onAddToPlaylist: (List<Song>) -> Unit,
) {
    val most by vm.mostPlayed.collectAsStateWithLifecycle()
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    val snack = rememberSnack()
    val list = most
    when {
        list == null -> LoadingState("Carregando…")
        list.isEmpty() -> StateMessage(Icons.AutoMirrored.Rounded.TrendingUp, "Nada por aqui ainda", "As músicas que você mais ouvir aparecem aqui.")
        else -> {
            val available = list.mapNotNull { it.song }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { it.stat.songKey }) { m ->
                    val song = m.song
                    val plays = Fmt.plural(m.stat.playCount, "reprodução", "reproduções")
                    if (song != null) {
                        SongItem(
                            song, available, player, song.contentKey in favorites, nav, { onAddToPlaylist(listOf(it)) },
                            subtitle = "${SongKeys.displayArtist(song.artist)} • $plays",
                        )
                    } else {
                        TrackRow(
                            title = m.stat.title,
                            subtitle = "Arquivo não encontrado neste dispositivo. • $plays",
                            artModel = null,
                            available = false,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            onClick = { snack("Arquivo não encontrado neste dispositivo.") },
                        )
                    }
                }
            }
        }
    }
}
