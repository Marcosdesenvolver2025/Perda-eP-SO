package com.musibox.app.ui.themes

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            onReset = { store.setCustomAccent(null); open = false },
            onPick = { store.setCustomAccent(it); open = false },
        )
    }
}

private val WheelHues = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

/** Roda de cores livre (matiz + intensidade) com controle de brilho. */
@Composable
private fun AccentWheelDialog(initial: Color, onDismiss: () -> Unit, onReset: () -> Unit, onPick: (Color) -> Unit) {
    val store = ThemeStore.get(LocalContext.current)
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (initial.red * 255).toInt(), (initial.green * 255).toInt(), (initial.blue * 255).toInt()), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    // brilho mínimo de 45%: cores escuras demais somem no fundo preto
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.45f, 1f)) }
    val color = Color.hsv(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.cardHigh,
        icon = { Icon(Icons.Rounded.Palette, null, tint = color) },
        title = { Text("Cor do app", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth(0.78f)
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
                Spacer(Modifier.height(14.dp))
                Text("Brilho", color = Neo.Text2, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.45f..1f,
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
                )
                Text("Mini player", color = Neo.Text2, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientChip("Com degradê", AppTheme.miniGradient, color, Modifier.weight(1f)) { store.setMiniGradient(true) }
                    GradientChip("Sem degradê", !AppTheme.miniGradient, color, Modifier.weight(1f)) { store.setMiniGradient(false) }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(
                        Modifier
                            .size(width = 120.dp, height = 40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Prévia", color = if (color.luminanceCompat() > 0.45f) Color(0xFF0A0A0A) else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Usar esta cor", color = color, fontWeight = FontWeight.Bold) } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Cor do tema", color = Neo.Text2) }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Neo.Text2) }
            }
        },
    )
}

private fun Color.luminanceCompat(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Botão de escolha do mini player: mostra a amostra (degradê ou cor única). */
@Composable
private fun GradientChip(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val dark = Color(android.graphics.Color.HSVToColor(FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), it); it[1] = minOf(it[1], 0.7f); it[2] = 0.08f }))
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (label.startsWith("Com")) Brush.horizontalGradient(listOf(color, dark)) else androidx.compose.ui.graphics.SolidColor(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f), shape)
            .bounceClick(pressed = 0.95f, shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text((if (selected) "✓ " else "") + label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
