package com.musibox.app.ui.download

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.musibox.app.download.Platform
import com.musibox.app.ui.components.PlatformIcon

/** Sites que abrem no navegador do MusiBox (ícones genéricos, não os logotipos oficiais). */
data class Site(
    val name: String,
    val url: String,
    val colors: List<Color>,
    val mark: String = "",
    val platform: Platform = Platform.OTHER,
)

val DownloadSites = listOf(
    Site("YouTube", "https://m.youtube.com", listOf(Color(0xFFFF2D3D), Color(0xFFD90F1F)), platform = Platform.YOUTUBE),
    Site("YT Music", "https://music.youtube.com", listOf(Color(0xFFFF4757), Color(0xFFB3001B)), mark = "♪"),
    Site("TikTok", "https://www.tiktok.com", listOf(Color(0xFF111116), Color(0xFF000000)), platform = Platform.TIKTOK),
    Site("Instagram", "https://www.instagram.com", emptyList(), platform = Platform.INSTAGRAM),
    Site("Facebook", "https://m.facebook.com", listOf(Color(0xFF3B8BFF), Color(0xFF1864E8)), mark = "f"),
    Site("X", "https://x.com", listOf(Color(0xFF2A2F3A), Color(0xFF0B0D12)), mark = "X"),
    Site("SoundCloud", "https://m.soundcloud.com", listOf(Color(0xFFFF8A3D), Color(0xFFFF4E00)), mark = "SC"),
    Site("Kwai", "https://www.kwai.com", listOf(Color(0xFFFFB13D), Color(0xFFFF6A00)), mark = "K"),
    Site("Pinterest", "https://www.pinterest.com", listOf(Color(0xFFFF4060), Color(0xFFC8001E)), mark = "P"),
    Site("Vimeo", "https://vimeo.com", listOf(Color(0xFF3CCBF4), Color(0xFF1196C9)), mark = "V"),
    Site("Dailymotion", "https://www.dailymotion.com", listOf(Color(0xFF3A8BFF), Color(0xFF0050C8)), mark = "D"),
    Site("Twitch", "https://m.twitch.tv", listOf(Color(0xFFA77BFF), Color(0xFF6A2BE0)), mark = "T"),
)

@Composable
fun SiteIcon(site: Site, size: Dp) {
    if (site.platform != Platform.OTHER) {
        PlatformIcon(site.platform, size)
        return
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(site.colors, start = Offset.Zero, end = Offset.Infinite)),
        contentAlignment = Alignment.Center,
    ) {
        if (site.mark == "♪") {
            // Círculo com "play" (estilo player de música)
            Canvas(Modifier.size(size * 0.56f)) {
                val s = this.size.width
                drawCircle(Color.White, radius = s / 2, style = Stroke(width = s * 0.09f))
                val p = Path().apply {
                    moveTo(s * 0.40f, s * 0.30f)
                    lineTo(s * 0.72f, s * 0.50f)
                    lineTo(s * 0.40f, s * 0.70f)
                    close()
                }
                drawPath(p, Color.White)
            }
        } else {
            Text(
                site.mark,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * if (site.mark.length > 1) 0.34f else 0.46f).sp,
            )
        }
    }
}
