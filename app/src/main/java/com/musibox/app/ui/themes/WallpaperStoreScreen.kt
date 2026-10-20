package com.musibox.app.ui.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.billing.PlayBilling
import com.musibox.app.themes.AppTheme
import com.musibox.app.themes.Wallpaper
import com.musibox.app.themes.Wallpapers
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.WallpaperImage
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.navigation.Routes

private enum class WallFilter(val label: String) {
    ALL("Todos"), GAMES("Mundos de Jogo"), PLANS("Go • Plus • Pro"), HEROES("Heróis & Vilões"), MINE("Meus"), CHEAP("Até R$ 1"), PREMIUM("Premium")
}

/** Loja de temas como tela própria (aberta por cima, com botão voltar). */
@Composable
fun WallpaperStoreScreen(nav: NavController) {
    Column(Modifier.fillMaxSize()) {
        MbTopBar("Temas", onBack = { nav.popBackStack() }, subtitle = "Para o app e para o navegador")
        WallpaperStoreContent(nav, Modifier.weight(1f))
    }
}

/**
 * Vitrine de temas (papéis de parede): coleção principal, Heróis & Vilões, Mundos de Jogo
 * e os exclusivos dos planos Go, Plus e Pro. Também é a aba "Temas" da barra de baixo.
 */
@Composable
fun WallpaperStoreContent(nav: NavController, modifier: Modifier = Modifier, bottomPadding: Dp = 24.dp, title: Boolean = false) {
    val owned by PlayBilling.owned.collectAsStateWithLifecycle()
    val pro by PlayBilling.pro.collectAsStateWithLifecycle()
    val prices by PlayBilling.prices.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(WallFilter.ALL) }
    val plan = pro.plan
    val appId = AppTheme.appWallpaper?.id
    val browserId = AppTheme.browserWallpaper.id

    fun usable(w: Wallpaper) = w.free || (w.pro && PlayBilling.planUnlocks(plan, w)) || (!w.pro && w.id in owned)

    val all = Wallpapers.all
    val games = all.filter { it.collection == Wallpapers.GAMES && !it.pro }.sortedByDescending { it.price }
    val heroes = all.filter { it.collection == Wallpapers.HEROES }.sortedByDescending { it.price }
    val main = all.filter { it.collection.isEmpty() && !it.pro }
    val exclusive = all.filter { it.pro }
    val byPlan = listOf(
        "Pro (mensal): todos os exclusivos" to exclusive,
        "Inclusos no Plus (semanal)" to exclusive.filter { "plus" in it.plans },
        "Inclusos no Go (3 dias)" to exclusive.filter { "go" in it.plans },
    )
    val sections: List<Pair<String, List<Wallpaper>>> = when (filter) {
        WallFilter.ALL -> listOf(
            "Grátis" to main.filter { it.free },
            "Nova coleção: Mundos de Jogo" to games,
            "Só nos planos Go, Plus e Pro" to exclusive.filter { it.collection == Wallpapers.GAMES },
            "Heróis & Vilões" to heroes,
            "Destaques" to main.filter { !it.free && it.price >= 399 }.sortedByDescending { it.price },
            "Populares" to main.filter { it.price in 99..299 }.sortedByDescending { it.price },
            "Baratinhos" to main.filter { it.price in 1..50 }.sortedByDescending { it.price },
            "Clássicos dos planos" to exclusive.filter { it.collection.isEmpty() },
        )
        WallFilter.GAMES -> listOf(
            "Lendários" to games.filter { it.price >= 1499 },
            "Épicos" to games.filter { it.price in 799..999 },
            "Raros" to games.filter { it.price < 799 },
        )
        WallFilter.PLANS -> byPlan
        WallFilter.HEROES -> listOf("Heróis & Vilões" to heroes)
        WallFilter.MINE -> listOf("Seus temas" to all.filter { usable(it) })
        WallFilter.CHEAP -> listOf("Até R$ 1" to all.filter { !it.pro && it.price in 1..100 }.sortedBy { it.price })
        WallFilter.PREMIUM -> listOf("Premium" to all.filter { !it.pro && it.price >= 499 }.sortedByDescending { it.price })
    }

    Column(modifier.fillMaxSize()) {
        if (title) com.musibox.app.ui.components.PageTitle("Temas", "${all.size} papéis para o app e o navegador")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(WallFilter.entries) { f ->
                val sel = f == filter
                Text(
                    f.label,
                    color = if (sel) Neo.OnGreen else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) Neo.Green else Color(0xFF1E1E1E))
                        .border(1.dp, if (sel) Neo.Green else Color(0xFF2E2E2E), RoundedCornerShape(20.dp))
                        .bounceClick(pressed = 0.92f, shape = RoundedCornerShape(20.dp)) { filter = f }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (filter == WallFilter.PLANS || filter == WallFilter.ALL) {
                item(key = "plans_banner", span = { GridItemSpan(maxLineSpan) }) {
                    PlansBanner(plan) { nav.navigate(Routes.PRO) }
                }
            }
            sections.forEach { (title, list) ->
                if (list.isEmpty()) return@forEach
                item(key = "h_$title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                }
                items(list, key = { "${title}_${it.id}" }) { w ->
                    WallTile(
                        w = w,
                        price = prices[w.productId] ?: w.priceLabel,
                        usable = usable(w),
                        inApp = w.id == appId,
                        inBrowser = w.id == browserId,
                    ) { nav.navigate(Routes.wallpaper(w.id)) }
                }
            }
            if (sections.all { it.second.isEmpty() }) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nenhum tema aqui ainda.", color = Neo.Text2, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

/** Faixa que explica os planos e leva para a tela de assinatura. */
@Composable
private fun PlansBanner(plan: com.musibox.app.billing.Plan, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF2A1A4A), Color(0xFF0E2A2A))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .bounceClick(pressed = 0.97f, shape = shape, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFFFFC857), modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (plan == com.musibox.app.billing.Plan.NONE) "Temas que não se compram" else "Seu plano: ${plan.label}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Go: 6 temas • Plus: 9 temas • Pro: todos os exclusivos",
                color = Neo.Text2,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WallTile(w: Wallpaper, price: String, usable: Boolean, inApp: Boolean, inBrowser: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 18f)
                .bounceClick(pressed = 0.94f, shape = shape, onClick = onClick)
                .border(if (inApp || inBrowser) 2.dp else 1.dp, if (inApp || inBrowser) w.accent else Color(0xFF2E2E2E), shape),
        ) {
            WallpaperImage(w, Modifier.fillMaxSize(), thumbnail = true)
            // selo de preço / dono
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    usable -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = w.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (w.free) "Grátis" else "Seu", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    w.pro -> {
                        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFFFFC857), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(w.planLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    else -> {
                        Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(price, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            if (inApp || inBrowser) {
                Text(
                    when {
                        inApp && inBrowser -> "App + Navegador"
                        inApp -> "No app"
                        else -> "No navegador"
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            w.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp),
        )
        Spacer(Modifier.height(2.dp))
    }
}
