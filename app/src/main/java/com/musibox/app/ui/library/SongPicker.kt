package com.musibox.app.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.AudioKinds
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.contentUri
import com.musibox.app.data.repo.displayArtist
import com.musibox.app.media.AudioCover
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.NeoChip
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.neoGlass
import com.musibox.app.ui.components.rememberSnack
import kotlinx.coroutines.launch

/** Escolher várias músicas de uma vez para colocar na playlist. */
@Composable
fun SongPickerScreen(nav: NavController, playlistId: String) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm = appViewModel(key = "lib") { LibraryViewModel(it) }
    val all by vm.songs.collectAsStateWithLifecycle()
    val playlist by container.library.playlist(playlistId).collectAsStateWithLifecycle(initialValue = null)
    val tracks by container.library.playlistTracks(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    val kinds = remember { AudioKinds.get(context) }
    val overrides by kinds.overrides.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var query by rememberSaveable { mutableStateOf("") }
    var showAudios by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var saving by remember { mutableStateOf(false) }

    val inPlaylist = remember(tracks) { tracks.mapTo(HashSet()) { it.track.songKey } }
    val songs = all
    val visible = remember(songs, overrides, showAudios, query) {
        songs.orEmpty()
            .filter { kinds.isMusic(it, overrides) != showAudios }
            .filter { q ->
                query.isBlank() || q.title.contains(query.trim(), true) || q.displayArtist().contains(query.trim(), true)
            }
    }
    val selectable = visible.filter { it.contentKey !in inPlaylist }
    val allVisibleSelected = selectable.isNotEmpty() && selectable.all { it.id in selected }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        MbTopBar(
            "Escolher músicas",
            onBack = { nav.popBackStack() },
            subtitle = playlist?.name?.let { "Para a playlist \"$it\"" },
        ) {
            TextButton(onClick = {
                selected = if (allVisibleSelected) selected - selectable.map { it.id }.toSet() else selected + selectable.map { it.id }
            }) {
                Text(if (allVisibleSelected) "Limpar" else "Selecionar tudo", color = Neo.Link)
            }
        }
        // Busca
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(50.dp)
                .neoGlass(RoundedCornerShape(25.dp))
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Neo.Text2)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Buscar por nome ou cantor", color = Neo.Text2, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    cursorBrush = SolidColor(Neo.Cyan),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Limpar", tint = Neo.Text2) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { NeoChip("Músicas", Icons.Rounded.LibraryMusic, !showAudios, { showAudios = false }) }
            item { NeoChip("Áudios", Icons.Rounded.GraphicEq, showAudios, { showAudios = true }) }
        }

        Box(Modifier.weight(1f)) {
            when {
                songs == null -> LoadingState("Carregando suas músicas…")
                visible.isEmpty() -> StateMessage(Icons.Rounded.Search, if (query.isBlank()) "Nada por aqui" else "Nada encontrado para \"$query\"")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(visible, key = { it.id }) { song ->
                        val already = song.contentKey in inPlaylist
                        PickRow(
                            song = song,
                            checked = already || song.id in selected,
                            locked = already,
                            onToggle = {
                                selected = if (song.id in selected) selected - song.id else selected + song.id
                            },
                        )
                    }
                }
            }
        }

        // Botão de confirmar
        val count = selected.size
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            GradientButton(
                if (count == 0) "Toque nas músicas para escolher" else "Adicionar ${Fmt.plural(count, "música", "músicas")}",
                onClick = {
                    val chosen = songs.orEmpty().filter { it.id in selected }
                    saving = true
                    scope.launch {
                        val added = container.library.addToPlaylist(playlistId, chosen)
                        snack(if (added == 0) "Essas músicas já estavam na playlist." else "${Fmt.plural(added, "música adicionada", "músicas adicionadas")} à playlist.")
                        nav.popBackStack()
                    }
                },
                enabled = count > 0 && !saving,
                loading = saving,
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PickRow(song: Song, checked: Boolean, locked: Boolean, onToggle: () -> Unit) {
    val bg by animateColorAsState(if (checked && !locked) Color(0x331FDF67) else Color.Transparent, label = "pickBg")
    val check by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "pickCheck")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = !locked, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(AudioCover(song.contentUri()), Modifier.size(52.dp), corner = 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (locked) "Já está na playlist" else "${song.displayArtist()} • ${Fmt.duration(song.durationMs)}",
                color = if (locked) Neo.Cyan else Neo.Artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (checked) (if (locked) Color(0xFF3A4A66) else Neo.Blue) else Color.Transparent)
                .border(2.dp, if (checked) Color.Transparent else Color(0xFF5A6E92), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                null,
                tint = if (locked) Color.White else Neo.OnGreen,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = check
                        scaleY = check
                        alpha = check
                    },
            )
        }
    }
}
