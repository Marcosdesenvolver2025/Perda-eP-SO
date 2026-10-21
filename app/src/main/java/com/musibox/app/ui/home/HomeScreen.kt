package com.musibox.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.FolderSpecial
import com.musibox.app.core.AudioKinds
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.components.rememberIntroState
import com.musibox.app.ui.components.intro
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.musibox.app.AppContainer
import com.musibox.app.R
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.PlayHistoryItem
import com.musibox.app.data.repo.PlaylistSummary
import com.musibox.app.data.repo.TrackRef
import com.musibox.app.data.repo.contentUri
import com.musibox.app.data.repo.displayArtist
import com.musibox.app.media.AudioCover
import com.musibox.app.media.PlayerState
import com.musibox.app.ui.components.AddToPlaylistDialog
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.NeoChip
import com.musibox.app.ui.components.NeoSearchPill
import com.musibox.app.ui.components.NeoSectionTitle
import com.musibox.app.ui.components.OverflowMenu
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.TextInputDialog
import com.musibox.app.ui.components.TrackRow
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.neoGlass
import com.musibox.app.ui.components.rememberAudioPermission
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.library.LibraryViewModel
import com.musibox.app.ui.library.PermissionNeeded
import com.musibox.app.ui.library.SongItem
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.navigation.navigateTopLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val recent: StateFlow<List<PlayHistoryItem>?> = c.history.recentPlays(200)
        .map { list -> list.distinctBy { it.entry.songKey }.filter { it.song != null }.take(40) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs: StateFlow<List<Song>?> = combine(c.music.songs, c.settings.settings.map { it.librarySort }) { list, sort ->
        LibraryViewModel.sortSongs(list, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favorites: StateFlow<List<TrackRef>?> =
        c.library.favoriteTracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favoriteKeys: StateFlow<Set<String>> =
        c.library.favoriteKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val playlists: StateFlow<List<PlaylistSummary>?> =
        c.library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rescan() {
        viewModelScope.launch { c.music.rescan() }
    }

    fun createPlaylist(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(c.library.createPlaylist(name)) }
    }
}

private enum class HomeFilter { ALL, SINGERS, FAVORITES, PLAYLISTS, AUDIOS, RECENT }

/**
 * Início: busca, filtros (Todas, Favoritas, Playlists, Recentes), "Continue ouvindo",
 * "Tocadas recentemente" e a lista de músicas.
 */
@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm = appViewModel { HomeViewModel(it) }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val favoriteKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    val snack = rememberSnack()
    val permission = rememberAudioPermission(onGranted = { vm.rescan() })
    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }
    var playlistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val kinds = remember { AudioKinds.get(context) }
    val overrides by kinds.overrides.collectAsStateWithLifecycle()
    val everything = songs.orEmpty()
    // Músicas e áudios (aulas, gravações…) ficam separados
    val split = remember(everything, overrides) { everything.partition { kinds.isMusic(it, overrides) } }
    val allSongs = split.first
    val audioSongs = split.second
    val singerFolders = remember(allSongs) { buildSingerFolders(allSongs) }
    val recentSongs = recent.orEmpty().mapNotNull { it.song }

    val introTop = rememberIntroState()
    val intro = rememberIntroState(filter)

    // ---------- seleção múltipla (segurar uma música) ----------
    var selection by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selection.isNotEmpty()
    val toggleSelect: (Song) -> Unit = { s -> selection = if (s.id in selection) selection - s.id else selection + s.id }
    androidx.activity.compose.BackHandler(enabled = selecting) { selection = emptySet() }
    val favSongs = favorites.orEmpty().mapNotNull { it.song }
    val visibleSongs: List<Song> = when (filter) {
        HomeFilter.ALL -> allSongs
        HomeFilter.AUDIOS -> audioSongs
        HomeFilter.FAVORITES -> favSongs
        HomeFilter.RECENT -> recentSongs
        else -> emptyList()
    }
    val selectedSongs = (allSongs + audioSongs + favSongs + recentSongs).distinctBy { it.id }.filter { it.id in selection }
    LaunchedEffect(filter) { selection = emptySet() }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    fun runDelete(uris: List<android.net.Uri>, launch: (android.content.IntentSender) -> Unit) {
        when (val r = com.musibox.app.storage.MediaStoreSaver.delete(context, uris)) {
            com.musibox.app.storage.DeleteResult.Deleted -> {
                snack(if (uris.size == 1) "Música apagada." else "${uris.size} músicas apagadas.")
                selection = emptySet()
                pendingDelete = emptyList()
                vm.rescan()
            }
            is com.musibox.app.storage.DeleteResult.NeedsUserConfirmation -> {
                pendingDelete = uris
                launch(r.intentSender)
            }
            is com.musibox.app.storage.DeleteResult.Failed -> snack(r.message)
        }
    }
    lateinit var launchConfirm: (android.content.IntentSender) -> Unit
    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            // Android 11+: o próprio sistema apagou. No Android 10, tenta de novo os que faltam.
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                snack(if (pendingDelete.size == 1) "Música apagada." else "${pendingDelete.size} músicas apagadas.")
                selection = emptySet()
                pendingDelete = emptyList()
                vm.rescan()
            } else {
                runDelete(pendingDelete, launchConfirm)
            }
        } else {
            pendingDelete = emptyList()
        }
    }
    launchConfirm = { sender -> deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + com.musibox.app.ui.navigation.LocalBottomBarSpace.current),
        ) {
            item(key = "search") {
                NeoSearchPill(
                    "Buscar músicas, artistas, playlists...",
                    onClick = { nav.navigate(Routes.SEARCH) },
                    modifier = Modifier
                        .intro("search", 0, introTop)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            item(key = "chips") {
                LazyRow(
                    modifier = Modifier.intro("chips", 1, introTop),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { NeoChip("Todas", Icons.Rounded.GridView, filter == HomeFilter.ALL, { filter = HomeFilter.ALL }) }
                    item { NeoChip("Cantores", Icons.Outlined.Person, filter == HomeFilter.SINGERS, { filter = HomeFilter.SINGERS }) }
                    item { NeoChip("Favoritas", Icons.Rounded.Favorite, filter == HomeFilter.FAVORITES, { filter = HomeFilter.FAVORITES }) }
                    item { NeoChip("Playlists", Icons.AutoMirrored.Rounded.QueueMusic, filter == HomeFilter.PLAYLISTS, { filter = HomeFilter.PLAYLISTS }) }
                    item { NeoChip("Áudios", Icons.Rounded.GraphicEq, filter == HomeFilter.AUDIOS, { filter = HomeFilter.AUDIOS }) }
                    item { NeoChip("Recentes", Icons.Rounded.Schedule, filter == HomeFilter.RECENT, { filter = HomeFilter.RECENT }) }
                }
            }
            // Lista conforme o filtro escolhido
            when {
                filter != HomeFilter.PLAYLISTS && filter != HomeFilter.FAVORITES && !permission.granted -> item(key = "perm") {
                    PermissionNeeded(permission.permanentlyDenied, permission.request, permission.openSettings)
                }
                filter == HomeFilter.ALL -> {
                    item(key = "allTitle") {
                        ListHeader(
                            "Suas músicas",
                            songs?.let { Fmt.plural(allSongs.size, "música", "músicas") },
                            onShuffle = { if (allSongs.isNotEmpty()) container.player.playSongs(allSongs, 0, shuffle = true) },
                            onPlay = { if (allSongs.isNotEmpty()) container.player.playSongs(allSongs, 0) },
                            onMore = { nav.navigate(Routes.library()) },
                        )
                    }
                    when {
                        songs == null -> item(key = "loading") { LoadingState("Carregando suas músicas…") }
                        everything.isEmpty() -> item(key = "empty") {
                            StateMessage(
                                Icons.Rounded.MusicOff,
                                "Nenhuma música encontrada",
                                "Copie arquivos de áudio para o aparelho (pasta Música ou Download) e eles aparecem aqui.",
                                actionText = "Procurar novamente",
                                onAction = { vm.rescan() },
                            )
                        }
                        else -> itemsIndexed(allSongs, key = { _, it -> "s" + it.id }) { i, song ->
                            Box(Modifier.animateItem().intro("s" + song.id, i + 1, intro)) {
                                SongItem(song, allSongs, player, song.contentKey in favoriteKeys, nav, { playlistSongs = listOf(it) }, selected = if (selecting) song.id in selection else null, onToggleSelect = toggleSelect)
                            }
                        }
                    }
                }
                filter == HomeFilter.SINGERS -> {
                    item(key = "singTitle") {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)) {
                            Text("Cantores", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Uma pasta para cada cantor, criada sozinha • ${Fmt.plural(singerFolders.size, "pasta", "pastas")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Neo.Text2,
                            )
                        }
                    }
                    if (songs == null) {
                        item(key = "singLoading") { LoadingState("Organizando as pastas…") }
                    } else if (singerFolders.isEmpty()) {
                        item(key = "singEmpty") {
                            StateMessage(Icons.Rounded.FolderSpecial, "Nenhum cantor ainda", "As pastas aparecem quando houver músicas com o nome do artista.")
                        }
                    } else {
                        val rows = singerFolders.chunked(2)
                        itemsIndexed(rows, key = { _, r -> "sg" + r.first().name }) { i, row ->
                            Row(
                                Modifier
                                    .animateItem()
                                    .intro("sg" + row.first().name, i + 1, intro)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEachIndexed { j, f ->
                                    SingerFolderCard(f, i * 2 + j, Modifier.weight(1f)) { nav.navigate(Routes.songs("singer", f.name)) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                filter == HomeFilter.AUDIOS -> {
                    item(key = "audTitle") {
                        ListHeader(
                            "Áudios",
                            "Aulas, gravações e outros áudios • ${Fmt.plural(audioSongs.size, "áudio", "áudios")}",
                            onShuffle = { if (audioSongs.isNotEmpty()) container.player.playSongs(audioSongs, 0, shuffle = true) },
                            onPlay = { if (audioSongs.isNotEmpty()) container.player.playSongs(audioSongs, 0) },
                        )
                    }
                    if (audioSongs.isEmpty()) {
                        item(key = "audEmpty") {
                            StateMessage(
                                Icons.Rounded.GraphicEq,
                                "Nenhum áudio separado",
                                "Aulas, gravações e áudios de conversa aparecem aqui. Para mover uma faixa, use o menu ⋮ dela.",
                            )
                        }
                    } else {
                        itemsIndexed(audioSongs, key = { _, it -> "a" + it.id }) { i, song ->
                            Box(Modifier.animateItem().intro("a" + song.id, i + 1, intro)) {
                                SongItem(song, audioSongs, player, song.contentKey in favoriteKeys, nav, { playlistSongs = listOf(it) }, selected = if (selecting) song.id in selection else null, onToggleSelect = toggleSelect)
                            }
                        }
                    }
                }
                filter == HomeFilter.FAVORITES -> {
                    val favs = favorites
                    val available = favs.orEmpty().mapNotNull { it.song }
                    item(key = "favTitle") {
                        ListHeader(
                            "Favoritas",
                            favs?.let { Fmt.plural(it.size, "música", "músicas") },
                            onShuffle = { if (available.isNotEmpty()) container.player.playSongs(available, 0, shuffle = true) },
                            onPlay = { if (available.isNotEmpty()) container.player.playSongs(available, 0) },
                        )
                    }
                    when {
                        favs == null -> item(key = "favLoading") { LoadingState("Carregando favoritas…") }
                        favs.isEmpty() -> item(key = "favEmpty") {
                            StateMessage(
                                Icons.Rounded.FavoriteBorder,
                                "Nenhuma favorita ainda",
                                "Toque no coração no player ou use o menu ⋮ de uma música.",
                            )
                        }
                        else -> itemsIndexed(favs, key = { _, it -> "f" + it.songKey }) { i, t ->
                            val song = t.song
                            Box(Modifier.animateItem().intro("f" + t.songKey, i + 1, intro)) {
                            if (song != null) {
                                SongItem(song, available, player, true, nav, { playlistSongs = listOf(it) }, selected = if (selecting) song.id in selection else null, onToggleSelect = toggleSelect)
                            } else {
                                TrackRow(
                                    title = t.title,
                                    subtitle = "Arquivo não encontrado neste aparelho",
                                    artModel = null,
                                    available = false,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    onClick = { snack("Arquivo não encontrado neste aparelho.") },
                                )
                            }
                            }
                        }
                    }
                }
                filter == HomeFilter.PLAYLISTS -> {
                    item(key = "plTitle") {
                        NeoSectionTitle("Playlists", Modifier.padding(start = 16.dp, end = 10.dp, top = 18.dp, bottom = 6.dp))
                    }
                    item(key = "plNew") {
                        Box(Modifier.intro("plNew", 1, intro)) {
                            PlaylistRow("Nova playlist", "Crie e escolha as músicas de uma vez", isNew = true) { creatingPlaylist = true }
                        }
                    }
                    val pls = playlists
                    if (pls == null) {
                        item(key = "plLoading") { LoadingState("Carregando playlists…") }
                    } else {
                        itemsIndexed(pls, key = { _, it -> "p" + it.playlist.id }) { i, p ->
                            Box(Modifier.animateItem().intro("p" + p.playlist.id, i + 2, intro)) {
                                PlaylistRow(p.playlist.name, Fmt.plural(p.count, "música", "músicas")) { nav.navigate(Routes.playlist(p.playlist.id)) }
                            }
                        }
                    }
                }
                else -> {
                    item(key = "recTitle") {
                        ListHeader(
                            "Recentes",
                            Fmt.plural(recentSongs.size, "música", "músicas"),
                            onShuffle = { if (recentSongs.isNotEmpty()) container.player.playSongs(recentSongs, 0, shuffle = true) },
                            onPlay = { if (recentSongs.isNotEmpty()) container.player.playSongs(recentSongs, 0) },
                            onMore = { nav.navigate(Routes.history(0)) },
                        )
                    }
                    if (recentSongs.isEmpty()) {
                        item(key = "recEmpty") {
                            StateMessage(Icons.Rounded.Headphones, "Nada tocado ainda", "As músicas que você ouvir aparecem aqui.")
                        }
                    } else {
                        itemsIndexed(recentSongs, key = { _, it -> "h" + it.id }) { i, song ->
                            Box(Modifier.animateItem().intro("h" + song.id, i + 1, intro)) {
                                SongItem(song, recentSongs, player, song.contentKey in favoriteKeys, nav, { playlistSongs = listOf(it) }, selected = if (selecting) song.id in selection else null, onToggleSelect = toggleSelect)
                            }
                        }
                    }
                }
            }
        }

        // barra de ações da seleção
        androidx.compose.animation.AnimatedVisibility(
            selecting,
            enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionBar(
                count = selection.size,
                allSelected = visibleSongs.isNotEmpty() && visibleSongs.all { it.id in selection },
                onClose = { selection = emptySet() },
                onSelectAll = {
                    selection = if (visibleSongs.all { it.id in selection }) emptySet() else visibleSongs.mapTo(HashSet()) { it.id }
                },
                onPlaylist = { if (selectedSongs.isNotEmpty()) playlistSongs = selectedSongs },
                onShare = {
                    val uris = selectedSongs.map { it.contentUri() }
                    if (uris.isEmpty() || !com.musibox.app.ui.components.ExternalActions.shareContent(context, uris, "audio/*")) {
                        snack("Não foi possível compartilhar.")
                    }
                },
                onDelete = { if (selectedSongs.isNotEmpty()) confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        val n = selectedSongs.size
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (n == 1) "Apagar 1 música?" else "Apagar $n músicas?") },
            text = { Text("Os arquivos serão apagados deste aparelho. Isso não pode ser desfeito.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    runDelete(selectedSongs.map { it.contentUri() }, launchConfirm)
                }) { Text("Apagar", color = Color(0xFFFF5A5A)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            containerColor = Color(0xFF1E1E1E),
        )
    }

    playlistSongs?.let { AddToPlaylistDialog(it) { playlistSongs = null; selection = emptySet() } }
    if (creatingPlaylist) {
        TextInputDialog(
            title = "Nova playlist",
            initial = "",
            label = "Nome da playlist",
            confirmText = "Criar",
            onConfirm = { name ->
                creatingPlaylist = false
                vm.createPlaylist(name) { id ->
                    nav.navigate(Routes.playlist(id))
                    nav.navigate(Routes.pickSongs(id))
                }
            },
            onDismiss = { creatingPlaylist = false },
        )
    }
}

@Composable
private fun ListHeader(title: String, subtitle: String?, onShuffle: () -> Unit, onPlay: () -> Unit, onMore: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Neo.Text2)
        }
        if (onMore != null) {
            Text(
                "Ver todas",
                color = Neo.Link,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onMore)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        SmallRound(Icons.Rounded.Shuffle, "Aleatório", onShuffle)
        Spacer(Modifier.width(10.dp))
        SmallRound(Icons.Rounded.PlayArrow, "Tocar tudo", onPlay, filled = true)
    }
}

@Composable
private fun SmallRound(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier
            .size(if (filled) 52.dp else 46.dp)
            .bounceClick(pressed = 0.85f, shape = CircleShape, onClick = onClick)
            .then(
                if (filled) Modifier.clip(CircleShape).background(Neo.Green)
                else Modifier.clip(CircleShape).background(Color(0xFF212121)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (filled) Neo.OnGreen else Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PlaylistRow(name: String, subtitle: String, isNew: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(pressed = 0.97f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .then(
                    if (isNew) Modifier.neoGlass(shape)
                    else Modifier.clip(shape).background(Brush.linearGradient(listOf(Neo.Green, androidx.compose.ui.graphics.lerp(Neo.Green, Color.Black, 0.3f)))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isNew) Icons.Rounded.Add else Icons.AutoMirrored.Rounded.QueueMusic, null, tint = if (isNew) Neo.Link else Neo.OnGreen)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (isNew) Neo.Link else Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Neo.Artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Pastas de cantores

private data class SingerFolder(val name: String, val songs: List<Song>)

private fun buildSingerFolders(songs: List<Song>): List<SingerFolder> {
    val map = LinkedHashMap<String, MutableList<Song>>()
    val display = HashMap<String, String>()
    val unknown = ArrayList<Song>()
    for (s in songs) {
        val names = AudioKinds.singers(s.artist)
        if (names.isEmpty()) {
            unknown += s
            continue
        }
        for (n in names) {
            val key = n.lowercase()
            display.putIfAbsent(key, n)
            map.getOrPut(key) { ArrayList() } += s
        }
    }
    val folders = map.map { (k, list) -> SingerFolder(display[k] ?: k, list) }
        .sortedWith(compareByDescending<SingerFolder> { it.songs.size >= 2 }.thenBy { it.name.lowercase() })
    return if (unknown.isEmpty()) folders else folders + SingerFolder(UNKNOWN_SINGER, unknown)
}

const val UNKNOWN_SINGER = "Artista desconhecido"

private val FOLDER_COLORS: List<List<Color>>
    get() {
        val g = Neo.Green
        return listOf(
            listOf(g, androidx.compose.ui.graphics.lerp(g, Color.Black, 0.3f)),
            listOf(androidx.compose.ui.graphics.lerp(g, Color.White, 0.12f), androidx.compose.ui.graphics.lerp(g, Color.Black, 0.4f)),
            listOf(g, androidx.compose.ui.graphics.lerp(g, Color.Black, 0.5f)),
        )
    }

/** Pasta 3D com a capa de uma música do cantor saindo de dentro. */
@Composable
private fun SingerFolderCard(folder: SingerFolder, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = FOLDER_COLORS[(folder.name.hashCode() and 0x7fffffff) % FOLDER_COLORS.size]
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .bounceClick(pressed = 0.95f, shape = shape, onClick = onClick)
            .neoGlass(shape)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp),
        ) {
            // fundo da pasta (com a aba)
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val tab = Path().apply {
                    moveTo(0f, h * 0.16f)
                    lineTo(0f, h * 0.06f)
                    quadraticTo(0f, 0f, w * 0.06f, 0f)
                    lineTo(w * 0.34f, 0f)
                    lineTo(w * 0.42f, h * 0.12f)
                    lineTo(w, h * 0.12f)
                    lineTo(w, h * 0.3f)
                    close()
                }
                drawPath(tab, Brush.linearGradient(listOf(colors[0].copy(alpha = 0.75f), colors[1].copy(alpha = 0.75f))))
                drawRoundRect(
                    Brush.linearGradient(listOf(colors[0].copy(alpha = 0.55f), colors[1].copy(alpha = 0.55f))),
                    topLeft = Offset(0f, h * 0.12f),
                    size = Size(w, h * 0.88f),
                    cornerRadius = CornerRadius(w * 0.06f),
                )
            }
            // capa saindo da pasta
            Artwork(
                AudioCover(folder.songs.first().contentUri()),
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .size(64.dp)
                    .graphicsLayer { rotationZ = if (index % 2 == 0) -6f else 6f },
                corner = 10.dp,
            )
            // frente da pasta
            Canvas(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(colors[0], colors[1])),
                    cornerRadius = CornerRadius(size.width * 0.06f),
                )
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                    size = Size(size.width, size.height * 0.4f),
                    cornerRadius = CornerRadius(size.width * 0.06f),
                )
            }
            Text(
                "${folder.songs.size}",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(folder.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Fmt.plural(folder.songs.size, "música", "músicas"), color = Neo.Artist, fontSize = 12.sp, maxLines = 1)
    }
}



/** Barra que aparece ao segurar uma música: contador, selecionar tudo e ações em lote. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .shadow(14.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF21A1D20))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelAction(Icons.Rounded.Close, "Cancelar", onClose)
        Text(
            if (count == 1) "1 selecionada" else "$count selecionadas",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            maxLines = 1,
        )
        SelAction(if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll, if (allSelected) "Desmarcar tudo" else "Selecionar tudo", onSelectAll)
        SelAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar à playlist", onPlaylist)
        SelAction(Icons.Rounded.Share, "Compartilhar", onShare)
        SelAction(Icons.Rounded.Delete, "Apagar", onDelete, tint = Color(0xFFFF6A6A))
    }
}

@Composable
private fun SelAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White) {
    Box(
        Modifier
            .size(44.dp)
            .bounceClick(pressed = 0.85f, shape = CircleShape, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
}
