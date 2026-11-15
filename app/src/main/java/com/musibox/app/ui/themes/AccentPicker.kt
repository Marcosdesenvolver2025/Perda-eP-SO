package com.musibox.app.ui.themes

import androidx.compose.foundation.Canvas
import com.musibox.app.themes.MiniColors
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.themes.AppTheme
import com.musibox.app.themes.ThemeStore
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.bounceClick
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Linha "Cor do app" (Temas): mostra a cor atual e abre a roda de cores.
 * Só Go, Plus e Pro escolhem; sem plano, o toque leva para os planos.
 */
@Composable
fun AccentPickerRow(canPick: Boolean, onNeedPlan: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ThemeStore.get(context) }
    var open by remember { mutableStateOf(false) }
    val current = AppTheme.customAccent
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.card)
            .border(1.dp, AppTheme.line, shape)
            .bounceClick(pressed = 0.97f, shape = shape) { if (canPick) open = true else onNeedPlan() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(WheelHues)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(current ?: AppTheme.themeAccent)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Cor do app", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                when {
                    !canPick -> "Escolha qualquer cor com o Go, Plus ou Pro"
                    current == null -> "Seguindo a cor do tema • toque para escolher"
                    else -> "Cor escolhida por você • toque para trocar"
                },
                color = Neo.Text2,
                fontSize = 12.sp,
            )
        }
        Icon(
            if (canPick) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
            null,
            tint = if (canPick) Neo.Text2 else Color(0xFFFFC857),
            modifier = Modifier.size(if (canPick) 24.dp else 18.dp),
        )
    }
    if (open) {
        AccentWheelDialog(
            initial = current ?: AppTheme.themeAccent,
            onDismiss = { open = false },
            onReset = { grad, fade ->
                store.setMiniGradient(grad); store.setMiniFade(fade)
                store.setCustomAccent(null); open = false
            },
            onPick = { c, grad, fade ->
                store.setMiniGradient(grad); store.setMiniFade(fade)
                store.setCustomAccent(c); open = false
            },
        )
    }
}

private val WheelHues = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

/**
 * Roda de cores livre (matiz + intensidade), brilho, degradê do mini player e prévia.
 * Nada muda no app até tocar em "Usar esta cor" (ou "Cor do tema").
 */
@Composable
private fun AccentWheelDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onReset: (gradient: Boolean, fade: Float) -> Unit,
    onPick: (Color, gradient: Boolean, fade: Float) -> Unit,
) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (initial.red * 255).toInt(), (initial.green * 255).toInt(), (initial.blue * 255).toInt()), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    // brilho mínimo de 45%: cores escuras demais somem no fundo preto
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.45f, 1f)) }
    var gradient by remember { mutableStateOf(AppTheme.miniGradient) }
    var fade by remember { mutableFloatStateOf(AppTheme.miniFade) }
    val color = Color.hsv(hue, sat, value)
    val green = AppTheme.DefaultAccent

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AppTheme.cardHigh)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Palette, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("Cor do app", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            ) {
                fun pick(pos: Offset, w: Float, h: Float) {
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = min(cx, cy)
                    val dx = pos.x - cx
                    val dy = pos.y - cy
                    val ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    hue = (ang + 360f) % 360f
                    sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ -> pick(change.position, size.width.toFloat(), size.height.toFloat()) }
                        },
                ) {
                    val r = min(size.width, size.height) / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Brush.sweepGradient(WheelHues, c), r, c)
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), c, r), r, c)
                    // escurece a roda conforme o brilho escolhido
                    drawCircle(Color.Black.copy(alpha = 1f - value), r, c)
                    val rad = Math.toRadians(hue.toDouble())
                    val p = Offset(c.x + (cos(rad) * sat * r).toFloat(), c.y + (sin(rad) * sat * r).toFloat())
                    drawCircle(color, 14.dp.toPx(), p)
                    drawCircle(Color.White, 14.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                }
            }
            Spacer(Modifier.height(16.dp))

            LabelRow("Brilho", "${(value * 100).toInt()}%")
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0.45f..1f,
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.25f)),
            )

            // Degradê: até onde a cor escurece no mini player (só com "Com degradê")
            LabelRow("Degradê", if (gradient) "${(fade * 100).toInt()}%" else "desligado")
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.horizontalGradient(listOf(MiniColors.start(color), MiniColors.nearBlack(color))))
                        .then(if (gradient) Modifier else Modifier.background(Color.Black.copy(alpha = 0.55f))),
                )
                Slider(
                    value = fade,
                    onValueChange = { fade = it },
                    valueRange = 0.2f..1f,
                    enabled = gradient,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        disabledThumbColor = Color.White.copy(alpha = 0.35f),
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent,
                    ),
                )
            }

            Spacer(Modifier.height(6.dp))
            LabelRow("Mini player", "")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientChip("Com degradê", gradient, MiniColors.brush(color, true, fade), Modifier.weight(1f)) { gradient = true }
                GradientChip("Sem degradê", !gradient, MiniColors.brush(color, false, fade), Modifier.weight(1f)) { gradient = false }
            }

            Spacer(Modifier.height(16.dp))
            LabelRow("Prévia", "")
            Spacer(Modifier.height(8.dp))
            MiniPreview(color, gradient, fade)

            Spacer(Modifier.height(22.dp))
            // Botões organizados: principal em cima, os outros dois lado a lado
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(green)
                    .bounceClick(pressed = 0.96f, shape = RoundedCornerShape(27.dp)) { onPick(color, gradient, fade) },
                contentAlignment = Alignment.Center,
            ) { Text("Usar esta cor", color = Color(0xFF06140B), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Cor do tema", Modifier.weight(1f)) { onReset(gradient, fade) }
                OutlineButton("Cancelar", Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun LabelRow(label: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neo.Text2, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (detail.isNotEmpty()) Text(detail, color = Neo.Text2, fontSize = 13.sp)
    }
}

@Composable
private fun OutlineButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(25.dp)
    Box(
        modifier
            .height(50.dp)
            .clip(shape)
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), shape)
            .bounceClick(pressed = 0.96f, shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

/** Mini player de exemplo, muda na hora com a cor, o brilho e o degradê. */
@Composable
private fun MiniPreview(color: Color, gradient: Boolean, fade: Float) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(MiniColors.brush(color, gradient, fade))
            .border(1.dp, MiniColors.start(color), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sua música", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text("Artista", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.Pause, null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

/** Botão de escolha do mini player: mostra a amostra (degradê ou cor única). */
@Composable
private fun GradientChip(label: String, selected: Boolean, brush: Brush, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(brush)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f), shape)
            .bounceClick(pressed = 0.95f, shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text((if (selected) "✓ " else "") + label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
