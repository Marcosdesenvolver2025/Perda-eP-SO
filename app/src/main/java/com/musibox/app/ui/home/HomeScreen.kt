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

private enum class HomeFilter { ALL, FAVORITES, PLAYLISTS, RECENT }

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

    val allSongs = songs.orEmpty()
    val recentSongs = recent.orEmpty().mapNotNull { it.song }

    val introTop = rememberIntroState()
    val intro = rememberIntroState(filter)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
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
                    item { NeoChip("Favoritas", Icons.Rounded.Favorite, filter == HomeFilter.FAVORITES, { filter = HomeFilter.FAVORITES }) }
                    item { NeoChip("Playlists", Icons.AutoMirrored.Rounded.QueueMusic, filter == HomeFilter.PLAYLISTS, { filter = HomeFilter.PLAYLISTS }) }
                    item { NeoChip("Recentes", Icons.Rounded.Schedule, filter == HomeFilter.RECENT, { filter = HomeFilter.RECENT }) }
                }
            }
            // Lista conforme o filtro escolhido
            when {
                filter != HomeFilter.PLAYLISTS && !permission.granted -> item(key = "perm") {
                    PermissionNeeded(permission.permanentlyDenied, permission.request, permission.openSettings)
                }
                filter == HomeFilter.ALL -> {
                    item(key = "allTitle") {
                        ListHeader(
                            "Suas músicas",
                            songs?.let { Fmt.plural(it.size, "música", "músicas") },
                            onShuffle = { if (allSongs.isNotEmpty()) container.player.playSongs(allSongs, 0, shuffle = true) },
                            onPlay = { if (allSongs.isNotEmpty()) container.player.playSongs(allSongs, 0) },
                            onMore = { nav.navigate(Routes.library()) },
                        )
                    }
                    when {
                        songs == null -> item(key = "loading") { LoadingState("Carregando suas músicas…") }
                        allSongs.isEmpty() -> item(key = "empty") {
                            StateMessage(
                                Icons.Rounded.MusicOff,
                                "Nenhuma música encontrada",
                                "Baixe músicas pelo MusiBox ou copie arquivos de áudio para o aparelho.",
                                actionText = "Ir para Baixar",
                                onAction = { nav.navigateTopLevel(Routes.download()) },
                                secondaryText = "Procurar novamente",
                                onSecondary = { vm.rescan() },
                            )
                        }
                        else -> itemsIndexed(allSongs, key = { _, it -> "s" + it.id }) { i, song ->
                            Box(Modifier.animateItem().intro("s" + song.id, i + 1, intro)) {
                                SongItem(song, allSongs, player, song.contentKey in favoriteKeys, nav, { playlistSongs = listOf(it) })
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
                                SongItem(song, available, player, true, nav, { playlistSongs = listOf(it) })
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
                            PlaylistRow("Nova playlist", "Crie e adicione músicas pelo menu ⋮", isNew = true) { creatingPlaylist = true }
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
                                SongItem(song, recentSongs, player, song.contentKey in favoriteKeys, nav, { playlistSongs = listOf(it) })
                            }
                        }
                    }
                }
            }
        }
    }

    playlistSongs?.let { AddToPlaylistDialog(it) { playlistSongs = null } }
    if (creatingPlaylist) {
        TextInputDialog(
            title = "Nova playlist",
            initial = "",
            label = "Nome da playlist",
            confirmText = "Criar",
            onConfirm = { name ->
                creatingPlaylist = false
                vm.createPlaylist(name) { id -> nav.navigate(Routes.playlist(id)) }
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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Neo.Text2)
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
        Spacer(Modifier.width(8.dp))
        SmallRound(Icons.Rounded.PlayArrow, "Tocar tudo", onPlay, filled = true)
    }
}

@Composable
private fun SmallRound(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier
            .size(40.dp)
            .bounceClick(pressed = 0.85f, shape = CircleShape, onClick = onClick)
            .then(
                if (filled) Modifier.clip(CircleShape).background(Neo.Accent)
                else Modifier.neoGlass(CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
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
                    else Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF2F7BFF), Color(0xFF8B5CFF)))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isNew) Icons.Rounded.Add else Icons.AutoMirrored.Rounded.QueueMusic, null, tint = if (isNew) Neo.Link else Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (isNew) Neo.Link else Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Neo.Artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
