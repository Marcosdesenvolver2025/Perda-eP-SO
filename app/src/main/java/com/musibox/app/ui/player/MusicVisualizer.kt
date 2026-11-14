package com.musibox.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musibox.app.media.AudioLevels
import kotlin.math.sin

/**
 * Visualizador (Plus): barras que pulsam com a música que está tocando.
 * Graves à esquerda, médios no meio e agudos à direita.
 */
@Composable
fun MusicVisualizer(playing: Boolean, color: Color, modifier: Modifier = Modifier, height: Dp = 44.dp, bars: Int = 28) {
    DisposableEffect(Unit) {
        AudioLevels.listening = true
        onDispose { AudioLevels.listening = false }
    }
    val heights = remember { FloatArray(bars) }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        while (true) {
            withFrameNanos { now ->
                val t = now / 1_000_000_000.0
                for (i in 0 until bars) {
                    val x = i / (bars - 1f)
                    val band = when {
                        x < 0.35f -> AudioLevels.bass
                        x < 0.7f -> AudioLevels.mid * 0.6f + AudioLevels.bass * 0.4f * (1f - (x - 0.35f) / 0.35f)
                        else -> AudioLevels.treble * 0.7f + AudioLevels.mid * 0.3f
                    }
                    // um pouco de vida em cada barra para não ficarem todas iguais
                    val wobble = 0.75f + 0.25f * sin(t * (3.0 + i * 0.7) + i * 1.3).toFloat()
                    val target = if (playing) (band * wobble).coerceIn(0.04f, 1f) else 0.04f
                    heights[i] += (target - heights[i]) * if (target > heights[i]) 0.55f else 0.18f
                }
                tick.longValue = now
            }
        }
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        tick.longValue // redesenha a cada quadro
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))
        for (i in 0 until bars) {
            val h = (heights[i] * size.height).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                brush,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}
