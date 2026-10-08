package com.musibox.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.musibox.app.AppContainer
import com.musibox.app.core.SongKeys
import com.musibox.app.data.repo.PlayHistoryItem
import com.musibox.app.data.repo.contentUri
import com.musibox.app.download.Platform
import com.musibox.app.media.AudioCover
import com.musibox.app.ui.components.AddToPlaylistDialog
import com.musibox.app.ui.components.ExternalActions
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.MorePlatformsDialog
import com.musibox.app.ui.components.MusiLogo
import com.musibox.app.ui.components.PlatformTiles
import com.musibox.app.ui.components.SectionTitle
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.TrackRow
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.navigation.navigateTopLevel
import com.musibox.app.ui.theme.Mb
import com.musibox.app.ui.theme.MbPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(c: AppContainer) : ViewModel() {
    val recent: StateFlow<List<PlayHistoryItem>?> = c.history.recentPlays(80)
        .map { list -> list.distinctBy { it.entry.songKey }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm = appViewModel { HomeViewModel(it) }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = rememberSnack()
    val scope = rememberCoroutineScope()
    var showMore by remember { mutableStateOf(false) }
    var playlistFor by remember { mutableStateOf<PlayHistoryItem?>(null) }

    val openPlatform: (Platform) -> Unit = { p ->
        ExternalActions.openPlatform(context, p)
        snack("No ${p.label}, toque em Compartilhar e escolha \"Baixar com MusiBox\".")
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MusiLogo(Modifier.weight(1f))
                MbIconButton(Icons.Rounded.Search, "Pesquisar", { nav.navigate(Routes.SEARCH) }, size = 48.dp)
                Spacer(Modifier.width(10.dp))
                MbIconButton(Icons.Rounded.Settings, "Configurações", { nav.navigate(Routes.SETTINGS) }, size = 48.dp)
            }
        }
        item { HeroBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Shortcut(
                    Modifier.weight(1f), Icons.Rounded.MusicNote,
                    Brush.linearGradient(listOf(MbPalette.Pink, MbPalette.Red)),
                    "Minhas\nMúsicas", "Do dispositivo",
                ) { nav.navigateTopLevel(Routes.library()) }
                Shortcut(
                    Modifier.weight(1f), Icons.Rounded.Download,
                    Brush.linearGradient(listOf(Color(0xFFFF5A4E), Color(0xFFE0201C))),
                    "Baixar\nMúsicas", "YouTube, TikTok, Instagram e mais",
                ) { nav.navigateTopLevel(Routes.download()) }
                Shortcut(
                    Modifier.weight(1f), Icons.Rounded.Lock,
                    Mb.colors.vaultGradient,
                    "Cofre", "Fotos, vídeos e arquivos",
                ) { nav.navigateTopLevel(Routes.VAULT) }
                Shortcut(
                    Modifier.weight(1f), Icons.Rounded.History,
                    Brush.linearGradient(listOf(MbPalette.BlueBright, MbPalette.BlueDeep)),
                    "Histórico", "Downloads e reproduções",
                ) { nav.navigate(Routes.history()) }
            }
        }
        item {
            SectionTitle(
                "Plataformas de Download",
                Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp),
                actionText = "Abrir",
                onAction = { nav.navigateTopLevel(Routes.download()) },
            )
        }
        item {
            PlatformTiles(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                onPlatform = openPlatform,
                onMore = { showMore = true },
            )
        }
        item {
            SectionTitle(
                "Tocadas Recentemente",
                Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp),
                actionText = "Ver tudo",
                onAction = { nav.navigate(Routes.history(0)) },
            )
        }
        val list = recent
        when {
            list == null -> Unit
            list.isEmpty() -> item {
                StateMessage(
                    Icons.Rounded.Headphones,
                    "Nenhuma música tocada ainda",
                    "As músicas que você ouvir aparecem aqui.",
                    actionText = "Ver minhas músicas",
                    onAction = { nav.navigateTopLevel(Routes.library()) },
                )
            }
            else -> items(list, key = { it.entry.id }) { item ->
                val song = item.song
                val isCurrent = song != null && player.current?.songKey == song.contentKey
                TrackRow(
                    title = item.entry.title,
                    subtitle = if (song != null) SongKeys.displayArtist(item.entry.artist) else "Arquivo não encontrado neste dispositivo.",
                    artModel = song?.let { AudioCover(it.contentUri()) },
                    available = song != null,
                    isCurrent = isCurrent,
                    isPlaying = player.isPlaying,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    onClick = {
                        if (song == null) snack("Arquivo não encontrado neste dispositivo.")
                        else container.player.playSong(song)
                    },
                    onPlayPause = {
                        when {
                            song == null -> snack("Arquivo não encontrado neste dispositivo.")
                            isCurrent -> container.player.togglePlayPause()
                            else -> container.player.playSong(song)
                        }
                    },
                    menu = buildList {
                        if (song != null) {
                            add(MenuAction("Tocar a seguir", Icons.Rounded.SkipNext) { container.player.playNext(listOf(song)); snack("Vai tocar a seguir.") })
                            add(MenuAction("Adicionar à fila", Icons.AutoMirrored.Rounded.QueueMusic) { container.player.addToQueue(listOf(song)); snack("Adicionada à fila.") })
                            add(MenuAction("Adicionar à playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { playlistFor = item })
                        }
                        add(MenuAction("Remover do histórico", Icons.Rounded.DeleteOutline) {
                            scope.launch { container.history.remove(item.entry.id) }
                        })
                    },
                )
            }
        }
    }

    if (showMore) MorePlatformsDialog(onDismiss = { showMore = false }, onPaste = { showMore = false; nav.navigateTopLevel(Routes.download()) })
    playlistFor?.song?.let { song ->
        AddToPlaylistDialog(songs = listOf(song), onDismiss = { playlistFor = null })
    }
}

@Composable
private fun HeroBanner(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Mb.colors.heroGradient),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.80f, size.height * 0.48f)
            for (i in 1..5) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f + 0.02f * (5 - i)),
                    radius = size.height * 0.16f * i,
                    center = center,
                    style = Stroke(width = 2f),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(MbPalette.BlueBright.copy(alpha = 0.55f), Color.Transparent),
                    center = center,
                    radius = size.height * 0.7f,
                ),
                radius = size.height * 0.7f,
                center = center,
            )
        }
        Icon(
            Icons.Rounded.Headphones,
            null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
                .size(88.dp),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp, end = 120.dp),
        ) {
            Text(
                buildAnnotatedString {
                    append("Suas músicas\nem um ")
                    withStyle(SpanStyle(color = MbPalette.BlueBright)) { append("só lugar") }
                },
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(10.dp))
            Text("Ouça, baixe e salve com segurança.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Shortcut(
    modifier: Modifier,
    icon: ImageVector,
    iconBrush: Brush,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    MbCard(modifier.height(176.dp), onClick = onClick) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
