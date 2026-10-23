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

/** Marca o lugar da faixa dos planos na lista. */
private const val PLANS_HEADER = "__planos__"

private enum class WallFilter(val label: String, val range: IntRange? = null) {
    ALL("Todos"),
    R1("R$ 0,10 a 0,50", 1..50),
    R3("R$ 0,99 a 1,49", 51..149),
    R6("R$ 1,99 a 2,49", 150..249),
    R10("R$ 2,99 a 4,99", 250..499),
    PLANS("Go • Plus • Pro"),
    INSPIRATIONS("Inspirações"),
    PREMIUM("Premium 3D"),
    GAMES("Mundos de Jogo"),
    HEROES("Heróis & Vilões"),
    MINE("Meus"),
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
    // Sempre do mais barato para o mais caro
    val forSale = all.filter { !it.pro }.sortedWith(compareBy({ it.price }, { it.name }))
    val exclusive = all.filter { it.pro }
    fun coll(c: String) = forSale.filter { it.collection == c && !it.free }
    val planSections = listOf(
        PLANS_HEADER to emptyList<Wallpaper>(),
        "Go (3 dias): ${exclusive.count { "go" in it.plans }} temas" to exclusive.filter { "go" in it.plans },
        "Plus (semanal): ${exclusive.count { "plus" in it.plans }} temas" to exclusive.filter { "plus" in it.plans },
        "Pro (mensal): todos os ${exclusive.size} exclusivos" to exclusive,
    )
    val sections: List<Pair<String, List<Wallpaper>>> = when (filter) {
        // Grátis, depois cada preço do mais barato ao mais caro; os planos ficam por último
        WallFilter.ALL -> listOf("Grátis" to forSale.filter { it.free }) +
            forSale.filter { !it.free }.groupBy { it.price }.toSortedMap().map { (price, list) ->
                Wallpapers.priceText(price) to list
            } + planSections
        WallFilter.R1, WallFilter.R3, WallFilter.R6, WallFilter.R10 ->
            forSale.filter { it.price in filter.range!! }.groupBy { it.price }.toSortedMap().map { (price, list) ->
                Wallpapers.priceText(price) to list
            }
        WallFilter.PLANS -> planSections
        WallFilter.INSPIRATIONS -> listOf("Inspirações" to coll(Wallpapers.INSPIRATIONS))
        WallFilter.PREMIUM -> listOf("Premium 3D" to coll(Wallpapers.PREMIUM))
        WallFilter.GAMES -> listOf("Mundos de Jogo" to coll(Wallpapers.GAMES))
        WallFilter.HEROES -> listOf("Heróis & Vilões" to coll(Wallpapers.HEROES))
        WallFilter.MINE -> listOf("Seus temas" to all.filter { usable(it) }.sortedBy { it.price })
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
            sections.forEach { (title, list) ->
                if (title == PLANS_HEADER) {
                    item(key = "plans_banner", span = { GridItemSpan(maxLineSpan) }) {
                        PlansBanner(plan, Modifier.padding(top = 18.dp)) { nav.navigate(Routes.PRO) }
                    }
                    return@forEach
                }
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
            if (sections.none { it.second.isNotEmpty() }) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nenhum tema aqui ainda.", color = Neo.Text2, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

/** Faixa que explica os planos e leva para a tela de assinatura. */
@Composable
private fun PlansBanner(plan: com.musibox.app.billing.Plan, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
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
                "Go: ${Wallpapers.all.count { "go" in it.plans }} temas • Plus: ${Wallpapers.all.count { "plus" in it.plans }} temas • Pro: ${Wallpapers.all.count { it.pro }} temas",
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
