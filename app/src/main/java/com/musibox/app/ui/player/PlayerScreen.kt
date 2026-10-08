package com.musibox.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.Song
import com.musibox.app.media.AudioCover
import com.musibox.app.ui.components.AddToPlaylistDialog
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.OverflowMenu
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.TrackRow
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb
import kotlinx.coroutines.launch

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val state by container.player.state.collectAsStateWithLifecycle()
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle(initialValue = emptySet())
    val index by container.music.index.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    var playlistSong by remember { mutableStateOf<Song?>(null) }

    val current = state.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MbIconButton(Icons.Rounded.KeyboardArrowDown, "Fechar player", { nav.popBackStack() }, background = Color.White.copy(alpha = 0.08f))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOCANDO AGORA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp)
                    if (current != null) {
                        Text(
                            SongKeys.displayAlbum(current.album),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MbIconButton(Icons.AutoMirrored.Rounded.QueueMusic, "Fila de reprodução", { nav.navigate(Routes.QUEUE) }, background = Color.White.copy(alpha = 0.08f))
            }

            if (current == null) {
                Spacer(Modifier.height(80.dp))
                StateMessage(
                    Icons.Rounded.Headphones,
                    "Nada tocando agora",
                    "Escolha uma música na sua biblioteca.",
                    actionText = "Ver músicas",
                    onAction = { nav.popBackStack(); nav.navigate(Routes.library()) },
                )
                return@Column
            }

            Spacer(Modifier.height(24.dp))
            Artwork(
                AudioCover(current.uri ?: android.net.Uri.EMPTY),
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(28.dp, RoundedCornerShape(28.dp), ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
                corner = 28.dp,
                fallbackIcon = Icons.Rounded.Headphones,
            )
            Spacer(Modifier.height(28.dp))

            val isFav = current.songKey in favorites
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        current.displayArtist,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        container.library.setFavorite(
                            current.songKey, current.looseKey, current.title, current.artist, current.album,
                            current.durationMs, !isFav,
                        )
                        snack(if (!isFav) "Adicionada aos favoritos." else "Removida dos favoritos.")
                    }
                }) {
                    Icon(
                        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isFav) "Remover dos favoritos" else "Favoritar",
                        tint = if (isFav) Color(0xFFFF4D8D) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            val duration = state.durationMs.coerceAtLeast(1)
            val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            Slider(
                value = dragging ?: progress,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { container.player.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val shown = dragging?.let { (it * duration).toLong() } ?: state.positionMs
                Text(Fmt.duration(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.duration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { container.player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle, if (state.shuffle) "Aleatório ligado" else "Aleatório desligado",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { container.player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .size(78.dp)
                        .shadow(20.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                        .clip(CircleShape)
                        .background(Mb.colors.accentGradient)
                        .clickable { container.player.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Tocar",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { container.player.next() }, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Próxima", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { container.player.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repetir uma"
                            Player.REPEAT_MODE_ALL -> "Repetir todas"
                            else -> "Repetir desligado"
                        },
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    BottomAction(Icons.Rounded.Speed, "${trimSpeed(state.speed)}x") { speedMenu = true }
                    DropdownMenu(speedMenu, { speedMenu = false }, containerColor = Mb.colors.cardHigh) {
                        speeds.forEach { sp ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${trimSpeed(sp)}x",
                                        color = if (sp == state.speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = { speedMenu = false; container.player.setSpeed(sp) },
                            )
                        }
                    }
                }
                BottomAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Playlist") {
                    val song = index.byId(current.songId)
                    if (song != null) playlistSong = song else snack("Arquivo não encontrado neste dispositivo.")
                }
                BottomAction(Icons.AutoMirrored.Rounded.QueueMusic, "Fila") { nav.navigate(Routes.QUEUE) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    playlistSong?.let { AddToPlaylistDialog(listOf(it)) { playlistSong = null } }
}

private fun trimSpeed(s: Float): String =
    if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0').trimEnd('.').replace('.', ',')

@Composable
private fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QueueScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val state by container.player.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        MbTopBar(
            "Fila de reprodução",
            onBack = { nav.popBackStack() },
            subtitle = Fmt.plural(state.queue.size, "música", "músicas"),
        ) {
            if (state.queue.isNotEmpty()) {
                OverflowMenu(listOf(MenuAction("Limpar fila", Icons.Rounded.Close, destructive = true) { container.player.clearQueue() }))
            }
        }
        if (state.queue.isEmpty()) {
            StateMessage(Icons.AutoMirrored.Rounded.QueueMusic, "A fila está vazia", "Toque em uma música para começar.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(state.queue, key = { i, item -> "${i}_${item.mediaId}" }) { i, item ->
                val isCurrent = i == state.currentIndex
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackRow(
                        title = item.title,
                        subtitle = item.displayArtist,
                        artModel = item.uri?.let { AudioCover(it) },
                        isCurrent = isCurrent,
                        isPlaying = state.isPlaying,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                        onClick = { container.player.playAt(i) },
                        menu = buildList {
                            if (i > 0) add(MenuAction("Mover para cima", Icons.Rounded.ArrowUpward) { container.player.move(i, i - 1) })
                            if (i < state.queue.lastIndex) add(MenuAction("Mover para baixo", Icons.Rounded.ArrowDownward) { container.player.move(i, i + 1) })
                            add(MenuAction("Remover da fila", Icons.Rounded.Close, destructive = true) { container.player.removeAt(i) })
                        },
                    )
                }
            }
        }
    }
}
