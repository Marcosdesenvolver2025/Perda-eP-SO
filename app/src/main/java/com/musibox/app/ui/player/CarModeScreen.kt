package com.musibox.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.themes.AppTheme
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.bounceClick

/**
 * Modo carro (Pro): tela simples com botões enormes e letras grandes, para usar no suporte do carro
 * sem precisar mirar. A tela fica acesa enquanto está aberta.
 */
@Composable
fun CarModeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val state by container.player.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val accent = AppTheme.themeAccent
    val onAccent = if (0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue > 0.45f) Color(0xFF0A0A0A) else Color.White
    val current = state.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DirectionsCar, null, tint = accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text("Modo carro", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .bounceClick(pressed = 0.9f, shape = CircleShape) { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Sair do modo carro", tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                current?.title ?: "Nada tocando",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                current?.displayArtist.orEmpty(),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(accent),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarButton(Icons.Rounded.SkipPrevious, "Anterior", 96.dp, Color.White.copy(alpha = 0.14f), Color.White) { container.player.previous() }
            CarButton(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) "Pausar" else "Tocar",
                128.dp,
                accent,
                onAccent,
            ) { container.player.togglePlayPause() }
            CarButton(Icons.Rounded.SkipNext, "Próxima", 96.dp, Color.White.copy(alpha = 0.14f), Color.White) { container.player.next() }
        }
    }
}

@Composable
private fun CarButton(icon: ImageVector, label: String, size: Dp, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .bounceClick(pressed = 0.88f, shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = fg, modifier = Modifier.size(size * 0.55f)) }
}
