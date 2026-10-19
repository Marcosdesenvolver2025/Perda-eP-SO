package com.musibox.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.musibox.app.ui.components.bounceClick
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.media.AudioCover
import com.musibox.app.media.PlayerState
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.Neo
import kotlinx.coroutines.launch

/** Mini player em vidro: capa, música, favoritar, play/pause, próxima e barra de progresso. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val current = state.current ?: return
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle(initialValue = emptySet())
    val isFav = current.songKey in favorites
    val shape = RoundedCornerShape(22.dp)
    // Arrastar para o lado fecha o player (como o X).
    val dragX = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    Box(
        modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(dragX.value.roundToInt(), 0) }
            .graphicsLayer {
                val f = (abs(dragX.value) / width).coerceIn(0f, 1f)
                alpha = 1f - f * 0.9f
                rotationZ = dragX.value / width * 4f
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(dragX.value) > width * 0.35f) {
                                dragX.animateTo(if (dragX.value > 0) width.toFloat() else -width.toFloat(), tween(180))
                                onClose()
                                dragX.snapTo(0f)
                            } else {
                                dragX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + dx) }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF1E1E1E))))
                .border(1.dp, Color(0xFF2E2E2E), shape)
                .clickable(onClick = onOpen),
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(current.uri?.let { AudioCover(it) }, Modifier.size(50.dp), corner = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Neo.Artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(modifier = Modifier.size(42.dp), onClick = {
                    scope.launch {
                        container.library.setFavorite(
                            current.songKey, current.looseKey, current.title, current.artist, current.album,
                            current.durationMs, !isFav,
                        )
                    }
                }) {
                    Icon(
                        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isFav) "Remover dos favoritos" else "Favoritar",
                        tint = if (isFav) com.musibox.app.ui.components.Neo.Green else Color(0xFFFFFFFF),
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Tocar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Próxima", tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                }
                // X: fecha o player; ele volta sozinho quando você tocar uma música de novo
                Box(
                    Modifier
                        .padding(start = 2.dp, end = 4.dp)
                        .size(40.dp)
                        .bounceClick(pressed = 0.8f, shape = CircleShape, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Fechar player", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            val target = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            val progress by animateFloatAsState(target, tween(450, easing = LinearEasing), label = "miniProgress")
            Box(
                Modifier
                    .padding(start = 72.dp, end = 18.dp, bottom = 10.dp, top = 2.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3F3F3F)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(com.musibox.app.ui.components.Neo.Green),
                )
            }
        }
    }
}
