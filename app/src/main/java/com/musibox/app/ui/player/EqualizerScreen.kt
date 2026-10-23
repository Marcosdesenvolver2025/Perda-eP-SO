package com.musibox.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.billing.PlayBilling
import com.musibox.app.media.AudioFx
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.navigation.Routes
import kotlin.math.roundToInt

/** Equalizador do player (Pro): presets, faixas, reforço de graves e som 3D. */
@Composable
fun EqualizerScreen(nav: NavController) {
    val info by AudioFx.info.collectAsStateWithLifecycle()
    val s by AudioFx.settings.collectAsStateWithLifecycle()
    val pro by PlayBilling.pro.collectAsStateWithLifecycle()
    val isPro = pro.isActive
    val on = s.enabled && isPro

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Equalizador", onBack = { nav.popBackStack() }) {
            Switch(checked = on, onCheckedChange = { if (isPro) AudioFx.setEnabled(it) else nav.navigate(Routes.PRO) })
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            if (!isPro) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Neo.Green.copy(alpha = 0.14f))
                        .border(1.dp, Neo.Green, RoundedCornerShape(18.dp))
                        .bounceClick(pressed = 0.97f, shape = RoundedCornerShape(18.dp)) { nav.navigate(Routes.PRO) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = Neo.Green, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Recurso do MusiBox Pro", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Experimente os ajustes; o som muda quando você for Pro.", color = Neo.Text2, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Estilos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (AudioFx.PRESETS.map { it.first } + if (s.preset == "Personalizado") listOf("Personalizado") else emptyList()).forEach { name ->
                    val sel = name == s.preset
                    Text(
                        name,
                        color = if (sel) Neo.OnGreen else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (sel) Neo.Green else com.musibox.app.themes.AppTheme.card)
                            .border(1.dp, if (sel) Neo.Green else com.musibox.app.themes.AppTheme.line, RoundedCornerShape(18.dp))
                            .bounceClick(pressed = 0.92f, shape = RoundedCornerShape(18.dp)) { if (name != "Personalizado") AudioFx.setPreset(name) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            val i = info
            if (i == null || i.centersHz.isEmpty()) {
                Text(
                    "Toque uma música para ajustar as faixas do equalizador.",
                    color = Neo.Text2,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                val gains = AudioFx.currentGains(s)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(com.musibox.app.themes.AppTheme.surface)
                        .padding(vertical = 16.dp, horizontal = 6.dp)
                        .alpha(if (on) 1f else 0.6f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    i.centersHz.forEachIndexed { b, hz ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val g = gains.getOrElse(b) { 0 }
                            Text(fmtDb(g), color = if (g != 0) Neo.Green else Neo.Text2, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            VerticalSlider(
                                value = (g - i.minMb).toFloat() / (i.maxMb - i.minMb).coerceAtLeast(1),
                                onChange = { f -> AudioFx.setBand(b, (i.minMb + f * (i.maxMb - i.minMb)).roundToInt()) },
                                modifier = Modifier.size(width = 40.dp, height = 190.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(fmtHz(hz), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Reforço de graves", color = Color.White, fontWeight = FontWeight.Bold)
            Slider(value = s.bass / 1000f, onValueChange = { AudioFx.setBass((it * 1000).roundToInt()) })
            Text("Som 3D (surround)", color = Color.White, fontWeight = FontWeight.Bold)
            Slider(value = s.surround / 1000f, onValueChange = { AudioFx.setSurround((it * 1000).roundToInt()) })
            Text(
                "O som 3D funciona melhor com fones de ouvido.",
                color = Neo.Text2,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { AudioFx.reset() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Restaurar faixas", color = Neo.Green)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun fmtDb(mb: Int): String {
    val db = mb / 100f
    val v = if (db % 1f == 0f) db.toInt().toString() else "%.1f".format(db).replace('.', ',')
    return (if (db > 0) "+" else "") + v + " dB"
}

private fun fmtHz(hz: Int): String = if (hz >= 1000) "${(hz / 100) / 10f}k".replace(".0k", "k").replace('.', ',') else "$hz"

/** Controle deslizante na vertical (cima = mais forte). value em 0..1. */
@Composable
private fun VerticalSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val accent = Neo.Green
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { p -> onChange((1f - p.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    onChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                }
            },
    ) {
        val w = 6.dp.toPx()
        val x = size.width / 2
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val y = bottom - (bottom - top) * value.coerceIn(0f, 1f)
        val mid = (top + bottom) / 2
        drawRoundRect(Color(0xFF3A3A3A), Offset(x - w / 2, top), Size(w, bottom - top), CornerRadius(w / 2))
        drawRoundRect(accent, Offset(x - w / 2, minOf(y, mid)), Size(w, kotlin.math.abs(mid - y)), CornerRadius(w / 2))
        drawLine(Color(0x55FFFFFF), Offset(x - 10.dp.toPx(), mid), Offset(x + 10.dp.toPx(), mid), strokeWidth = 1.dp.toPx())
        drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, y))
        drawCircle(accent, radius = 6.dp.toPx(), center = Offset(x, y))
    }
}
