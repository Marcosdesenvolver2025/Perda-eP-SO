package com.musibox.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.musibox.app.AppContainer
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.MostPlayed
import com.musibox.app.data.repo.PlayHistoryItem
import com.musibox.app.data.repo.contentUri
import com.musibox.app.data.repo.displayArtist
import com.musibox.app.media.AudioCover
import com.musibox.app.media.PlayerState
import com.musibox.app.core.Fmt
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.LogoMark
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.download.DownloadSites
import com.musibox.app.ui.download.SiteIcon
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.navigation.navigateTopLevel
import com.musibox.app.ui.theme.Mb
import com.musibox.app.ui.theme.MbPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

class HomeViewModel(c: AppContainer) : ViewModel() {
    val recent: StateFlow<List<PlayHistoryItem>?> = c.history.recentPlays(80)
        .map { list -> list.distinctBy { it.entry.songKey }.filter { it.song != null }.take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs: StateFlow<List<Song>?> = c.music.songs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mostPlayed: StateFlow<List<MostPlayed>> = c.library.mostPlayed(10)
        .map { list -> list.filter { it.song != null }.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vaultCount: StateFlow<Int> = c.vault.items.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val downloadCount: StateFlow<Int> = c.downloads.all.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/**
 * Tela inicial "Palco": um disco de vinil no centro (gira só enquanto toca) com os atalhos
 * em órbita, mesa de som com os números da biblioteca, carrossel em 3D e discos em miniatura.
 */
@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm = appViewModel { HomeViewModel(it) }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val vaultCount by vm.vaultCount.collectAsStateWithLifecycle()
    val downloadCount by vm.downloadCount.collectAsStateWithLifecycle()
    val player by container.player.state.collectAsStateWithLifecycle()
    val user by container.auth.user.collectAsStateWithLifecycle()
    val snack = rememberSnack()
    val allSongs = songs.orEmpty()
    val newest = remember(allSongs) { allSongs.sortedByDescending { it.dateAddedMs }.take(12) }
    val artistCount = remember(allSongs) { allSongs.map { it.displayArtist() }.distinct().size }

    val shuffleAll: () -> Unit = {
        if (allSongs.isEmpty()) {
            nav.navigateTopLevel(Routes.library())
        } else {
            container.player.playSongs(allSongs.shuffled(), 0, shuffle = true)
            snack("Tocando tudo no aleatório.")
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        StageBackground(Modifier.fillMaxWidth().height(560.dp))
        LazyColumn(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "header") {
                Header(
                    name = user?.name?.substringBefore(' '),
                    photo = user?.photoUrl,
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onAccount = { nav.navigate(if (user == null) Routes.LOGIN else Routes.ACCOUNT) },
                )
            }
            item(key = "stage") {
                Stage(
                    state = player,
                    onMusic = { nav.navigateTopLevel(Routes.library()) },
                    onDownload = { nav.navigateTopLevel(Routes.download()) },
                    onVault = { nav.navigateTopLevel(Routes.VAULT) },
                    onHistory = { nav.navigate(Routes.history()) },
                    onOpenPlayer = { nav.navigate(Routes.PLAYER) },
                )
            }
            item(key = "controls") {
                StageControls(
                    state = player,
                    songCount = allSongs.size,
                    onPrev = { container.player.previous() },
                    onToggle = { container.player.togglePlayPause() },
                    onNext = { container.player.next() },
                    onShuffle = shuffleAll,
                )
            }
            item(key = "mixer") {
                Section("Mesa de som", null) {}
                Mixer(
                    channels = listOf(
                        MixChannel("Músicas", allSongs.size, MbPalette.BlueBright) { nav.navigateTopLevel(Routes.library()) },
                        MixChannel("Artistas", artistCount, MbPalette.Pink) { nav.navigateTopLevel(Routes.library(1)) },
                        MixChannel("Downloads", downloadCount, MbPalette.Orange) { nav.navigate(Routes.DOWNLOADS) },
                        MixChannel("Cofre", vaultCount, Mb.colors.vault) { nav.navigateTopLevel(Routes.VAULT) },
                    ),
                )
            }

            val rec = recent.orEmpty()
            if (rec.isNotEmpty()) {
                item(key = "recentTitle") { Section("Continuar ouvindo", "Ver tudo") { nav.navigate(Routes.history(0)) } }
                item(key = "recentFlow") {
                    CoverFlow(
                        items = rec.mapNotNull { it.song },
                        currentKey = player.current?.songKey,
                        playing = player.isPlaying,
                    ) { song -> container.player.playSong(song, rec.mapNotNull { it.song }) }
                }
            }

            if (newest.isNotEmpty()) {
                item(key = "newTitle") { Section("Chegaram agora", "Ver todas") { nav.navigateTopLevel(Routes.library(6)) } }
                item(key = "newRow") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(newest, key = { "n_" + it.id }) { song ->
                            MiniVinyl(song, player.current?.songKey == song.contentKey && player.isPlaying) {
                                container.player.playSong(song, newest)
                            }
                        }
                    }
                }
            }

            if (mostPlayed.isNotEmpty()) {
                item(key = "topTitle") { Section("As que você mais ouve", "Ver todas") { nav.navigateTopLevel(Routes.library(7)) } }
                itemsIndexed(mostPlayed, key = { _, m -> "t_" + m.stat.songKey }) { i, m ->
                    val song = m.song ?: return@itemsIndexed
                    TopRow(i + 1, song, m.stat.playCount, player.current?.songKey == song.contentKey) {
                        container.player.playSong(song, mostPlayed.mapNotNull { it.song })
                    }
                }
            }

            if (allSongs.isEmpty() && songs != null) {
                item(key = "empty") { EmptyLibraryCard { nav.navigateTopLevel(Routes.library()) } }
            }

            item(key = "sitesTitle") { Section("Baixe de qualquer lugar", "Abrir") { nav.navigateTopLevel(Routes.download()) } }
            item(key = "sites") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(DownloadSites.take(8), key = { it.name }) { site ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { nav.navigate(Routes.browser(site.url)) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SiteIcon(site, 56.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(site.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
            item(key = "vault") { VaultTeaser(vaultCount) { nav.navigateTopLevel(Routes.VAULT) } }
        }
    }
}

// ---------------------------------------------------------------------------
// Fundo e cabeçalho

@Composable
private fun StageBackground(modifier: Modifier) {
    val bg = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Luzes de palco: dois "holofotes" (azul e roxo) cruzando sobre o disco.
        fun beam(cx: Float, color: Color) {
            drawCircle(
                Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, h * 0.42f), radius = w * 0.75f),
                radius = w * 0.75f,
                center = Offset(cx, h * 0.42f),
            )
        }
        beam(w * 0.15f, MbPalette.BlueDeep.copy(alpha = 0.38f))
        beam(w * 0.88f, MbPalette.Purple.copy(alpha = 0.34f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg), startY = h * 0.55f, endY = h))
    }
}

@Composable
private fun Header(name: String?, photo: String?, onSearch: () -> Unit, onAccount: () -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoMark(34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isNullOrBlank()) "$greeting!" else "$greeting, $name",
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Seu palco está pronto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
        RoundGlass(onSearch) { Icon(Icons.Rounded.Search, "Pesquisar", tint = MaterialTheme.colorScheme.onBackground) }
        Spacer(Modifier.width(8.dp))
        RoundGlass(onAccount) {
            Icon(Icons.Rounded.Person, "Conta", tint = MaterialTheme.colorScheme.onBackground)
            if (photo != null) AsyncImage(photo, "Foto da conta", Modifier.fillMaxSize().clip(CircleShape))
        }
    }
}

@Composable
private fun RoundGlass(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------------------
// Palco: disco de vinil + atalhos em órbita

private data class Orbit(val icon: ImageVector, val label: String, val brush: Brush, val angle: Float, val onClick: () -> Unit)

@Composable
private fun Stage(
    state: PlayerState,
    onMusic: () -> Unit,
    onDownload: () -> Unit,
    onVault: () -> Unit,
    onHistory: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center,
    ) {
        val stageW = maxWidth
        val disc = minOf(stageW * 0.62f, 250.dp)
        val orbitR = disc / 2 + 52.dp
        val orbits = listOf(
            Orbit(Icons.Rounded.MusicNote, "Músicas", Brush.linearGradient(listOf(MbPalette.Pink, MbPalette.Red)), 215f, onMusic),
            Orbit(Icons.Rounded.Download, "Baixar", Mb.colors.accentGradient, 325f, onDownload),
            Orbit(Icons.Rounded.Lock, "Cofre", Mb.colors.vaultGradient, 35f, onVault),
            Orbit(Icons.Rounded.History, "Histórico", Brush.linearGradient(listOf(MbPalette.Orange, Color(0xFFFF6A3D))), 145f, onHistory),
        )
        // Anel da órbita
        Canvas(Modifier.size(orbitR * 2)) {
            drawCircle(Color.White.copy(alpha = 0.07f), style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.04f), radius = size.minDimension / 2 - 18.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
        }
        Vinyl(state, Modifier.size(disc).clickable(enabled = state.current != null, onClick = onOpenPlayer))
        orbits.forEach { o ->
            val rad = Math.toRadians(o.angle.toDouble())
            val dx = orbitR * cos(rad).toFloat()
            val dy = orbitR * sin(rad).toFloat()
            Column(
                Modifier
                    .offset(x = dx, y = dy)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = o.onClick)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(o.brush)
                        .border(2.dp, MaterialTheme.colorScheme.background.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(o.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.height(4.dp))
                Text(o.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
private fun Vinyl(state: PlayerState, modifier: Modifier) {
    val playing = state.isPlaying
    val rotation = if (playing) {
        val t = rememberInfiniteTransition(label = "vinyl")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "spin").value
    } else {
        0f
    }
    val armAngle by animateFloatAsState(if (playing) 22f else 0f, tween(600), label = "arm")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(Color(0xFF1B1F2A), Color(0xFF07080C)), radius = r), radius = r)
                // Sulcos
                var gr = r * 0.95f
                while (gr > r * 0.40f) {
                    drawCircle(Color.White.copy(alpha = 0.045f), radius = gr, style = Stroke(width = 1f))
                    gr -= r * 0.035f
                }
                // Brilho (reflexo da luz)
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            Color.Transparent, MbPalette.BlueBright.copy(alpha = 0.18f), Color.Transparent,
                            Color.Transparent, MbPalette.Purple.copy(alpha = 0.16f), Color.Transparent,
                        ),
                    ),
                    radius = r,
                )
                drawCircle(Color.White.copy(alpha = 0.10f), radius = r, style = Stroke(width = 2f))
            }
            // Selo central: capa da música atual (ou o logotipo)
            val label = Modifier
                .fillMaxSize(0.40f)
                .clip(CircleShape)
            val current = state.current
            if (current?.uri != null) {
                Artwork(AudioCover(current.uri), label, corner = 200.dp, fallbackBrush = Mb.colors.accentGradient)
            } else {
                Box(label.background(Mb.colors.accentGradient), contentAlignment = Alignment.Center) { LogoMark(34.dp) }
            }
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
            )
        }
        // Braço do toca-discos
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = armAngle
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.92f, 0.06f)
                },
        ) {
            val w = size.width
            val pivot = Offset(w * 0.92f, w * 0.06f)
            drawCircle(Color(0xFFB8C2D6), radius = w * 0.04f, center = pivot)
            drawLine(Color(0xFFB8C2D6), pivot, Offset(w * 0.80f, w * 0.48f), strokeWidth = w * 0.018f)
            drawRoundRect(
                Color(0xFFDDE3EE),
                topLeft = Offset(w * 0.76f, w * 0.46f),
                size = androidx.compose.ui.geometry.Size(w * 0.07f, w * 0.10f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.015f),
            )
        }
    }
}

@Composable
private fun StageControls(
    state: PlayerState,
    songCount: Int,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
) {
    val current = state.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            current?.title ?: if (songCount > 0) "Pronto para tocar" else "Seu palco está vazio",
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            current?.displayArtist ?: if (songCount > 0) Fmt.plural(songCount, "música", "músicas") + " no aparelho" else "Baixe músicas ou permita o acesso às do aparelho",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(34.dp))
                .background(Mb.colors.card)
                .border(1.dp, Mb.colors.border, RoundedCornerShape(34.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (current == null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(Mb.colors.accentGradient)
                        .clickable(onClick = onShuffle)
                        .padding(horizontal = 26.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shuffle, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(if (songCount > 0) "Tocar tudo no aleatório" else "Ver minhas músicas", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            } else {
                CtrlButton(Icons.Rounded.SkipPrevious, "Anterior", onPrev)
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Mb.colors.accentGradient)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Tocar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                CtrlButton(Icons.Rounded.SkipNext, "Próxima", onNext)
                CtrlButton(Icons.Rounded.Shuffle, "Aleatório de tudo", onShuffle)
            }
        }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MaterialTheme.colorScheme.onBackground) }
}

// ---------------------------------------------------------------------------
// Mesa de som (números da biblioteca como faders)

private data class MixChannel(val label: String, val value: Int, val color: Color, val onClick: () -> Unit)

@Composable
private fun Mixer(channels: List<MixChannel>) {
    val max = (channels.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Mb.colors.cardHigh, Mb.colors.card)))
            .border(1.dp, Mb.colors.border, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        channels.forEach { ch ->
            // Escala logarítmica: valores pequenos também aparecem.
            val level = if (ch.value <= 0) 0.06f else (0.15f + 0.85f * (ln(1f + ch.value) / ln(1f + max))).coerceIn(0f, 1f)
            val anim by animateFloatAsState(level, tween(900), label = "fader")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = ch.onClick)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${ch.value}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ch.color)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(96.dp).width(36.dp), contentAlignment = Alignment.BottomCenter) {
                    // Trilho
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                    )
                    // Nível
                    Box(
                        Modifier
                            .fillMaxHeight(anim)
                            .width(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.verticalGradient(listOf(ch.color, ch.color.copy(alpha = 0.35f)))),
                    )
                    // Botão do fader
                    Box(Modifier.fillMaxHeight(anim), contentAlignment = Alignment.TopCenter) {
                        Box(
                            Modifier
                                .offset(y = (-7).dp)
                                .size(width = 30.dp, height = 14.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFDDE3EE))
                                .border(1.dp, ch.color, RoundedCornerShape(5.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(ch.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carrossel 3D ("coverflow") e discos em miniatura

@Composable
private fun CoverFlow(items: List<Song>, currentKey: String?, playing: Boolean, onPlay: (Song) -> Unit) {
    val state = rememberLazyListState()
    val itemSize = 150.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = (maxWidth - itemSize) / 2
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = side.coerceAtLeast(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(itemSize + 64.dp),
        ) {
            itemsIndexed(items, key = { _, s -> "c_" + s.id }) { index, song ->
                val transform by remember(index) {
                    derivedStateOf {
                        val info = state.layoutInfo
                        val it = info.visibleItemsInfo.firstOrNull { v -> v.index == index }
                        if (it == null) {
                            0f
                        } else {
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val itemCenter = it.offset + it.size / 2f
                            ((itemCenter - center) / it.size).coerceIn(-2f, 2f)
                        }
                    }
                }
                val isCurrent = currentKey == song.contentKey
                Column(
                    Modifier
                        .width(itemSize)
                        .graphicsLayer {
                            rotationY = -transform * 38f
                            val s = 1f - 0.16f * abs(transform).coerceAtMost(1.5f)
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.25f * abs(transform).coerceAtMost(1.5f)
                            cameraDistance = 14f * density
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPlay(song) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(itemSize)) {
                        Artwork(AudioCover(song.contentUri()), Modifier.fillMaxSize(), corner = 20.dp)
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent && playing) MbPalette.BlueBright else Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isCurrent && playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.displayArtist(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MiniVinyl(song: Song, playing: Boolean, onClick: () -> Unit) {
    val size: Dp = 112.dp
    Column(
        Modifier
            .width(size)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                drawCircle(Color(0xFF0B0D13), radius = r)
                var gr = r * 0.92f
                while (gr > r * 0.5f) {
                    drawCircle(Color.White.copy(alpha = 0.05f), radius = gr, style = Stroke(width = 1f))
                    gr -= r * 0.07f
                }
                drawCircle(if (playing) MbPalette.BlueBright else Color.White.copy(alpha = 0.12f), radius = r, style = Stroke(width = 3f))
            }
            Artwork(
                AudioCover(song.contentUri()),
                Modifier
                    .size(size * 0.48f)
                    .clip(CircleShape),
                corner = 100.dp,
                fallbackBrush = Mb.colors.accentGradient,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.displayArtist(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------
// Demais seções

@Composable
private fun Section(title: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontFamily = FontFamily.Serif, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TopRow(rank: Int, song: Song, plays: Int, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            fontSize = 30.sp,
            fontFamily = FontFamily.Serif,
            color = if (rank == 1) MbPalette.BlueBright else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(34.dp),
        )
        Artwork(AudioCover(song.contentUri()), Modifier.size(52.dp), corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SongKeys.displayArtist(song.artist), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(Fmt.plural(plays, "vez", "vezes"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyLibraryCard(onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Mb.colors.card)
            .border(1.dp, Mb.colors.border, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Nenhuma música encontrada ainda", style = MaterialTheme.typography.titleSmall)
            Text(
                "Toque para permitir o acesso às músicas do aparelho ou baixe novas pela aba Baixar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VaultTeaser(count: Int, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1660), Color(0xFF1A1440))))
            .border(1.dp, Mb.colors.vault.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Mb.colors.vaultGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Cofre privado", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (count > 0) "${Fmt.plural(count, "item protegido", "itens protegidos")} com criptografia" else "Guarde fotos, vídeos e arquivos com PIN",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
