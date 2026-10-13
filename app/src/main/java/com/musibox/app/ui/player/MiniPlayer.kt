package com.musibox.app.ui.player

import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier,
) {
    val current = state.current ?: return
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val favorites by container.library.favoriteKeys.collectAsStateWithLifecycle(initialValue = emptySet())
    val isFav = current.songKey in favorites
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = Color(0xFF0F4BFF), spotColor = Color(0xFF0F4BFF))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF0D1C34), Color(0xFF071021))))
                .border(1.dp, Brush.linearGradient(listOf(Color(0xFF2D5C9C), Color(0xFF1A2C4E), Color(0xFF3A3C8C))), shape)
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
                IconButton(onClick = {
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
                        tint = if (isFav) Color(0xFFFF4D8D) else Color(0xFF9FC8F5),
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Tocar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, "Próxima", tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(30.dp))
                }
            }
            val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .padding(start = 72.dp, end = 18.dp, bottom = 10.dp, top = 2.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1A2A44)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1E6BFF), Color(0xFF7FD4FF)))),
                )
            }
        }
    }
}
