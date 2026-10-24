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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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

/** Filtro de preço (botão ao lado da busca). */
private enum class PriceFilter(val label: String, val range: IntRange? = null) {
    ALL("Todos os preços"),
    FREE("Grátis"),
    R1("R$ 0,99", 1..99),
    R3("R$ 1,49 a R$ 1,99", 100..199),
    R6("R$ 2,49 a R$ 2,99", 200..299),
    R10("R$ 3,99 a R$ 4,99", 300..499),
    PLANS("Planos Go, Plus e Pro"),
    MINE("Meus temas"),
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
 * Vitrine de temas: busca pelo nome, filtro de preço ao lado e a lista organizada em
 * Grátis → preços do mais barato ao mais caro → Planos Go, Plus e Pro.
 */
@Composable
fun WallpaperStoreContent(nav: NavController, modifier: Modifier = Modifier, bottomPadding: Dp = 24.dp, title: Boolean = false) {
    val owned by PlayBilling.owned.collectAsStateWithLifecycle()
    val pro by PlayBilling.pro.collectAsStateWithLifecycle()
    val prices by PlayBilling.prices.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf(PriceFilter.ALL) }
    var filterMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val plan = pro.plan
    val appId = AppTheme.appWallpaper?.id
    val browserId = AppTheme.browserWallpaper.id

    val ownedNow = owned
    fun usable(w: Wallpaper) = PlayBilling.canUse(w) || (!w.pro && w.id in ownedNow)

    val all = Wallpapers.all
    val found = remember(query) { all.filter { Wallpapers.matches(it, query) } }
    val free = found.filter { it.free }.sortedBy { it.name }
    val paid = found.filter { !it.free && !it.pro }.sortedWith(compareBy({ it.price }, { it.name }))
    // Planos: primeiro os do Go, depois os do Plus e por fim os só do Pro (sem repetir)
    val plans = found.filter { it.pro }.sortedWith(compareBy({ if ("go" in it.plans) 0 else if ("plus" in it.plans) 1 else 2 }, { it.name }))

    fun byPrice(list: List<Wallpaper>) = list.groupBy { it.price }.toSortedMap().map { (p, l) -> Wallpapers.priceText(p) to l }

    val sections: List<Pair<String, List<Wallpaper>>> = when (price) {
        PriceFilter.ALL -> listOf("Grátis" to free) + byPrice(paid) + listOf(PLANS_TITLE to plans)
        PriceFilter.FREE -> listOf("Grátis" to free)
        PriceFilter.PLANS -> listOf(PLANS_TITLE to plans)
        PriceFilter.MINE -> listOf("Meus temas" to found.filter { usable(it) }.sortedWith(compareBy({ it.pro }, { it.price }, { it.name })))
        else -> byPrice(paid.filter { it.price in price.range!! })
    }
    val count = sections.sumOf { it.second.size }

    Column(modifier.fillMaxSize()) {
        if (title) com.musibox.app.ui.components.PageTitle("Temas", "${all.size} papéis • as cores do app seguem o tema")
        // Busca + filtro de preço
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(AppTheme.card)
                    .border(1.dp, AppTheme.line, RoundedCornerShape(26.dp))
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Neo.Text2, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Pesquisar temas", color = Neo.Text2, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Neo.Green),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).bounceClick(pressed = 0.85f, shape = CircleShape) { query = "" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Limpar busca", tint = Neo.Text2, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box {
                val active = price != PriceFilter.ALL
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (active) Neo.Green else AppTheme.card)
                        .border(1.dp, if (active) Neo.Green else AppTheme.line, CircleShape)
                        .bounceClick(pressed = 0.9f, shape = CircleShape) { filterMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Tune, "Filtrar por preço", tint = if (active) Neo.OnGreen else Color.White) }
                DropdownMenu(filterMenu, { filterMenu = false }, containerColor = AppTheme.cardHigh) {
                    Text(
                        "Filtrar por preço",
                        color = Neo.Text2,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    PriceFilter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label, color = if (f == price) Neo.Green else Color.White, fontWeight = if (f == price) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    if (f == price) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                    null,
                                    tint = if (f == price) Neo.Green else Neo.Text2,
                                )
                            },
                            onClick = { price = f; filterMenu = false },
                        )
                    }
                }
            }
        }
        if (price != PriceFilter.ALL) {
            Row(
                Modifier
                    .padding(start = 16.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Neo.Green.copy(alpha = 0.16f))
                    .bounceClick(pressed = 0.94f, shape = RoundedCornerShape(16.dp)) { price = PriceFilter.ALL }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(price.label, color = Neo.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Close, "Tirar filtro", tint = Neo.Green, modifier = Modifier.size(16.dp))
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
                if (list.isEmpty()) return@forEach
                item(key = "h_$title", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("${list.size}", color = Neo.Text2, fontSize = 14.sp)
                        }
                        if (title == PLANS_TITLE) PlansBanner(plan, Modifier.padding(top = 8.dp)) { nav.navigate(Routes.PRO) }
                    }
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
                    Text(
                        if (query.isNotBlank()) "Nenhum tema com \"$query\". Tente outra palavra." else "Nenhum tema com esse filtro.",
                        color = Neo.Text2,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

private const val PLANS_TITLE = "Planos Go, Plus e Pro"

/** Faixa que explica os planos e leva para a tela de assinatura. */
@Composable
private fun PlansBanner(plan: com.musibox.app.billing.Plan, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Neo.Green.copy(alpha = 0.24f).compositeOver(AppTheme.card),
                        AppTheme.accent2.copy(alpha = 0.18f).compositeOver(AppTheme.card),
                    ),
                ),
            )
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
