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

/** Faixas de preço (segunda linha de filtros). */
private enum class PriceFilter(val label: String, val range: IntRange? = null) {
    ALL("Todos os preços"),
    FREE("Grátis e planos"),
    R1("R$ 0,99", 1..99),
    R3("R$ 1,49 a 1,99", 100..199),
    R6("R$ 2,49 a 2,99", 200..299),
    R10("R$ 3,99 a 4,99", 300..499),
}

/** Categorias: "Todos", as de conteúdo (Games, Natureza, Espaço…), "Meus" e "Planos". */
private const val CAT_ALL = "todos"
private const val CAT_MINE = "meus"
private const val CAT_PLANS = "planos"

/** Loja de temas como tela própria (aberta por cima, com botão voltar). */
@Composable
fun WallpaperStoreScreen(nav: NavController) {
    Column(Modifier.fillMaxSize()) {
        MbTopBar("Temas", onBack = { nav.popBackStack() }, subtitle = "Para o app e para o navegador")
        WallpaperStoreContent(nav, Modifier.weight(1f))
    }
}

/**
 * Vitrine de temas (papéis de parede). Em cima: o que a pessoa procura (Games, Natureza, Espaço…)
 * e a faixa de preço. Os exclusivos dos planos Go, Plus e Pro ficam por último.
 */
@Composable
fun WallpaperStoreContent(nav: NavController, modifier: Modifier = Modifier, bottomPadding: Dp = 24.dp, title: Boolean = false) {
    val owned by PlayBilling.owned.collectAsStateWithLifecycle()
    val pro by PlayBilling.pro.collectAsStateWithLifecycle()
    val prices by PlayBilling.prices.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(CAT_ALL) }
    var price by rememberSaveable { mutableStateOf(PriceFilter.ALL) }
    val plan = pro.plan
    val appId = AppTheme.appWallpaper?.id
    val browserId = AppTheme.browserWallpaper.id

    val ownedNow = owned
    fun usable(w: Wallpaper) = PlayBilling.canUse(w) || (!w.pro && w.id in ownedNow)

    val all = Wallpapers.all
    val inCategory = when (category) {
        CAT_ALL, CAT_PLANS, CAT_MINE -> all
        else -> all.filter { category in it.tags }
    }
    // Sempre do mais barato para o mais caro
    val forSale = inCategory.filter { !it.pro }.sortedWith(compareBy({ it.price }, { it.name }))
    val exclusive = inCategory.filter { it.pro }
    val planSections = if (exclusive.isEmpty()) emptyList() else listOf(
        PLANS_HEADER to emptyList<Wallpaper>(),
        "Go (3 dias): ${exclusive.count { "go" in it.plans }} temas" to exclusive.filter { "go" in it.plans },
        "Plus (semanal): ${exclusive.count { "plus" in it.plans }} temas" to exclusive.filter { "plus" in it.plans },
        "Pro (mensal): ${exclusive.size} exclusivos" to exclusive,
    )

    fun byPrice(list: List<Wallpaper>) = list.filter { !it.free }.groupBy { it.price }.toSortedMap().map { (p, l) ->
        Wallpapers.priceText(p) to l
    }

    val sections: List<Pair<String, List<Wallpaper>>> = when {
        category == CAT_MINE -> listOf("Seus temas" to all.filter { usable(it) }.sortedBy { it.price })
        category == CAT_PLANS -> planSections
        price == PriceFilter.FREE -> listOf("Grátis" to forSale.filter { it.free }) + planSections
        price.range != null -> byPrice(forSale.filter { it.price in price.range!! })
        else -> {
            val news = forSale.filter { it.isNew() }
            (if (category == CAT_ALL && news.isNotEmpty()) listOf("Novos • Pro usa 1 semana antes" to news) else emptyList()) +
                listOf("Grátis" to forSale.filter { it.free }) +
                byPrice(if (category == CAT_ALL) forSale.filter { !it.isNew() } else forSale) + planSections
        }
    }
    val count = sections.sumOf { it.second.size }

    Column(modifier.fillMaxSize()) {
        if (title) com.musibox.app.ui.components.PageTitle("Temas", "${all.size} papéis • as cores do app seguem o tema")
        Text(
            "O que você procura?",
            color = Neo.Text2,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 18.dp, bottom = 6.dp),
        )
        val cats = listOf(CAT_ALL to "Todos") + Wallpapers.categories + listOf(CAT_MINE to "Meus", CAT_PLANS to "Go • Plus • Pro")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(cats, key = { it.first }) { (key, label) ->
                val n = when (key) {
                    CAT_ALL -> all.size
                    CAT_MINE -> -1
                    CAT_PLANS -> all.count { it.pro }
                    else -> all.count { key in it.tags }
                }
                Chip(if (n >= 0) "$label $n" else label, key == category) { category = key }
            }
        }
        if (category != CAT_MINE && category != CAT_PLANS) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(PriceFilter.entries) { f -> Chip(f.label, f == price, small = true) { price = f } }
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
                        early = w.isEarly() && plan != com.musibox.app.billing.Plan.PRO && !pro.owner,
                    ) { nav.navigate(Routes.wallpaper(w.id)) }
                }
            }
            if (count == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nenhum tema com esse filtro. Tente outra faixa de preço.", color = Neo.Text2, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Neo.OnGreen else Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (small) 13.sp else 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Neo.Green else if (small) Color.Transparent else com.musibox.app.themes.AppTheme.card)
            .border(1.dp, if (selected) Neo.Green else com.musibox.app.themes.AppTheme.line, RoundedCornerShape(20.dp))
            .bounceClick(pressed = 0.92f, shape = RoundedCornerShape(20.dp), onClick = onClick)
            .padding(horizontal = if (small) 13.dp else 16.dp, vertical = if (small) 7.dp else 9.dp),
    )
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
private fun WallTile(w: Wallpaper, price: String, usable: Boolean, inApp: Boolean, inBrowser: Boolean, early: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 18f)
                .bounceClick(pressed = 0.94f, shape = shape, onClick = onClick)
                .border(if (inApp || inBrowser) 2.dp else 1.dp, if (inApp || inBrowser) w.accent else com.musibox.app.themes.AppTheme.line, shape),
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
                    early -> {
                        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFFFFC857), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chega " + java.text.SimpleDateFormat("dd/MM", java.util.Locale("pt", "BR")).format(java.util.Date(w.releaseAt)), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
            if (w.isNew() && !(inApp || inBrowser)) {
                Text(
                    "NOVO",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFC857))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
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
