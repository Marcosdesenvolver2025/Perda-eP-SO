package com.musibox.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/** Encolhe um pouco com mola ao tocar (efeito "botão de verdade"). */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.93f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.45f, stiffness = 650f),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** clickable + encolher com mola ao tocar. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressed: Float = 0.93f,
    shape: Shape? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .pressScale(interaction, pressed)
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Guarda quais itens já fizeram a animação de entrada (para não repetir ao rolar de volta). */
class IntroState {
    val seen = HashSet<Any>()
    val createdAt = SystemClock.uptimeMillis()
}

@Composable
fun rememberIntroState(vararg keys: Any?): IntroState = remember(*keys) { IntroState() }

/**
 * Entrada suave: o item sobe e aparece. Na primeira tela os itens entram em cascata;
 * ao rolar, cada item novo aparece uma vez só.
 */
fun Modifier.intro(key: Any, index: Int, state: IntroState, distance: Dp = 26.dp): Modifier = composed {
    val first = remember(key, state) { key !in state.seen }
    val progress = remember(key, state) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key, state) {
        if (first) {
            state.seen += key
            val sinceStart = SystemClock.uptimeMillis() - state.createdAt
            if (sinceStart < 700) delay(index.coerceIn(0, 12) * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
        }
    }
    graphicsLayer {
        val v = progress.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * distance.toPx()
        val s = 0.96f + 0.04f * v
        scaleX = s
        scaleY = s
    }
}

/** Barrinhas de equalizador: mexem só enquanto a música toca. */
@Composable
fun Equalizer(playing: Boolean, modifier: Modifier = Modifier, color: Color = Neo.Cyan, size: Dp = 18.dp) {
    val phase = if (playing) {
        val t = rememberInfiniteTransition(label = "eq")
        t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "eqPhase").value
    } else {
        0f
    }
    Canvas(modifier.size(size)) {
        val bars = 3
        val gap = this.size.width / (bars * 2 - 1)
        for (i in 0 until bars) {
            val h = if (playing) 0.3f + 0.7f * abs(sin(phase + i * 1.3f)) else 0.35f + 0.2f * i
            val bh = this.size.height * h
            drawRoundRect(
                Brush.verticalGradient(listOf(color, com.musibox.app.ui.components.Neo.Green)),
                topLeft = Offset(i * gap * 2, this.size.height - bh),
                size = Size(gap, bh),
                cornerRadius = CornerRadius(gap / 2),
            )
        }
    }
}
