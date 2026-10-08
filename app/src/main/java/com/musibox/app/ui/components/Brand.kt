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
import com.musibox.app.download.Platform
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

/** Ícones genéricos das plataformas (formas próprias, não os logotipos oficiais). */
@Composable
fun PlatformIcon(platform: Platform, size: Dp = 52.dp) {
    when (platform) {
        Platform.YOUTUBE -> Box(
            Modifier
                .size(width = size, height = size * 0.72f)
                .clip(RoundedCornerShape(size * 0.22f))
                .background(MbPalette.Red),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.3f)) {
                val w = this.size.width
                val h = this.size.height
                val p = Path().apply {
                    moveTo(w * 0.15f, 0f)
                    lineTo(w, h / 2)
                    lineTo(w * 0.15f, h)
                    close()
                }
                drawPath(p, Color.White)
            }
        }
        Platform.TIKTOK -> Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.26f))
                .background(Color(0xFF0B0B0F)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote, null, tint = Color(0xFF25F4EE),
                modifier = Modifier
                    .offset(x = size * -0.03f, y = size * -0.03f)
                    .size(size * 0.62f),
            )
            Icon(
                Icons.Rounded.MusicNote, null, tint = Color(0xFFFE2C55),
                modifier = Modifier
                    .offset(x = size * 0.03f, y = size * 0.03f)
                    .size(size * 0.62f),
            )
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        }
        Platform.INSTAGRAM -> Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)),
                        start = Offset(0f, Float.POSITIVE_INFINITY),
                        end = Offset(Float.POSITIVE_INFINITY, 0f),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.62f)) {
                val s = this.size.width
                val stroke = Stroke(width = s * 0.09f)
                drawRoundRect(Color.White, size = Size(s, s), cornerRadius = CornerRadius(s * 0.3f), style = stroke)
                drawCircle(Color.White, radius = s * 0.22f, center = Offset(s / 2, s / 2), style = stroke)
                drawCircle(Color.White, radius = s * 0.06f, center = Offset(s * 0.77f, s * 0.23f))
            }
        }
        Platform.LOCAL -> GenericIcon(Icons.Rounded.Folder, size)
        Platform.FACEBOOK, Platform.TWITTER, Platform.OTHER -> GenericIcon(Icons.Rounded.Link, size)
    }
}

@Composable
fun MorePlatformsIcon(size: Dp = 52.dp) = GenericIcon(Icons.Rounded.MoreHoriz, size)

@Composable
private fun GenericIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.55f))
    }
}
