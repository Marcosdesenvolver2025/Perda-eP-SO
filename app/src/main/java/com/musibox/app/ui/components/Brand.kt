package com.musibox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.ui.theme.MbPalette

/** Logo do MusiBox: barra + "play" em gradiente, com o nome ao lado. */
@Composable
fun MusiLogo(modifier: Modifier = Modifier, iconSize: Dp = 34.dp, showName: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LogoMark(iconSize)
        if (showName) {
            Spacer(Modifier.width(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append("Musi") }
                    withStyle(SpanStyle(color = MbPalette.Blue)) { append("Box") }
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
        }
    }
}

@Composable
fun LogoMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.26f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(MbPalette.PurpleBright, MbPalette.BlueDeep)),
            topLeft = Offset(0f, h * 0.04f),
            size = Size(barW, h * 0.92f),
            cornerRadius = CornerRadius(barW / 2, barW / 2),
        )
        val path = Path().apply {
            moveTo(w * 0.40f, h * 0.18f)
            lineTo(w * 0.98f, h * 0.5f)
            lineTo(w * 0.40f, h * 0.82f)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(MbPalette.BlueBright, MbPalette.BlueDeep)))
    }
}
