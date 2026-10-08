package com.musibox.app.ui.vault

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.musibox.app.core.Fmt
import com.musibox.app.core.findActivity
import com.musibox.app.data.db.VaultCategory
import com.musibox.app.data.db.VaultItemEntity
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.theme.Mb
import com.musibox.app.vault.VaultDataSource
import com.musibox.app.vault.VaultImage
import com.musibox.app.vault.VaultThumb
import com.musibox.app.vault.vaultUri
import kotlinx.coroutines.delay

// ------------------------------------------------------------------
// Visualizador de fotos

@Composable
fun VaultViewerScreen(nav: NavController, id: String) {
    VaultGate { VaultViewer(nav, id) }
}

private sealed interface ViewerDialog {
    data class Export(val id: String, val remove: Boolean) : ViewerDialog
    data class Share(val id: String) : ViewerDialog
    data class Delete(val id: String) : ViewerDialog
}

@Composable
private fun VaultViewer(nav: NavController, id: String) {
    val vm = appViewModel { VaultViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val list = items
    when {
        list == null -> Box(Modifier.fillMaxSize().background(Color.Black)) { LoadingState("Abrindo…") }
        else -> {
            val photos = list.filter { it.category == VaultCategory.PHOTO }
            if (photos.isEmpty()) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    MbIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", { nav.popBackStack() })
                    StateMessage(Icons.Rounded.Image, "Nenhuma foto no cofre")
                }
            } else {
                PhotoPager(nav, vm, photos, photos.indexOfFirst { it.id == id }.coerceAtLeast(0))
            }
        }
    }
}

@Composable
private fun PhotoPager(nav: NavController, vm: VaultViewModel, photos: List<VaultItemEntity>, start: Int) {
    val snack = rememberSnack()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, photos.lastIndex)) { photos.size }
    var showUi by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<ViewerDialog?>(null) }
    val current = photos.getOrNull(pager.currentPage)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(pager, key = { photos[it].id }, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(photos[page], onTap = { showUi = !showUi })
        }
        if (showUi && current != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${pager.currentPage + 1} de ${photos.size} • ${Fmt.size(current.size)}" +
                            if (current.width > 0) " • ${current.width}×${current.height}" else "",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerAction(Icons.Rounded.Share, "Compartilhar") { dialog = ViewerDialog.Share(current.id) }
                ViewerAction(Icons.Rounded.Upload, "Exportar") { dialog = ViewerDialog.Export(current.id, false) }
                ViewerAction(Icons.Rounded.LockOpen, "Remover") { dialog = ViewerDialog.Export(current.id, true) }
                ViewerAction(Icons.Rounded.DeleteForever, "Excluir", Color(0xFFFF6B6B)) { dialog = ViewerDialog.Delete(current.id) }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is ViewerDialog.Export -> ConfirmDialog(
            if (d.remove) "Remover do cofre?" else "Exportar?",
            EXPORT_WARNING,
            "Continuar",
            onConfirm = { dialog = null; vm.export(listOf(d.id), d.remove) { snack(it) } },
            onDismiss = { dialog = null },
        )
        is ViewerDialog.Share -> ConfirmDialog(
            "Compartilhar?",
            EXPORT_WARNING,
            "Continuar",
            onConfirm = { dialog = null; vm.handOff(d.id, share = true) { snack(it) } },
            onDismiss = { dialog = null },
        )
        is ViewerDialog.Delete -> ConfirmDialog(
            "Excluir permanentemente?",
            "A foto será apagada do cofre. Esta ação não pode ser desfeita.",
            "Excluir",
            destructive = true,
            onConfirm = { dialog = null; vm.delete(listOf(d.id)) { snack(it) } },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

/** Imagem com zoom (pinça e toque duplo) que não atrapalha a troca de páginas. */
@Composable
private fun ZoomableImage(item: VaultItemEntity, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.size > 1
                        if (multi || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = VaultImage(item.id, item.mimeType),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

// ------------------------------------------------------------------
// Player de vídeos e áudios do cofre

@Composable
fun VaultPlayerScreen(nav: NavController, id: String) {
    VaultGate { VaultPlayer(nav, id) }
}

private val vaultSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@OptIn(UnstableApi::class)
@Composable
private fun VaultPlayer(nav: NavController, initialId: String) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm = appViewModel { VaultViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    var currentId by rememberSaveable { mutableStateOf(initialId) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val current = items?.firstOrNull { it.id == currentId }
    val list = items.orEmpty().filter { it.category == (current?.category ?: VaultCategory.VIDEO) }
    val isVideo = current?.category == VaultCategory.VIDEO
    val latestList by rememberUpdatedState(list)

    val exo = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VaultDataSource.Factory(container.vault)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = exo.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) {
                    val all = latestList
                    val idx = all.indexOfFirst { it.id == currentId }
                    if (idx in 0 until all.lastIndex) currentId = all[idx + 1].id
                }
            }

            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = "Não foi possível reproduzir este arquivo. Formato não suportado."
            }
        }
        exo.addListener(listener)
        container.player.pause()
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    LaunchedEffect(currentId, current != null) {
        if (current == null) return@LaunchedEffect
        error = null
        exo.setMediaItem(MediaItem.Builder().setUri(vaultUri(current.id)).setMediaId(current.id).build())
        exo.prepare()
        exo.play()
    }
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition.coerceAtLeast(0)
            if (exo.duration > 0) duration = exo.duration
            delay(300)
        }
    }

    val activity = context.findActivity()
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    if (items == null) {
        LoadingState("Abrindo…")
        return
    }
    if (current == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MbIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", { nav.popBackStack() })
            StateMessage(Icons.Rounded.MusicNote, "Arquivo não encontrado no cofre.")
        }
        return
    }

    val videoSurface: @Composable (Modifier) -> Unit = { modifier ->
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = exo },
            modifier = modifier.background(Color.Black),
        )
    }

    val controls: @Composable () -> Unit = {
        val dur = duration.coerceAtLeast(1)
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = dragging ?: (position.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { exo.seekTo((it * dur).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(thumbColor = Mb.colors.vault, activeTrackColor = Mb.colors.vault),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.duration(dragging?.let { (it * dur).toLong() } ?: position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.duration(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { speedMenu = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Speed, "Velocidade", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${speed}x".replace(".0x", "x"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DropdownMenu(speedMenu, { speedMenu = false }, containerColor = Mb.colors.cardHigh) {
                        vaultSpeeds.forEach { sp ->
                            DropdownMenuItem(text = { Text("${sp}x".replace(".0x", "x")) }, onClick = {
                                speedMenu = false
                                speed = sp
                                exo.playbackParameters = PlaybackParameters(sp)
                            })
                        }
                    }
                }
                IconButton(onClick = {
                    val idx = list.indexOfFirst { it.id == currentId }
                    if (exo.currentPosition > 3000 || idx <= 0) exo.seekTo(0) else currentId = list[idx - 1].id
                }) { Icon(Icons.Rounded.SkipPrevious, "Anterior", modifier = Modifier.size(34.dp)) }
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Mb.colors.vaultGradient)
                        .clickable { if (exo.isPlaying) exo.pause() else { if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0); exo.play() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pausar" else "Tocar", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = {
                    val idx = list.indexOfFirst { it.id == currentId }
                    if (idx in 0 until list.lastIndex) currentId = list[idx + 1].id
                }) { Icon(Icons.Rounded.SkipNext, "Próximo", modifier = Modifier.size(34.dp)) }
                IconButton(onClick = { fullscreen = !fullscreen }, enabled = isVideo) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        if (fullscreen) "Sair da tela cheia" else "Tela cheia",
                        tint = if (isVideo) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    )
                }
            }
        }
    }

    if (fullscreen && isVideo) {
        var showControls by remember { mutableStateOf(true) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onTap = { showControls = !showControls }) },
        ) {
            videoSurface(Modifier.fillMaxSize())
            if (showControls) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) { controls() }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.KeyboardArrowDown, "Fechar") }
            Text(
                if (isVideo) "Vídeo do cofre" else "Áudio do cofre",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (isVideo) {
                        val ratio = if (current.width > 0 && current.height > 0) current.width.toFloat() / current.height else 16f / 9f
                        videoSurface(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio.coerceIn(0.5f, 2.4f))
                                .clip(RoundedCornerShape(18.dp)),
                        )
                    } else {
                        Artwork(
                            if (current.hasThumb) VaultThumb(current.id) else null,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            corner = 24.dp,
                            fallbackIcon = Icons.Rounded.MusicNote,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(current.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Mb.colors.danger, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    controls()
                    Spacer(Modifier.height(20.dp))
                    Text(if (isVideo) "Todos os vídeos" else "Todos os áudios", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(list, key = { it.id }) { item ->
                val selected = item.id == currentId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { currentId = item.id }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        if (item.hasThumb) VaultThumb(item.id) else null,
                        Modifier.size(width = 72.dp, height = 56.dp),
                        corner = 10.dp,
                        fallbackIcon = if (isVideo) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            color = if (selected) Mb.colors.vault else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${Fmt.duration(item.durationMs)}  |  ${Fmt.size(item.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
