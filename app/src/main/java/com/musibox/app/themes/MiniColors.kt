package com.musibox.app.themes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** Cores do mini player: a cor do tema (a mesma de cima) e, com degradê, indo para o preto. */
object MiniColors {
    /** A cor, só um pouco mais escura quando é clara demais (o texto branco continua legível). */
    fun capLight(c: Color, maxLum: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        var out = c
        var v = hsv[2]
        while (out.luminance() > maxLum && v > 0.3f) {
            v -= 0.03f
            out = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], v)))
        }
        return out
    }

    /** Quase preto, com um toque da cor (fim do degradê). */
    fun nearBlack(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], minOf(hsv[1], 0.7f), 0.08f)))
    }

    fun start(accent: Color): Color = capLight(accent, 0.62f)

    /** [fade] 0 = sem escurecer, 1 = até quase preto. */
    fun end(accent: Color, fade: Float): Color = lerp(start(accent), nearBlack(accent), fade.coerceIn(0f, 1f))

    fun brush(accent: Color, gradient: Boolean, fade: Float): Brush =
        if (gradient) Brush.horizontalGradient(listOf(start(accent), end(accent, fade)))
        else SolidColor(capLight(accent, 0.45f))
}
