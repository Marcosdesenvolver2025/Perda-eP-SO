package com.musibox.app.ui.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.billing.PlayBilling
import com.musibox.app.themes.ThemeStore
import com.musibox.app.themes.Wallpapers
import com.musibox.app.ui.components.WallpaperImage
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.navigation.Routes
import kotlin.math.absoluteValue

/**
 * "Configurar seu navegador" (primeira vez): escolher o papel de parede e os interesses.
 * A cor do botão acompanha o papel escolhido.
 */
@Composable
fun BrowserSetupScreen(nav: NavController) {
    val context = LocalContext.current
    val themes = remember { ThemeStore.get(context) }
    val owned by PlayBilling.owned.collectAsStateWithLifecycle()
    val interests by themes.interests.collectAsStateWithLifecycle()
    // Papéis que a pessoa pode usar + um cartão "ver todos" no fim
    val options = remember(owned) { Wallpapers.all.filter { PlayBilling.canUse(it) } }
    val startIndex = remember { options.indexOfFirst { it.id == themes.browserWallpaperId() }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { options.size + 1 }
    var chosen by remember { mutableStateOf(options.getOrNull(startIndex)) }
    LaunchedEffect(pager.settledPage) { options.getOrNull(pager.settledPage)?.let { chosen = it } }
    val accent by animateColorAsState(chosen?.accent ?: Color(0xFF1FDF67), label = "setupAccent")
    val dark = Color(0xFF0E1214)

    Column(
        Modifier
            .fillMaxSize()
            .background(lerp(dark, accent, 0.06f))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        Text("Configurar seu navegador", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(18.dp))
        Text("Escolher um papel de parede", color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
        Spacer(Modifier.height(18.dp))
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 104.dp),
            pageSpacing = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) { page ->
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val scale by animateFloatAsState(1f, label = "s")
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.12f * offset
                        scaleX = s * scale
                        scaleY = s * scale
                        alpha = 1f - 0.35f * offset
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .then(
                        if (page == pager.currentPage && page < options.size) {
                            Modifier.border(3.dp, accent, RoundedCornerShape(26.dp))
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val w = options.getOrNull(page)
                if (w != null) {
                    WallpaperImage(w, Modifier.fillMaxSize(), thumbnail = true)
                    // miniatura da página inicial (barra de busca e atalhos)
                    Column(Modifier.fillMaxSize().padding(top = 70.dp, start = 18.dp, end = 18.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                        )
                        Spacer(Modifier.height(16.dp))
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(4) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                )
                            }
                        }
                    }
                } else {
                    // último cartão: loja com todos os papéis
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(com.musibox.app.themes.AppTheme.card)
                            .bounceClick(pressed = 0.95f) { nav.navigate(Routes.WALLPAPERS) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Collections, null, tint = accent, modifier = Modifier.size(46.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Ver todos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${Wallpapers.all.size} papéis", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(chosen?.name.orEmpty(), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(26.dp))
        Text("Quais são os seus interesses?", color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 24.dp)) {
            InterestChips(interests, { themes.setInterests(it) }, accent = accent)
        }
        Spacer(Modifier.weight(1f))
        // indicador de progresso (como no exemplo)
        Box(
            Modifier
                .width(96.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(
                Modifier
                    .width(68.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
        }
        Spacer(Modifier.height(22.dp))
        val onAccent = if (accent.luminance() > 0.45f) Color(0xFF10201A) else Color.White
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(58.dp)
                .bounceClick(pressed = 0.96f, shape = RoundedCornerShape(29.dp)) {
                    chosen?.let { themes.setBrowserWallpaper(it.id) }
                    themes.finishBrowserSetup()
                    nav.popBackStack()
                }
                .background(lerp(accent, Color.White, 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Começar a navegar", color = onAccent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
    }
}
