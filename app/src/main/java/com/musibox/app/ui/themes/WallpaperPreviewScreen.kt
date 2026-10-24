package com.musibox.app.ui.themes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.billing.PlayBilling
import com.musibox.app.core.findActivity
import com.musibox.app.themes.AppTheme
import com.musibox.app.themes.ThemeStore
import com.musibox.app.themes.Wallpaper
import com.musibox.app.themes.Wallpapers
import com.musibox.app.ui.components.WallpaperImage
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.navigation.Routes

/**
 * Prévia em tela cheia (deslize para ver os outros). Mostra como fica no app ou no navegador,
 * e os botões Comprar / Usar no app / Usar no navegador.
 */
@Composable
fun WallpaperPreviewScreen(nav: NavController, id: String) {
    val context = LocalContext.current
    val themes = remember { ThemeStore.get(context) }
    val owned by PlayBilling.owned.collectAsStateWithLifecycle()
    val pro by PlayBilling.pro.collectAsStateWithLifecycle()
    val prices by PlayBilling.prices.collectAsStateWithLifecycle()
    // Deslizar passa pelos papéis da mesma coleção (Heróis & Vilões fica junto)
    val list = remember(id) {
        val col = Wallpapers.get(id)?.collection.orEmpty()
        Wallpapers.all.filter { it.collection == col }.ifEmpty { Wallpapers.all }
    }
    val start = remember(id) { list.indexOfFirst { it.id == id }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { list.size }
    var mode by rememberSaveable { mutableStateOf("app") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { list[it].id }) { page ->
            val w = list[page]
            Box(Modifier.fillMaxSize()) {
                WallpaperImage(w, Modifier.fillMaxSize())
                if (mode == "app") AppMock(w) else BrowserMock()
            }
        }
        val w = list[pager.currentPage]
        val plan = pro.plan
        val claimTick by PlayBilling.claimTick.collectAsStateWithLifecycle()
        val usable = PlayBilling.canUse(w) || (!w.pro && w.id in owned)
        val price = prices[w.productId] ?: w.priceLabel
        val canClaim = remember(claimTick, plan, w.id) { !usable && !w.pro && !w.free && PlayBilling.freeClaimAvailable() }

        // topo
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .bounceClick(pressed = 0.85f, shape = CircleShape) { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White) }
            Spacer(Modifier.width(4.dp))
            Text("${pager.currentPage + 1} de ${list.size}", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            ModeChip("App", Icons.Rounded.PhoneAndroid, mode == "app") { mode = "app" }
            Spacer(Modifier.width(6.dp))
            ModeChip("Navegador", Icons.Rounded.Language, mode == "browser") { mode = "browser" }
        }

        // painel de baixo
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            pro.owner -> "Liberado • conta do dono do app"
                            w.free -> "Grátis"
                            w.pro && usable -> "Liberado no seu plano ${plan.label}"
                            w.pro -> "Não se compra • incluso no ${w.plansText}"
                            usable -> "Comprado • fica na sua conta Google"
                            else -> "Compra única • fica para sempre na sua conta Google"
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                }
                if (!usable && !w.pro) {
                    Text(price, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            val onAccent = if (w.accent.luminance() > 0.45f) Color(0xFF0A0A0A) else Color.White
            when {
                usable -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Usar no app", w.accent, onAccent, Modifier.weight(1f)) {
                            themes.setAppWallpaper(w.id)
                            toast("Pronto! O app inteiro ficou com as cores de ${w.name}.")
                        }
                        BigButton("Usar no navegador", Color.White.copy(alpha = 0.16f), Color.White, Modifier.weight(1f)) {
                            themes.setBrowserWallpaper(w.id)
                            toast("Papel do navegador trocado.")
                        }
                    }
                    if (AppTheme.appWallpaper?.id == w.id) {
                        TextButton(onClick = { themes.setAppWallpaper(null); toast("O app voltou ao fundo preto.") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Tirar do app (voltar ao preto)", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    if (AppTheme.browserWallpaper.id == w.id && w.id != Wallpapers.DEFAULT_BROWSER) {
                        TextButton(
                            onClick = {
                                themes.setBrowserWallpaper(Wallpapers.DEFAULT_BROWSER)
                                toast("O navegador voltou ao tema padrão.")
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("Tirar do navegador (voltar ao padrão)", color = Color.White.copy(alpha = 0.8f)) }
                    }
                }
                w.pro -> BigButton("Liberar com o ${w.planLabel}", w.accent, onAccent, Modifier.fillMaxWidth(), Icons.Rounded.WorkspacePremium) {
                    nav.navigate(Routes.PRO)
                }
                canClaim -> Column {
                    BigButton("Pegar grátis • 1 por mês no Pro", w.accent, onAccent, Modifier.fillMaxWidth(), Icons.Rounded.WorkspacePremium) {
                        PlayBilling.claimFreeTheme(w) { msg ->
                            toast(msg)
                            if (msg == PlayBilling.LOGIN_MSG) nav.navigate(Routes.LOGIN)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BigButton("Comprar • $price", Color.White.copy(alpha = 0.16f), Color.White, Modifier.fillMaxWidth(), Icons.Rounded.Lock) {
                        val activity = context.findActivity()
                        if (activity == null) toast("Não foi possível abrir a Google Play.") else PlayBilling.buyWallpaper(activity, w) { msg ->
                            toast(msg)
                            if (msg == PlayBilling.LOGIN_MSG) nav.navigate(Routes.LOGIN)
                        }
                    }
                }
                else -> BigButton("Comprar • $price", w.accent, onAccent, Modifier.fillMaxWidth(), Icons.Rounded.Lock) {
                    val activity = context.findActivity()
                    if (activity == null) {
                        toast("Não foi possível abrir a Google Play.")
                    } else {
                        PlayBilling.buyWallpaper(activity, w) { msg ->
                            toast(msg)
                            if (msg == PlayBilling.LOGIN_MSG) nav.navigate(Routes.LOGIN)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.4f))
            .bounceClick(pressed = 0.9f, shape = RoundedCornerShape(18.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BigButton(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(54.dp)
            .bounceClick(pressed = 0.95f, shape = RoundedCornerShape(27.dp), onClick = onClick)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(27.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Como o app fica com este fundo (cabeçalho e cartões por cima). */
@Composable
private fun AppMock(w: Wallpaper) {
    val pal = remember(w.id) { com.musibox.app.themes.WallpaperPalettes[w.id] }
    val bg = pal?.let { Color(it[1]) } ?: Color.Black
    val card = pal?.let { Color(it[3]) } ?: com.musibox.app.themes.AppTheme.DefaultSurface
    val line = pal?.let { Color(it[5]) } ?: com.musibox.app.themes.AppTheme.DefaultLine
    val accent2 = pal?.let { Color(it[0]) } ?: w.accent
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.34f), bg.copy(alpha = 0.64f))))) {
        Column(Modifier.statusBarsPadding().padding(top = 64.dp, start = 18.dp, end = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Musi", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Box", color = w.accent, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todas", "Cantores", "Favoritas").forEachIndexed { i, label ->
                    Text(
                        label,
                        color = if (i == 0) Color.Black else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (i == 0) w.accent else card)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            repeat(4) { i ->
                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(if (i == 1) accent2.copy(alpha = 0.7f) else line))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.7f - i * 0.08f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(if (i == 0) w.accent else Color.White.copy(alpha = 0.85f)))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.4f)))
                    }
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (i == 0) w.accent else card))
                }
            }
        }
    }
}

/** Como a página inicial do navegador fica com este fundo. */
@Composable
private fun BrowserMock() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 110.dp, start = 18.dp, end = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MusiBox", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color.Black.copy(alpha = 0.38f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(27.dp)),
            contentAlignment = Alignment.CenterStart,
        ) { Text("Pesquise ou pergunte algo", color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(start = 20.dp)) }
        Spacer(Modifier.height(26.dp))
        repeat(2) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(5) { Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f))) }
            }
        }
    }
}
