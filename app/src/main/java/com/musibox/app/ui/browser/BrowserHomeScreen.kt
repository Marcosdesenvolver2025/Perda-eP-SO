package com.musibox.app.ui.browser

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.R
import com.musibox.app.download.BrowserStore
import com.musibox.app.download.LinkUtils
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.NeoSectionTitle
import com.musibox.app.ui.components.PageTitle
import com.musibox.app.ui.components.neoGlass
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.download.DownloadSites
import com.musibox.app.ui.download.Site
import com.musibox.app.ui.download.SiteIcon
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb

private val SHORTCUT_SITES = listOf("YouTube", "Instagram", "TikTok", "Kwai")

private enum class BrowserSheet { FAVORITES, HISTORY }

/** Aba "Navegador": busca/endereço, favoritos, histórico, atalhos e sites visitados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserHomeScreen(nav: NavController) {
    val context = LocalContext.current
    val store = remember { BrowserStore.get(context) }
    val history by store.history.collectAsStateWithLifecycle()
    val bookmarks by store.bookmarks.collectAsStateWithLifecycle()
    val shortcuts by store.shortcuts.collectAsStateWithLifecycle()
    val lastUrl by store.lastUrl.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val snack = rememberSnack()
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<BrowserSheet?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<BrowserStore.Entry?>(null) }

    fun open(url: String) {
        focus.clearFocus()
        nav.navigate(Routes.browser(url))
    }

    fun go(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = LinkUtils.extractUrl(t)
        when {
            url != null -> open(url)
            LinkUtils.looksLikeAddress(t) -> open(LinkUtils.normalizeAddress(t))
            else -> open("https://m.youtube.com/results?search_query=${Uri.encode(t)}")
        }
        query = ""
    }

    val heroSites = SHORTCUT_SITES.mapNotNull { n -> DownloadSites.firstOrNull { it.name == n } }
    val otherSites = DownloadSites.filter { it.name !in SHORTCUT_SITES }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "title") {
                PageTitle("Navegador") {
                    // "Abas": volta para a última página aberta
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(2.dp, Color(0xFFDCE6F5), RoundedCornerShape(10.dp))
                            .clickable { open(lastUrl ?: "https://m.youtube.com") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (lastUrl != null) "1" else "0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = { sheet = BrowserSheet.HISTORY }) {
                        Icon(Icons.Rounded.History, "Histórico", tint = Color.White)
                    }
                }
            }
            item(key = "search") {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .neoGlass(RoundedCornerShape(27.dp))
                        .padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Neo.Text2, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Pesquisar ou digitar endereço", color = Neo.Text2, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                            cursorBrush = SolidColor(Neo.Cyan),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { go(query) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Limpar", tint = Neo.Text2) }
                    }
                    Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFF2A3D5C)))
                    IconButton(onClick = {
                        val text = runCatching { clipboard.getText()?.text }.getOrNull()
                        if (text.isNullOrBlank()) snack("Nada copiado.") else go(text)
                    }) {
                        Icon(Icons.Rounded.ContentPaste, "Colar endereço", tint = Color.White)
                    }
                }
            }
            item(key = "chips") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BrowserChip("Favoritos", Icons.Rounded.Star, Modifier.weight(1f)) { sheet = BrowserSheet.FAVORITES }
                    BrowserChip("Histórico", Icons.Rounded.History, Modifier.weight(1f)) { sheet = BrowserSheet.HISTORY }
                    BrowserChip("Abas", Icons.Rounded.FilterNone, Modifier.weight(1f)) { open(lastUrl ?: "https://m.youtube.com") }
                }
            }
            item(key = "hero") { ExploreCard { open("https://m.youtube.com") } }
            item(key = "shortcutsTitle") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Atalhos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(42.dp)
                            .neoGlass(CircleShape)
                            .clickable { adding = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, "Adicionar atalho", tint = Color.White) }
                }
            }
            val tiles: List<Pair<Site?, BrowserStore.Entry?>> =
                heroSites.map { it to null } + shortcuts.map { null to it }
            tiles.chunked(4).forEachIndexed { row, chunk ->
                item(key = "tiles$row") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        chunk.forEach { (site, custom) ->
                            if (site != null) {
                                ShortcutTile(site.name, { SiteIcon(site, 50.dp) }, Modifier.weight(1f), onClick = { open(site.url) })
                            } else if (custom != null) {
                                ShortcutTile(
                                    custom.title,
                                    { LetterIcon(custom.title, 50.dp) },
                                    Modifier.weight(1f),
                                    onClick = { open(custom.url) },
                                    onLongClick = { removing = custom },
                                )
                            }
                        }
                        repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item(key = "others") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(otherSites, key = { it.name }) { site ->
                        Row(
                            Modifier
                                .neoGlass(RoundedCornerShape(20.dp))
                                .clickable { open(site.url) }
                                .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SiteIcon(site, 26.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(site.name, color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item(key = "recentTitle") {
                NeoSectionTitle(
                    "Visitados recentemente",
                    Modifier.padding(start = 16.dp, end = 10.dp, top = 16.dp, bottom = 4.dp),
                    action = if (history.size > 5) "Ver todos" else null,
                    onAction = { sheet = BrowserSheet.HISTORY },
                )
            }
            if (history.isEmpty()) {
                item(key = "recentEmpty") {
                    Text(
                        "Os sites que você abrir aparecem aqui.",
                        color = Neo.Text2,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            } else {
                items(history.take(5), key = { "h" + it.url }) { e -> VisitRow(e) { open(e.url) } }
            }
        }
    }

    when (sheet) {
        null -> Unit
        else -> {
            val favorites = sheet == BrowserSheet.FAVORITES
            val list = if (favorites) bookmarks else history
            ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color(0xFF0A1424)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (favorites) "Favoritos" else "Histórico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!favorites && list.isNotEmpty()) {
                        TextButton(onClick = { store.clearHistory(); snack("Histórico do navegador apagado.") }) {
                            Icon(Icons.Rounded.DeleteSweep, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Limpar")
                        }
                    }
                }
                if (list.isEmpty()) {
                    Text(
                        if (favorites) "Nenhum favorito ainda. Dentro do navegador, toque na ⭐ para salvar a página."
                        else "Nenhum site visitado ainda.",
                        color = Neo.Text2,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(list, key = { it.url }) { e ->
                            VisitRow(e, onRemove = {
                                if (favorites) store.toggleBookmark(e.url, e.title) else store.removeHistory(e.url)
                            }) {
                                sheet = null
                                open(e.url)
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddShortcutDialog(
            onAdd = { name, url ->
                adding = false
                store.addShortcut(name, url)
                snack("Atalho adicionado.")
            },
            onDismiss = { adding = false },
        )
    }
    removing?.let { e ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remover atalho?") },
            text = { Text("\"${e.title}\" sai dos seus atalhos.") },
            confirmButton = {
                TextButton(onClick = {
                    store.removeShortcut(e.url)
                    removing = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
            containerColor = Mb.colors.cardHigh,
        )
    }
}

@Composable
private fun BrowserChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(46.dp)
            .neoGlass(RoundedCornerShape(23.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Cartão "Explore a web" com o globo 3D. */
@Composable
private fun ExploreCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(156.dp)
            .shadow(18.dp, shape, ambientColor = Color(0xFF2F5BFF), spotColor = Color(0xFF2F5BFF))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0A2C8A), Color(0xFF071B66), Color(0xFF0B1550))))
            .border(1.2.dp, Brush.linearGradient(listOf(Color(0xFF2F7BFF), Color(0xFF1E3A9A), Color(0xFFB04DFF))), shape)
            .clickable(onClick = onClick),
    ) {
        Image(
            painterResource(R.drawable.hero_globe),
            null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.55f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.Black, 1f to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp, end = 150.dp),
        ) {
            Text("Explore a web", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text("Música, vídeos e muito mais.", fontSize = 15.sp, color = Color(0xFFD2DCF0), maxLines = 2)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    name: String,
    icon: @Composable () -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .height(104.dp)
            .neoGlass(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(8.dp))
        Text(name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LetterIcon(name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF2F7BFF), Color(0xFF8B5CFF)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase().ifBlank { "•" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.46f).sp)
    }
}

@Composable
private fun VisitRow(e: BrowserStore.Entry, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    val host = BrowserStore.hostOf(e.url)
    val site = DownloadSites.firstOrNull { s -> BrowserStore.hostOf(s.url).let { h -> host == h || host.endsWith(".$h") } }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (site != null) {
            SiteIcon(site, 44.dp)
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .neoGlass(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Public, null, tint = Neo.Link) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, color = Neo.Artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remover", tint = Neo.Text2) }
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Neo.Text2)
        }
    }
}

@Composable
private fun AddShortcutDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val valid = url.isNotBlank() && LinkUtils.looksLikeAddress(url.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo atalho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(
                    url,
                    { url = it },
                    label = { Text("Endereço (ex.: soundcloud.com)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onAdd(name, LinkUtils.normalizeAddress(url.trim())) }) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Mb.colors.cardHigh,
    )
}
