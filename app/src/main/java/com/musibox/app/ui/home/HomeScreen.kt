package com.musibox.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.musibox.app.AppContainer
import com.musibox.app.core.Fmt
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.MostPlayed
import com.musibox.app.data.repo.PlayHistoryItem
import com.musibox.app.data.repo.contentUri
import com.musibox.app.data.repo.displayArtist
import com.musibox.app.media.AudioCover
import com.musibox.app.media.PlayerState
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.LogoMark
import com.musibox.app.ui.components.MbIconButton
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
import kotlin.math.PI
import kotlin.math.cos
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

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxWidth().height(420.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item(key = "header") {
                Header(
                    name = user?.name?.substringBefore(' '),
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            item(key = "hero") {
                if (player.current != null) {
                    NowPlayingHero(
                        player,
                        onOpen = { nav.navigate(Routes.PLAYER) },
                        onToggle = { container.player.togglePlayPause() },
                        onNext = { container.player.next() },
                    )
                } else {
                    ShuffleHero(
                        count = allSongs.size,
                        onShuffle = {
                            if (allSongs.isEmpty()) {
                                nav.navigateTopLevel(Routes.library())
                            } else {
                                container.player.playSongs(allSongs.shuffled(), 0, shuffle = true)
                                snack("Tocando tudo no aleatório.")
                            }
                        },
                    )
                }
            }
            item(key = "actions") {
                QuickActions(
                    onMusic = { nav.navigateTopLevel(Routes.library()) },
                    onDownload = { nav.navigateTopLevel(Routes.download()) },
                    onVault = { nav.navigateTopLevel(Routes.VAULT) },
                    onHistory = { nav.navigate(Routes.history()) },
                )
            }
            item(key = "stats") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(Modifier.weight(1f), "${allSongs.size}", "músicas", MbPalette.BlueBright) { nav.navigateTopLevel(Routes.library()) }
                    StatTile(Modifier.weight(1f), "$artistCount", "artistas", MbPalette.Pink) { nav.navigateTopLevel(Routes.library(1)) }
                    StatTile(Modifier.weight(1f), "$downloadCount", "downloads", MbPalette.Orange) { nav.navigate(Routes.DOWNLOADS) }
                    StatTile(Modifier.weight(1f), "$vaultCount", "no cofre", Mb.colors.vault) { nav.navigateTopLevel(Routes.VAULT) }
                }
            }

            val rec = recent.orEmpty()
            if (rec.isNotEmpty()) {
                item(key = "recentTitle") { Section("Continuar ouvindo", "Ver tudo") { nav.navigate(Routes.history(0)) } }
                item(key = "recentRow") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(rec, key = { it.entry.id }) { item ->
                            val song = item.song ?: return@items
                            CoverCard(
                                model = AudioCover(song.contentUri()),
                                title = item.entry.title,
                                subtitle = SongKeys.displayArtist(item.entry.artist),
                                playing = player.current?.songKey == song.contentKey && player.isPlaying,
                            ) { container.player.playSong(song, rec.mapNotNull { it.song }) }
                        }
                    }
                }
            }

            if (newest.isNotEmpty()) {
                item(key = "newTitle") { Section("Adicionadas recentemente", "Ver todas") { nav.navigateTopLevel(Routes.library(6)) } }
                item(key = "newRow") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(newest, key = { "n_" + it.id }) { song ->
                            CoverCard(
                                model = AudioCover(song.contentUri()),
                                title = song.title,
                                subtitle = song.displayArtist(),
                                playing = player.current?.songKey == song.contentKey && player.isPlaying,
                            ) { container.player.playSong(song, newest) }
                        }
                    }
                }
            }

            if (mostPlayed.isNotEmpty()) {
                item(key = "topTitle") { Section("Suas mais tocadas", "Ver todas") { nav.navigateTopLevel(Routes.library(7)) } }
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
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

/** Fundo "aurora": manchas de luz que se movem devagar atrás do cabeçalho. */
@Composable
private fun AuroraBackground(modifier: Modifier) {
    // Animação curta ao abrir (depois fica parada para não gastar bateria).
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo((PI / 2).toFloat(), tween(2_400)) }
    val phase = anim.value
    val bg = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun blob(cx: Float, cy: Float, r: Float, color: Color) {
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        }
        blob(w * (0.2f + 0.08f * cos(phase)), h * (0.25f + 0.06f * sin(phase)), w * 0.65f, MbPalette.BlueDeep.copy(alpha = 0.45f))
        blob(w * (0.85f + 0.06f * sin(phase * 1.3f)), h * (0.18f + 0.07f * cos(phase)), w * 0.55f, MbPalette.Purple.copy(alpha = 0.38f))
        blob(w * (0.55f + 0.1f * cos(phase * 0.7f)), h * (0.55f + 0.05f * sin(phase * 1.6f)), w * 0.5f, MbPalette.BlueBright.copy(alpha = 0.22f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg), startY = h * 0.45f, endY = h))
    }
}

@Composable
private fun Header(name: String?, onSearch: () -> Unit, onSettings: () -> Unit) {
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
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoMark(40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isNullOrBlank()) "$greeting!" else "$greeting, $name!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("O que vamos ouvir hoje?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
        }
        GlassButton(Icons.Rounded.Search, "Pesquisar", onSearch)
        Spacer(Modifier.width(8.dp))
        GlassButton(Icons.Rounded.Settings, "Configurações", onSettings)
    }
}

@Composable
private fun GlassButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MaterialTheme.colorScheme.onBackground) }
}

@Composable
private fun NowPlayingHero(state: PlayerState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val current = state.current ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF16245A), Color(0xFF2A1A5E), Color(0xFF0E1A3A))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .clickable(onClick = onOpen)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(current.uri?.let { AudioCover(it) }, Modifier.size(96.dp), corner = 20.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Equalizer(state.isPlaying, Modifier.size(width = 18.dp, height = 14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.isPlaying) "TOCANDO AGORA" else "PAUSADO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MbPalette.BlueBright,
                        letterSpacing = 1.5.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(current.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(current.displayArtist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        val dur = state.durationMs.takeIf { it > 0 } ?: current.durationMs
        LinearProgressIndicator(
            progress = { if (dur > 0) (state.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MbPalette.BlueBright,
            trackColor = Color.White.copy(alpha = 0.15f),
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${Fmt.duration(state.positionMs)} / ${Fmt.duration(dur)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pausar" else "Tocar",
                    tint = Color(0xFF0B1020),
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(enabled = state.hasNext, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.SkipNext, "Próxima", tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.35f)) }
        }
    }
}

@Composable
private fun ShuffleHero(count: Int, onShuffle: () -> Unit) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo((2 * PI).toFloat(), tween(2_000)) }
    val phase = anim.value
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0F2D78), Color(0xFF3B1F8F), Color(0xFF0B1A3D))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .clickable(onClick = onShuffle),
    ) {
        // Onda sonora animada
        Canvas(Modifier.fillMaxSize()) {
            val bars = 36
            val bw = size.width / (bars * 1.6f)
            for (i in 0 until bars) {
                val x = size.width * (i + 0.5f) / bars
                val amp = (0.35f + 0.65f * ((sin(phase + i * 0.45f) + 1f) / 2f)) * (0.4f + 0.6f * sin(PI.toFloat() * i / bars))
                val bh = size.height * 0.42f * amp
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f + 0.10f * amp),
                    topLeft = Offset(x - bw / 2, size.height * 0.78f - bh),
                    size = androidx.compose.ui.geometry.Size(bw, bh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(20.dp),
        ) {
            Text("SEU MIX", style = MaterialTheme.typography.labelSmall, color = MbPalette.BlueBright, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (count > 0) "Toque tudo no\naleatório" else "Sua biblioteca\nestá esperando",
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (count > 0) Fmt.plural(count, "música", "músicas") + " no aparelho" else "Toque para ver suas músicas",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(Mb.colors.accentGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (count > 0) Icons.Rounded.Shuffle else Icons.Rounded.LibraryMusic, "Tocar no aleatório", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun Equalizer(playing: Boolean, modifier: Modifier) {
    // Só anima enquanto toca (animação infinita gasta bateria).
    if (playing) AnimatedEqualizer(modifier) else EqualizerBars(listOf(0.3f, 0.3f, 0.3f), modifier)
}

@Composable
private fun AnimatedEqualizer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(340), RepeatMode.Reverse), label = "c")
    EqualizerBars(listOf(a, b, c), modifier)
}

@Composable
private fun EqualizerBars(levels: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        val bw = size.width / 5
        levels.forEachIndexed { i, l ->
            val h = size.height * l
            drawRoundRect(
                MbPalette.BlueBright,
                topLeft = Offset(i * bw * 2, size.height - h),
                size = androidx.compose.ui.geometry.Size(bw, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2),
            )
        }
    }
}

@Composable
private fun QuickActions(onMusic: () -> Unit, onDownload: () -> Unit, onVault: () -> Unit, onHistory: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickAction(Modifier.weight(1f), Icons.Rounded.MusicNote, "Músicas", Brush.linearGradient(listOf(MbPalette.Pink, MbPalette.Red)), onMusic)
        QuickAction(Modifier.weight(1f), Icons.Rounded.Download, "Baixar", Mb.colors.accentGradient, onDownload)
        QuickAction(Modifier.weight(1f), Icons.Rounded.Lock, "Cofre", Mb.colors.vaultGradient, onVault)
        QuickAction(Modifier.weight(1f), Icons.Rounded.History, "Histórico", Brush.linearGradient(listOf(MbPalette.Orange, Color(0xFFFF6A3D))), onHistory)
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, brush: Brush, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Mb.colors.card)
            .border(1.dp, Mb.colors.border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(brush),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun StatTile(modifier: Modifier, value: String, label: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = accent, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Section(title: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
private fun CoverCard(model: Any?, title: String, subtitle: String, playing: Boolean, size: Dp = 148.dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(size)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(size)
                .aspectRatio(1f),
        ) {
            Artwork(model, Modifier.fillMaxSize(), corner = 18.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (playing) MbPalette.BlueBright else Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                if (playing) Equalizer(true, Modifier.size(width = 16.dp, height = 12.dp))
                else Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            fontWeight = FontWeight.Black,
            color = if (rank == 1) MbPalette.BlueBright else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(34.dp),
        )
        Artwork(AudioCover(song.contentUri()), Modifier.size(52.dp), corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.displayArtist(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
        Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
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
            .padding(start = 16.dp, end = 16.dp, top = 22.dp)
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
        MbIconButton(Icons.Rounded.PlayArrow, "Abrir cofre", onOpen, size = 40.dp, tint = Color.White, background = Color.White.copy(alpha = 0.12f))
    }
}
