package com.musibox.app.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.contentUri
import com.musibox.app.data.repo.displayArtist
import com.musibox.app.media.AudioCover
import com.musibox.app.media.PlayerState
import com.musibox.app.ui.components.ExternalActions
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.TrackRow
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/** Linha de música com as ações padrão (tocar a seguir, fila, playlist, favorito, artista, compartilhar). */
@Composable
fun SongItem(
    song: Song,
    queue: List<Song>,
    player: PlayerState,
    favorite: Boolean,
    nav: NavController?,
    onAddToPlaylist: (Song) -> Unit,
    subtitle: String? = null,
    extraMenu: List<MenuAction> = emptyList(),
    /** Seleção múltipla: null = normal. Com seleção ativa, tocar marca/desmarca. */
    selected: Boolean? = null,
    onToggleSelect: ((Song) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val snack = rememberSnack()
    val scope = rememberCoroutineScope()
    val isCurrent = player.current?.songId == song.id
    val play = {
        if (isCurrent) container.player.togglePlayPause() else container.player.playSong(song, queue)
    }
    TrackRow(
        title = song.title,
        subtitle = subtitle ?: song.displayArtist(),
        trailingText = if (subtitle == null) Fmt.duration(song.durationMs) else null,
        artModel = AudioCover(song.contentUri()),
        isCurrent = isCurrent,
        isPlaying = player.isPlaying,
        modifier = Modifier.padding(horizontal = 6.dp),
        onClick = { if (selected != null && onToggleSelect != null) onToggleSelect(song) else play() },
        onLongClick = onToggleSelect?.let { t -> { t(song) } },
        selected = selected,
        onPlayPause = play,
        menu = buildList {
            add(MenuAction("Tocar a seguir", Icons.Rounded.SkipNext) {
                container.player.playNext(listOf(song)); snack("Vai tocar a seguir.")
            })
            add(MenuAction("Adicionar à fila", Icons.AutoMirrored.Rounded.QueueMusic) {
                container.player.addToQueue(listOf(song)); snack("Adicionada à fila.")
            })
            add(MenuAction("Adicionar à playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { onAddToPlaylist(song) })
            add(
                MenuAction(
                    if (favorite) "Remover dos favoritos" else "Favoritar",
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                ) {
                    scope.launch {
                        val now = container.library.toggleFavorite(song)
                        snack(if (now) "Adicionada aos favoritos." else "Removida dos favoritos.")
                    }
                },
            )
            if (nav != null) {
                add(MenuAction("Ir para o artista", Icons.Rounded.Person) {
                    nav.navigate(Routes.songs("artist", song.displayArtist()))
                })
            }
            val kinds = com.musibox.app.core.AudioKinds.get(context)
            val isMusic = kinds.isMusic(song)
            add(
                MenuAction(
                    if (isMusic) "Mover para Áudios" else "Mover para Músicas",
                    if (isMusic) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                ) {
                    kinds.set(song, !isMusic)
                    snack(if (isMusic) "Movida para Áudios." else "Movida para Músicas.")
                },
            )
            add(MenuAction("Compartilhar", Icons.Rounded.Share) {
                if (!ExternalActions.shareContent(context, listOf(song.contentUri()), song.mimeType)) {
                    snack("Não foi possível compartilhar.")
                }
            })
            addAll(extraMenu)
        },
    )
}
