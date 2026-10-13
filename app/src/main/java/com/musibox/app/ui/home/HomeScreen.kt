package com.musibox.app.ui.home

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.musibox.app.ui.components.rememberDeviceTilt
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
 * Tela inicial "Holo Deck": palco holográfico em 3D (grade em perspectiva, disco flutuante e capa
 * que acompanham a inclinação do aparelho), portais 3D para algumas funções, painel de telemetria
 * e as seções de músicas. Só anima o que precisa (disco gira apenas tocando).
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
    val tilt = rememberDeviceTilt()
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
            item(key = "holo") {
                HoloStage(player, tilt, onOpenPlayer = { if (player.current != null) nav.navigate(Routes.PLAYER) else shuffleAll() })
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
            item(key = "portalsTitle") { Section("Portais", null) {} }
            item(key = "portals") {
                Portals(
                    listOf(
                        Portal("Músicas", Icons.Rounded.MusicNote, listOf(MbPalette.Pink, MbPalette.Red)) { nav.navigateTopLevel(Routes.library()) },
                        Portal("Baixar", Icons.Rounded.Download, listOf(MbPalette.BlueBright, MbPalette.BlueDeep)) { nav.navigateTopLevel(Routes.download()) },
                        Portal("Cofre", Icons.Rounded.Lock, listOf(MbPalette.PurpleBright, Color(0xFF4F5BFF))) { nav.navigateTopLevel(Routes.VAULT) },
                        Portal("Playlists", Icons.AutoMirrored.Rounded.QueueMusic, listOf(Color(0xFF2ECC71), Color(0xFF1E8BFF))) { nav.navigateTopLevel(Routes.library(4)) },
                        Portal("Favoritas", Icons.Rounded.Favorite, listOf(MbPalette.Pink, MbPalette.Purple)) { nav.navigateTopLevel(Routes.library(5)) },
                        Portal("Histórico", Icons.Rounded.History, listOf(MbPalette.Orange, Color(0xFFFF6A3D))) { nav.navigate(Routes.history()) },
                    ),
                )
            }
            item(key = "hudTitle") { Section("Telemetria", null) {} }
            item(key = "hud") {
                Telemetry(
                    listOf(
                        Gauge("Músicas", allSongs.size, MbPalette.BlueBright) { nav.navigateTopLevel(Routes.library()) },
                        Gauge("Artistas", artistCount, MbPalette.Pink) { nav.navigateTopLevel(Routes.library(1)) },
                        Gauge("Downloads", downloadCount, MbPalette.Orange) { nav.navigate(Routes.DOWNLOADS) },
                        Gauge("Cofre", vaultCount, Mb.colors.vault) { nav.navigateTopLevel(Routes.VAULT) },
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
// Palco holográfico

@Composable
private fun HoloStage(state: PlayerState, tilt: State<Offset>, onOpenPlayer: () -> Unit) {
    val playing = state.isPlaying
    val spin = if (playing) {
        val t = rememberInfiniteTransition(label = "holo")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "spin").value
    } else {
        0f
    }
    val lift by animateFloatAsState(if (playing) 1f else 0f, tween(700), label = "lift")
    Box(
        Modifier
            .fillMaxWidth()
            .height(370.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onOpenPlayer,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Grade em perspectiva (chão "tron") e horizonte
        Canvas(Modifier.fillMaxSize()) {
            val t = tilt.value
            val w = size.width
            val h = size.height
            val horizon = h * 0.52f
            val vx = w / 2 + t.x * w * 0.08f
            // brilho do horizonte
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, MbPalette.BlueDeep.copy(alpha = 0.28f), Color.Transparent), startY = horizon - 90f, endY = horizon + 90f),
            )
            drawCircle(
                Brush.radialGradient(listOf(MbPalette.Purple.copy(alpha = 0.35f), Color.Transparent), center = Offset(vx, horizon), radius = w * 0.6f),
                radius = w * 0.6f,
                center = Offset(vx, horizon),
            )
            val line = MbPalette.BlueBright
            // linhas que fogem para o ponto de fuga
            for (i in -10..10) {
                val bottomX = w / 2 + i * w * 0.16f
                drawLine(
                    Brush.verticalGradient(listOf(line.copy(alpha = 0f), line.copy(alpha = 0.35f)), startY = horizon, endY = h),
                    Offset(vx + (bottomX - w / 2) * 0.05f, horizon),
                    Offset(bottomX, h),
                    strokeWidth = 1.5f,
                )
            }
            // linhas horizontais com perspectiva
            var k = 1f
            while (k < 14f) {
                val y = horizon + (h - horizon) * (k * k) / 196f
                drawLine(line.copy(alpha = (0.05f + 0.30f * (y - horizon) / (h - horizon))), Offset(0f, y), Offset(w, y), strokeWidth = 1.2f)
                k += 1f
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = h * 0.85f, endY = h))
        }

        // Disco holográfico deitado sobre a grade
        Canvas(
            Modifier
                .padding(top = 150.dp)
                .size(270.dp)
                .graphicsLayer {
                    val t = tilt.value
                    rotationX = 64f - t.y * 6f
                    rotationY = t.x * 8f
                    rotationZ = spin
                    cameraDistance = 16f * density
                },
        ) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Color(0xFF16204A), Color(0xFF05070D)), radius = r), radius = r)
            var gr = r * 0.96f
            while (gr > r * 0.35f) {
                drawCircle(MbPalette.BlueBright.copy(alpha = 0.10f), radius = gr, style = Stroke(width = 1.2f))
                gr -= r * 0.06f
            }
            drawCircle(
                Brush.sweepGradient(listOf(MbPalette.BlueBright, MbPalette.Purple, Color.Transparent, MbPalette.Pink, MbPalette.BlueBright)),
                radius = r,
                style = Stroke(width = 5f),
            )
            drawCircle(MbPalette.BlueBright.copy(alpha = 0.6f), radius = r * 0.12f)
        }

        // Feixe de luz saindo do disco
        Canvas(
            Modifier
                .padding(top = 40.dp)
                .size(width = 220.dp, height = 260.dp),
        ) {
            val path = Path().apply {
                moveTo(size.width * 0.5f - 40f, size.height * 0.95f)
                lineTo(size.width * 0.5f + 40f, size.height * 0.95f)
                lineTo(size.width, 0f)
                lineTo(0f, 0f)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(MbPalette.BlueBright.copy(alpha = 0.0f), MbPalette.BlueBright.copy(alpha = 0.16f + 0.10f * lift)), startY = 0f, endY = size.height))
        }

        // Capa flutuante (holograma) que acompanha a inclinação
        val current = state.current
        Box(
            Modifier
                .padding(top = 36.dp)
                .size(176.dp)
                .graphicsLayer {
                    val t = tilt.value
                    rotationY = t.x * 16f
                    rotationX = -t.y * 10f + 6f
                    translationY = -14f * lift
                    cameraDistance = 14f * density
                    shadowElevation = 28f
                    shape = RoundedCornerShape(30.dp)
                    clip = true
                }
                .border(
                    2.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), MbPalette.BlueBright.copy(alpha = 0.5f), MbPalette.Purple.copy(alpha = 0.6f))),
                    RoundedCornerShape(30.dp),
                ),
        ) {
            if (current?.uri != null) {
                Artwork(AudioCover(current.uri), Modifier.fillMaxSize(), corner = 30.dp, fallbackBrush = Mb.colors.accentGradient)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF0E2A6E), Color(0xFF2B1A6B), Color(0xFF0B1530)))),
                    contentAlignment = Alignment.Center,
                ) { LogoMark(64.dp) }
            }
            // reflexo de vidro
            Canvas(Modifier.fillMaxSize()) {
                val shine = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width * 0.62f, 0f)
                    lineTo(size.width * 0.18f, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(shine, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)))
            }
            if (playing) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("AO VIVO", color = MbPalette.BlueBright, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Portais 3D (algumas funções; as demais seguem nas abas)

private data class Portal(val label: String, val icon: ImageVector, val colors: List<Color>, val onClick: () -> Unit)

@Composable
private fun Portals(portals: List<Portal>) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(176.dp),
    ) {
        itemsIndexed(portals, key = { _, p -> p.label }) { index, p ->
            val turn by remember(index) {
                derivedStateOf {
                    val info = state.layoutInfo
                    val it = info.visibleItemsInfo.firstOrNull { v -> v.index == index } ?: return@derivedStateOf 0f
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    ((it.offset + it.size / 2f - center) / info.viewportSize.width.coerceAtLeast(1)).coerceIn(-1f, 1f)
                }
            }
            Column(
                Modifier
                    .size(width = 118.dp, height = 160.dp)
                    .graphicsLayer {
                        rotationY = -turn * 28f
                        cameraDistance = 12f * density
                        val s = 1f - 0.08f * abs(turn)
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(p.colors.first().copy(alpha = 0.32f), Color(0xFF0B0F1C))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), p.colors.last().copy(alpha = 0.25f))), RoundedCornerShape(26.dp))
                    .clickable(onClick = p.onClick)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                HoloIcon(p.icon, p.colors)
                Text(p.label, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Ícone em esfera 3D (luz no alto, sombra embaixo e anel inclinado). */
@Composable
private fun HoloIcon(icon: ImageVector, colors: List<Color>, size: Dp = 76.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationX = 70f; cameraDistance = 10f * density },
        ) {
            drawCircle(Brush.sweepGradient(listOf(colors.first(), Color.Transparent, colors.last(), colors.first())), style = Stroke(width = 4f))
        }
        Box(
            Modifier
                .size(size * 0.70f)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = Offset(this.size.width * 0.3f, this.size.height * 0.25f), radius = this.size.minDimension * 0.55f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)), center = Offset(this.size.width * 0.42f, this.size.height * 0.35f), radius = this.size.minDimension * 0.75f),
                )
            }
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.34f))
        }
    }
}

// ---------------------------------------------------------------------------
// Telemetria (medidores circulares estilo painel)

private data class Gauge(val label: String, val value: Int, val color: Color, val onClick: () -> Unit)

@Composable
private fun Telemetry(gauges: List<Gauge>) {
    val max = (gauges.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF111A33), Color(0xFF0A0E1A))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), MbPalette.BlueBright.copy(alpha = 0.25f), Color.Transparent)), RoundedCornerShape(26.dp))
            .padding(vertical = 16.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        gauges.forEach { g ->
            val level = if (g.value <= 0) 0.04f else (0.15f + 0.85f * (ln(1f + g.value) / ln(1f + max))).coerceIn(0f, 1f)
            val anim by animateFloatAsState(level, tween(1_100), label = "gauge")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = g.onClick)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 7.dp.toPx()
                        val inset = stroke / 2 + 2f
                        val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(
                            Brush.sweepGradient(listOf(g.color.copy(alpha = 0.4f), g.color, g.color.copy(alpha = 0.4f))),
                            135f,
                            270f * anim,
                            false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        // marcações
                        for (i in 0..12) {
                            val a = Math.toRadians((135 + i * 22.5)).toFloat()
                            val r1 = size.minDimension / 2 - stroke * 1.9f
                            val r2 = r1 - 4f
                            drawLine(
                                Color.White.copy(alpha = 0.18f),
                                Offset(center.x + r1 * cos(a), center.y + r1 * sin(a)),
                                Offset(center.x + r2 * cos(a), center.y + r2 * sin(a)),
                                strokeWidth = 1.5f,
                            )
                        }
                    }
                    Text("${g.value}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Text(g.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = g.color, letterSpacing = 1.sp, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cabeçalho

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
