package com.musibox.app.ui.download

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.R
import com.musibox.app.ui.main.LocalPageActive
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class HowSlide(
    val step: String,
    val title: String,
    val text: String,
    val colors: List<Color>,
    val art: @Composable (active: Boolean) -> Unit,
)

/**
 * Carrossel "Como funciona": mostra, passo a passo, as formas de baixar. Passa sozinho
 * a cada poucos segundos e também dá para deslizar.
 */
@Composable
fun HowItWorksCarousel(modifier: Modifier = Modifier, onAction: (Int) -> Unit = {}) {
    val slides =
        listOf(
            HowSlide(
                "1", "Cole o link",
                "Copiou um link do YouTube, TikTok ou Instagram? Toque em Colar e escolha música ou vídeo.",
                listOf(Color(0xFF2F6BFF), Color(0xFF8B3DFF)),
            ) { active -> PasteArt(active) },
            HowSlide(
                "2", "Compartilhe direto do app",
                "No vídeo, toque em Compartilhar e escolha \"Baixar com MusiBox\".",
                listOf(Color(0xFFFF2D7A), Color(0xFFFF8A00)),
            ) { active -> ShareArt(active) },
            HowSlide(
                "3", "Navegue e baixe",
                "Abra o Navegador, entre no vídeo e toque no botão Baixar.",
                listOf(Color(0xFF00B894), Color(0xFF0984E3)),
            ) { active -> BrowseArt(active) },
            HowSlide(
                "4", "Música ou vídeo, galeria ou cofre",
                "Escolha o formato e onde salvar: na galeria do celular ou escondido no cofre.",
                listOf(Color(0xFF9B3DFF), Color(0xFFFF3DB8)),
            ) { active -> ChooseArt(active) },
            HowSlide(
                "5", "Acompanhe em tempo real",
                "O progresso aparece aqui embaixo e na notificação, até \"Download concluído\".",
                listOf(Color(0xFFFFB300), Color(0xFFFF4D4D)),
            ) { active -> ProgressArt(active) },
        )
    val pager = rememberPagerState { slides.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val pageActive = LocalPageActive.current
    // Passa sozinho (para quando a pessoa está arrastando ou saiu da tela)
    LaunchedEffect(dragged, pageActive) {
        if (dragged || !pageActive) return@LaunchedEffect
        while (true) {
            delay(4500)
            pager.animateScrollToPage((pager.currentPage + 1) % slides.size, animationSpec = tween(650))
        }
    }

    Column(modifier) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val slide = slides[page]
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .graphicsLayer {
                        val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        val a = abs(off)
                        scaleY = 1f - a * 0.08f
                        alpha = 1f - a * 0.35f
                        rotationY = off * 10f
                        cameraDistance = 16f * density
                    }
                    .shadow(16.dp, shape, ambientColor = slide.colors[0], spotColor = slide.colors[0])
                    .clip(shape)
                    .background(Brush.linearGradient(slide.colors.map { it.copy(alpha = 0.92f) }))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
            ) {
                // brilho
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), radius = 520f)),
                )
                Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text("PASSO ${slide.step}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(slide.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 23.sp, maxLines = 2)
                        Spacer(Modifier.height(6.dp))
                        Text(slide.text, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
                        slide.art(pager.currentPage == page)
                    }
                }
            }
        }
        // Indicadores
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(slides.size) { i ->
                val selected = pager.currentPage == i
                val w by animateDpAsState(if (selected) 22.dp else 7.dp, spring(dampingRatio = 0.6f), label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = w, height = 7.dp)
                        .clip(CircleShape)
                        .background(if (selected) Brush.horizontalGradient(slides[i].colors) else Brush.linearGradient(listOf(Color(0x55FFFFFF), Color(0x55FFFFFF)))),
                )
            }
        }
    }
}

// ---------------- Ilustrações animadas (tocam ao chegar no slide) ----------------

@Composable
private fun rememberPlay(active: Boolean): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            a.snapTo(0f)
            a.animateTo(1f, tween(1200))
        }
    }
    return a.value
}

@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(width = 76.dp, height = 118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B0B1E))
            .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Bubble(icon: ImageVector, size: Int = 34, bg: Color = Color.White, tint: Color = Color(0xFF2F6BFF)) {
    Box(
        Modifier
            .size(size.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.55f).dp)) }
}

@Composable
private fun PasteArt(active: Boolean) {
    val t = rememberPlay(active)
    PhoneFrame {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(t.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF7FB2FF)),
                )
            }
            Spacer(Modifier.height(10.dp))
            Icon(Icons.Rounded.Link, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
    Box(Modifier.offset(x = 34.dp, y = 34.dp).graphicsLayer { scaleX = 0.6f + 0.4f * t; scaleY = 0.6f + 0.4f * t }) {
        Bubble(Icons.Rounded.ContentPaste)
    }
}

@Composable
private fun ShareArt(active: Boolean) {
    val t = rememberPlay(active)
    PhoneFrame {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Smartphone, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .graphicsLayer { alpha = t; translationY = (1f - t) * 30f }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.logo_note3d), null, Modifier.size(14.dp))
                    Text(" Baixar", color = Color(0xFF111133), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Box(Modifier.offset(x = -34.dp, y = 36.dp)) { Bubble(Icons.Rounded.Share, bg = Color.White, tint = Color(0xFFFF2D7A)) }
}

@Composable
private fun BrowseArt(active: Boolean) {
    val t = rememberPlay(active)
    Image(
        painterResource(R.drawable.ic3d_browser),
        null,
        Modifier
            .size(84.dp)
            .graphicsLayer { rotationZ = -20f + 20f * t },
    )
    Box(
        Modifier
            .offset(x = 30.dp, y = 40.dp)
            .graphicsLayer { scaleX = 0.5f + 0.5f * t; scaleY = 0.5f + 0.5f * t; alpha = t }
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Download, null, tint = Color(0xFF0984E3), modifier = Modifier.size(14.dp))
            Text(" Baixar", color = Color(0xFF0984E3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
    Box(Modifier.offset(x = 44.dp, y = 8.dp).graphicsLayer { alpha = t }) {
        Icon(Icons.Rounded.TouchApp, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ChooseArt(active: Boolean) {
    val t = rememberPlay(active)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.graphicsLayer { alpha = t.coerceIn(0f, 1f) }) { Bubble(Icons.Rounded.MusicNote, 40, Color.White, Color(0xFF9B3DFF)) }
            Box(Modifier.graphicsLayer { alpha = (t * 1.4f - 0.2f).coerceIn(0f, 1f) }) { Bubble(Icons.Rounded.Videocam, 40, Color.White, Color(0xFFFF3DB8)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.graphicsLayer { alpha = (t * 1.6f - 0.5f).coerceIn(0f, 1f) }) {
                Image(painterResource(R.drawable.ic3d_home), null, Modifier.size(40.dp))
            }
            Box(Modifier.graphicsLayer { alpha = (t * 1.8f - 0.7f).coerceIn(0f, 1f) }) {
                Image(painterResource(R.drawable.ic3d_vault), null, Modifier.size(40.dp))
            }
        }
    }
    Box(Modifier.offset(x = 44.dp, y = -46.dp).graphicsLayer { alpha = t }) {
        Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ProgressArt(active: Boolean) {
    val t = rememberPlay(active)
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(12.dp))
                    Text(
                        if (t >= 1f) " Concluído ✓" else " Baixando… ${(t * 100).toInt()}%",
                        color = Color(0xFF111133),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { t },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Color(0xFFFF8A00),
                    trackColor = Color(0x22000000),
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.graphicsLayer { val v = if (t >= 1f) 1f else 0f; alpha = v; scaleX = 0.6f + 0.4f * v; scaleY = 0.6f + 0.4f * v }) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}
