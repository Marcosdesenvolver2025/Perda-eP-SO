package com.musibox.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.random.Random

/**
 * Fundo do app inteiro: nebulosas suaves em azul e roxo, um campo de estrelas e um piso de
 * grade em perspectiva no rodapé. É estático (desenhado uma vez e guardado em cache), então
 * não gasta bateria.
 */
@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val dark = bg.luminance() < 0.3f
    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val rnd = Random(2077)
                val stars = List(90) {
                    Triple(
                        Offset(rnd.nextFloat() * w, rnd.nextFloat() * h),
                        (0.5f + rnd.nextFloat() * 1.3f) * density,
                        0.08f + rnd.nextFloat() * 0.32f,
                    )
                }
                val nebulaBlue = Brush.radialGradient(
                    listOf(Color(0x402F6BFF), Color.Transparent),
                    center = Offset(w * 0.1f, h * 0.08f),
                    radius = w * 0.95f,
                )
                val nebulaPurple = Brush.radialGradient(
                    listOf(Color(0x3A8B3DFF), Color.Transparent),
                    center = Offset(w * 1.0f, h * 0.5f),
                    radius = w * 0.85f,
                )
                val nebulaCyan = Brush.radialGradient(
                    listOf(Color(0x28E03DFF), Color.Transparent),
                    center = Offset(w * 0.35f, h * 1.02f),
                    radius = w * 0.9f,
                )
                val horizon = h * 0.74f
                val gridColor = Color(0xFF8B7BFF)
                onDrawBehind {
                    drawRect(bg)
                    if (!dark) return@onDrawBehind
                    drawRect(nebulaBlue)
                    drawRect(nebulaPurple)
                    drawRect(nebulaCyan)
                    stars.forEach { (c, r, a) -> drawCircle(Color.White.copy(alpha = a), r, c) }
                    // piso de grade com ponto de fuga no centro do horizonte
                    val vx = w / 2f
                    for (i in -8..8) {
                        val x = vx + i * w * 0.16f
                        drawLine(
                            gridColor.copy(alpha = 0.05f),
                            Offset(vx + i * w * 0.02f, horizon),
                            Offset(x, h),
                            strokeWidth = 1f * density,
                        )
                    }
                    var k = 0
                    var y = horizon
                    var step = 4f * density
                    while (y < h && k < 30) {
                        drawLine(gridColor.copy(alpha = 0.035f + k * 0.002f), Offset(0f, y), Offset(w, y), strokeWidth = 1f * density)
                        step *= 1.32f
                        y += step
                        k++
                    }
                }
            },
    )
}
