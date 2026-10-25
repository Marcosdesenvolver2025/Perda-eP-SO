package com.musibox.app.ui.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.musibox.app.R
import com.musibox.app.browser.BrowserStore
import com.musibox.app.browser.LinkUtils
import com.musibox.app.browser.NewsFeed
import com.musibox.app.browser.NewsItem
import com.musibox.app.browser.NewsTopic
import com.musibox.app.themes.AppTheme
import com.musibox.app.themes.ThemeStore
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.main.LocalPageActive
import com.musibox.app.ui.navigation.Routes
import kotlinx.coroutines.launch

private val GlassFill = Color.Black.copy(alpha = 0.38f)
private val GlassLine = Color.White.copy(alpha = 0.14f)
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 14f)

/**
 * Página inicial do navegador (estilo do navegador de exemplo): papel de parede ao fundo,
 * logo MusiBox, busca com QR e voz, Modo IA, navegação Privada, atalhos e notícias.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserHomeScreen(nav: NavController) {
    val context = LocalContext.current
    val store = remember { BrowserStore.get(context).also { it.ensureDefaultShortcuts() } }
    val themes = remember { ThemeStore.get(context) }
    val shortcuts by store.shortcuts.collectAsStateWithLifecycle()
    val history by store.history.collectAsStateWithLifecycle()
    val bookmarks by store.bookmarks.collectAsStateWithLifecycle()
    val tabs by store.tabs.collectAsStateWithLifecycle()
    val setupDone by themes.browserSetupDone.collectAsStateWithLifecycle()
    val interests by themes.interests.collectAsStateWithLifecycle()
    val newsOn by themes.newsEnabled.collectAsStateWithLifecycle()
    val newsAsked by themes.newsAsked.collectAsStateWithLifecycle()
    val active = LocalPageActive.current
    val focus = LocalFocusManager.current
    val accent = AppTheme.browserWallpaper.accent

    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<BrowserStore.Entry?>(null) }
    var moreSites by remember { mutableStateOf(false) }

    val openPrivate = rememberPrivateOpener { nav.navigate(Routes.browser(BrowserStore.HOME_URL, private = true)) }
    val proState by com.musibox.app.billing.PlayBilling.pro.collectAsStateWithLifecycle()
    val isPro = proState.isActive
    var adblockOn by remember { mutableStateOf(com.musibox.app.browser.AdBlocker.isEnabled(context)) }
    var darkOn by remember { mutableStateOf(com.musibox.app.browser.AdBlocker.darkSites(context)) }
    var lockOn by remember { mutableStateOf(com.musibox.app.browser.AdBlocker.privateLock(context)) }

    // Primeira vez no navegador: "Configurar seu navegador" (papel de parede e interesses)
    LaunchedEffect(active, setupDone) {
        if (active && !setupDone) nav.navigate(Routes.BROWSER_SETUP) { launchSingleTop = true }
    }

    fun open(url: String) {
        focus.clearFocus()
        searching = false
        nav.navigate(Routes.browser(url))
    }

    fun submit(text: String = query) {
        val t = text.trim()
        if (t.isNotEmpty()) {
            query = ""
            open(BrowserStore.searchOrUrl(t))
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) submit(text)
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o que você quer pesquisar")
        try {
            voice.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Pesquisa por voz indisponível neste aparelho.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startQr() {
        runCatching {
            GmsBarcodeScanning.getClient(context).startScan()
                .addOnSuccessListener { code ->
                    val raw = code.rawValue.orEmpty()
                    val url = LinkUtils.extractUrl(raw)
                    if (url != null) open(url) else if (raw.isNotBlank()) submit(raw)
                }
                .addOnFailureListener { Toast.makeText(context, "Não foi possível abrir o leitor de QR Code.", Toast.LENGTH_SHORT).show() }
        }.onFailure { Toast.makeText(context, "Leitor de QR Code indisponível.", Toast.LENGTH_SHORT).show() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Ajustes (canto superior direito)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(44.dp)
                        .bounceClick(pressed = 0.85f, shape = CircleShape) { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Tune, "Personalizar navegador", tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            Spacer(Modifier.height(if (searching) 8.dp else 46.dp))
            AnimatedVisibility(!searching, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "MusiBox",
                    style = TextStyle(fontSize = 62.sp, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = (-1).sp, shadow = TextShadow),
                    modifier = Modifier.padding(bottom = 40.dp),
                )
            }

            // Busca
            SearchField(
                value = query,
                onValue = { query = it },
                onFocus = { searching = it },
                onSubmit = { submit() },
                onQr = { startQr() },
                onVoice = { startVoice() },
            )
            if (searching && query.isNotBlank()) {
                Suggestions(query, history + bookmarks, onOpen = { open(it) }, onSearch = { submit() })
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    "Modo IA",
                    { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(24.dp)) },
                    Modifier.weight(1f),
                ) {
                    val q = query.trim()
                    query = ""
                    open("https://www.google.com/search?udm=50" + if (q.isNotEmpty()) "&q=" + android.net.Uri.encode(q) else "")
                }
                PillButton(
                    "Privada",
                    { Icon(painterResource(R.drawable.ic_ghost), null, tint = Color.White, modifier = Modifier.size(24.dp)) },
                    Modifier.weight(1f),
                ) {
                    focus.clearFocus()
                    openPrivate()
                }
            }

            // Atalhos
            Spacer(Modifier.height(26.dp))
            SpeedDial(
                sites = shortcuts,
                onOpen = { open(it.url) },
                onLongPress = { removeTarget = it },
                onFolder = { moreSites = true },
                onAdd = { addDialog = true },
            )

            // Notícias (a pessoa pode desligar no ⚙ do widget ou no menu lá em cima)
            if (newsOn) {
                Spacer(Modifier.height(18.dp))
                NewsPanel(active = active, interests = interests, accent = accent, onOpen = { open(it) }, onSettings = { sheet = "interests" })
            }
            Spacer(Modifier.height(24.dp + com.musibox.app.ui.navigation.LocalBottomBarSpace.current))
        }
    }

    if (menu) {
        ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = com.musibox.app.themes.AppTheme.surface) {
            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp)) {
                Text("Navegador", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
                MenuRow(Icons.Rounded.Wallpaper, "Papel de parede", "Escolha o fundo da página inicial") { menu = false; nav.navigate(Routes.WALLPAPERS) }
                if (com.musibox.app.themes.AppTheme.browserWallpaper.id != com.musibox.app.themes.Wallpapers.DEFAULT_BROWSER) {
                    MenuRow(Icons.Rounded.Wallpaper, "Voltar ao tema padrão", "Tira o papel atual e usa o padrão (grátis)") {
                        menu = false
                        com.musibox.app.themes.ThemeStore.get(context).setBrowserWallpaper(com.musibox.app.themes.Wallpapers.DEFAULT_BROWSER)
                    }
                }
                SwitchRow(Icons.Rounded.Newspaper, "Notícias na página inicial", if (newsOn) "Ativado" else "Desativado", newsOn) { themes.setNewsEnabled(it) }
                MenuRow(Icons.Outlined.Settings, "Seus interesses", "Notícias de \"Para você\"") { menu = false; sheet = "interests" }
                MenuRow(Icons.Rounded.Star, "Favoritos", "${bookmarks.size} salvos") { menu = false; sheet = "bookmarks" }
                MenuRow(Icons.Rounded.History, "Histórico", "${history.size} páginas") { menu = false; sheet = "history" }
                MenuRow(Icons.Rounded.Tab, "Abas abertas", "${tabs.size} abas") { menu = false; nav.navigate(Routes.browser(BrowserStore.TABS_URL)) }
                MenuRow(Icons.Rounded.Add, "Adicionar atalho", "Coloque um site na página inicial") { menu = false; addDialog = true }
                Text("Recursos Pro", color = Color(0xFF9A9A9A), fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 2.dp))
                ProToggle("Bloquear anúncios e rastreadores", isPro, isPro && adblockOn, onPro = { menu = false; nav.navigate(Routes.PRO) }) {
                    adblockOn = it
                    com.musibox.app.browser.AdBlocker.setEnabled(context, it)
                }
                ProToggle("Modo escuro em todos os sites", isPro, isPro && darkOn, onPro = { menu = false; nav.navigate(Routes.PRO) }) {
                    darkOn = it
                    com.musibox.app.browser.AdBlocker.setDarkSites(context, it)
                }
                ProToggle("Senha (PIN do cofre) nas abas privadas", isPro, isPro && lockOn, onPro = { menu = false; nav.navigate(Routes.PRO) }) {
                    lockOn = it
                    com.musibox.app.browser.AdBlocker.setPrivateLock(context, it)
                }
            }
        }
    }

    when (sheet) {
        "interests" -> InterestsSheet(
            interests,
            onChange = { themes.setInterests(it) },
            onDismiss = { sheet = null },
            newsOn = newsOn,
            onNews = { themes.setNewsEnabled(it); if (!it) sheet = null },
        )
        "history" -> VisitsSheet("Histórico", history, onOpen = { sheet = null; open(it) }, onClear = { store.clearHistory() }, onDismiss = { sheet = null })
        "bookmarks" -> VisitsSheet("Favoritos", bookmarks, onOpen = { sheet = null; open(it) }, onClear = null, onDismiss = { sheet = null })
    }
    if (moreSites) {
        ModalBottomSheet(onDismissRequest = { moreSites = false }, containerColor = com.musibox.app.themes.AppTheme.surface) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                Text("Mais sites", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 14.dp))
                FlowRow(maxItemsInEachRow = 4, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BrowserStore.MORE_SITES.forEach { (name, url) ->
                        SiteTile(name, url, Modifier.weight(1f), onLongPress = {
                            store.addShortcut(name, url)
                            Toast.makeText(context, "$name foi para a página inicial.", Toast.LENGTH_SHORT).show()
                        }) { moreSites = false; open(url) }
                    }
                }
                Text("Toque e segure para fixar na página inicial.", color = Color(0xFF9A9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
    // Primeira vez na página inicial: perguntar se quer deixar as notícias ativadas
    if (active && setupDone && !newsAsked) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.Newspaper, null, tint = accent) },
            title = { Text("Notícias na página inicial") },
            text = {
                Text(
                    "Quer deixar o widget de notícias ativado? Você pode mudar depois no ⚙ das notícias ou no menu lá em cima.",
                )
            },
            confirmButton = { TextButton(onClick = { themes.setNewsEnabled(true) }) { Text("Deixar ativado") } },
            dismissButton = { TextButton(onClick = { themes.setNewsEnabled(false) }) { Text("Desativar") } },
            containerColor = com.musibox.app.themes.AppTheme.card,
        )
    }
    if (addDialog) {
        AddShortcutDialog(onAdd = { name, url -> addDialog = false; store.addShortcut(name, url) }, onDismiss = { addDialog = false })
    }
    removeTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remover atalho?") },
            text = { Text("\"${e.title}\" sai da página inicial. Você pode adicionar de novo depois.") },
            confirmButton = { TextButton(onClick = { store.removeShortcut(e.url); removeTarget = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancelar") } },
            containerColor = com.musibox.app.themes.AppTheme.card,
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValue: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onQr: () -> Unit,
    onVoice: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(GlassFill)
            .border(1.dp, GlassLine, RoundedCornerShape(30.dp))
            .clickable { requester.requestFocus() }
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubcomposeAsyncImage(
            model = BrowserStore.iconUrl("https://www.google.com/"),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            error = { Icon(Icons.Outlined.Search, null, tint = Color.White) },
        )
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Pesquise ou pergunte algo", color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (value.isNotEmpty()) {
            RoundIcon(Icons.Rounded.Close, "Limpar") { onValue("") }
        } else {
            RoundIcon(Icons.Outlined.QrCodeScanner, "Ler QR Code", onQr)
            RoundIcon(Icons.Outlined.Mic, "Pesquisar por voz", onVoice)
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(25.dp)) }
}

@Composable
private fun PillButton(label: String, icon: @Composable () -> Unit, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .height(56.dp)
            .bounceClick(pressed = 0.95f, shape = shape, onClick = onClick)
            .background(GlassFill)
            .border(1.dp, GlassLine, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Suggestions(query: String, entries: List<BrowserStore.Entry>, onOpen: (String) -> Unit, onSearch: () -> Unit) {
    val q = query.trim().lowercase()
    val matches = entries.asSequence()
        .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
        .distinctBy { it.url }
        .take(5)
        .toList()
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xE6181818)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSearch)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                if (LinkUtils.looksLikeAddress(query)) "Abrir $query" else "Pesquisar \"$query\"",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        matches.forEach { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(e.url) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(e.url, 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LinkUtils.hostOf(e.url), color = Color(0xFF9A9A9A), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- atalhos

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDial(
    sites: List<BrowserStore.Entry>,
    onOpen: (BrowserStore.Entry) -> Unit,
    onLongPress: (BrowserStore.Entry) -> Unit,
    onFolder: () -> Unit,
    onAdd: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        maxItemsInEachRow = 5,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sites.forEach { s ->
            SiteTile(s.title, s.url, Modifier.weight(1f), onLongPress = { onLongPress(s) }) { onOpen(s) }
        }
        // Pasta "Mais sites" (quatro ícones pequenos) e o "+"
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(62.dp)
                    .bounceClick(pressed = 0.88f, shape = CircleShape, onClick = onFolder)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BrowserStore.MORE_SITES.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { row.forEach { (_, url) -> Favicon(url, 20.dp, circle = true) } }
                    }
                }
            }
            Text("Mais", color = Color.White, fontSize = 13.sp, style = TextStyle(shadow = TextShadow), modifier = Modifier.padding(top = 6.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(62.dp)
                    .bounceClick(pressed = 0.88f, shape = CircleShape, onClick = onAdd)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, GlassLine, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Adicionar atalho", tint = Color.White, modifier = Modifier.size(30.dp)) }
            Text(" ", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        // completa a última linha para os ícones ficarem alinhados
        val total = sites.size + 2
        repeat((5 - total % 5) % 5) { Spacer(Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(name: String, url: String, modifier: Modifier, onLongPress: () -> Unit, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Favicon(url, 38.dp) }
        Text(
            name,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = TextShadow),
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** Ícone do site (favicon). Sem internet, mostra a inicial. */
@Composable
fun Favicon(url: String, size: Dp, circle: Boolean = false) {
    val host = LinkUtils.hostOf(url)
    SubcomposeAsyncImage(
        model = BrowserStore.iconUrl(url),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .then(if (circle) Modifier.clip(CircleShape).background(Color.White) else Modifier),
        error = {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF3A3A3A), Color(0xFF202020)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(host.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
            }
        },
    )
}

// ---------------------------------------------------------------- notícias

@Composable
private fun NewsPanel(active: Boolean, interests: Set<String>, accent: Color, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val feed = remember { NewsFeed.get(context) }
    val scope = rememberCoroutineScope()
    var topic by rememberSaveable { mutableStateOf(NewsTopic.FOR_YOU) }
    var items by remember { mutableStateOf<List<NewsItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) started = true }

    LaunchedEffect(topic, interests, reload, started) {
        if (!started) return@LaunchedEffect
        loading = true
        items = runCatching { feed.load(topic, interests, force = reload > 0) }.getOrDefault(emptyList())
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, GlassLine, RoundedCornerShape(28.dp))
            .padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(NewsTopic.tabs) { t ->
                    val sel = t == topic
                    val bg by animateColorAsState(if (sel) accent.copy(alpha = 0.32f) else Color.Transparent, label = "tab")
                    Text(
                        t.label,
                        color = Color.White,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .clickable { topic = t }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Settings, "Seus interesses", tint = Color.White) }
        }
        Spacer(Modifier.height(10.dp))
        val list = items
        when {
            list == null || (loading && list.isEmpty()) -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            list.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Public, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
                Text("Não foi possível carregar as notícias.", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { reload++ }) {
                    Icon(Icons.Rounded.Refresh, null, tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Tentar de novo", color = accent)
                }
            }
            else -> Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.firstOrNull { it.image != null }?.let { top -> BigNews(top) { onOpen(top.link) } }
                list.filter { it != list.firstOrNull { x -> x.image != null } }.take(14).forEach { n -> NewsRow(n) { onOpen(n.link) } }
            }
        }
    }
}

@Composable
private fun BigNews(n: NewsItem, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF222222))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(n.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(n.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(listOf(n.source, NewsFeed.ago(n.time)).filter { it.isNotBlank() }.joinToString(" • "), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun NewsRow(n: NewsItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(n.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(n.source, NewsFeed.ago(n.time)).filter { it.isNotBlank() }.joinToString(" • "),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (n.image != null) {
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                n.image,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222222)),
            )
        }
    }
}

// ---------------------------------------------------------------- folhas

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = Color(0xFF9A9A9A), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = Color(0xFF9A9A9A), fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProToggle(label: String, isPro: Boolean, checked: Boolean, onPro: () -> Unit, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (isPro) onChange(!checked) else onPro() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (isPro) {
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
        } else {
            Text(
                "Pro",
                color = Color(0xFF0A0A0A),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFC857))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
fun InterestsSheet(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    newsOn: Boolean? = null,
    onNews: (Boolean) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.musibox.app.themes.AppTheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            if (newsOn != null) {
                Text("Notícias", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNews(!newsOn) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar notícias na página inicial", color = Color.White, fontSize = 16.sp)
                        Text(if (newsOn) "Ativado" else "Desativado", color = Color(0xFF9A9A9A), fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(checked = newsOn, onCheckedChange = onNews)
                }
                Spacer(Modifier.height(16.dp))
            }
            Text("Quais são os seus interesses?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("As notícias de \"Para você\" seguem o que você escolher.", color = Color(0xFF9A9A9A), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            InterestChips(selected, onChange)
        }
    }
}

@Composable
fun InterestChips(selected: Set<String>, onChange: (Set<String>) -> Unit, accent: Color = AppTheme.browserWallpaper.accent) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ThemeStore.ALL_INTERESTS.forEach { label ->
            val on = label in selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) accent.copy(alpha = 0.28f) else Color.Transparent)
                    .border(1.dp, if (on) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .clickable { onChange(if (on) selected - label else selected + label) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Text("✓", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun VisitsSheet(
    title: String,
    entries: List<BrowserStore.Entry>,
    onOpen: (String) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.musibox.app.themes.AppTheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                if (onClear != null && entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Limpar") }
            }
            if (entries.isEmpty()) {
                Text("Nada por aqui ainda.", color = Color(0xFF9A9A9A), modifier = Modifier.padding(vertical = 24.dp))
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                entries.take(80).forEach { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpen(e.url) }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Favicon(e.url, 26.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(LinkUtils.hostOf(e.url), color = Color(0xFF9A9A9A), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddShortcutDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo atalho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(
                    url,
                    { url = it },
                    label = { Text("Endereço (ex.: shopee.com.br)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val u = url.trim()
                    if (u.isNotEmpty()) onAdd(name.ifBlank { LinkUtils.hostOf(LinkUtils.normalizeAddress(u)) }, LinkUtils.normalizeAddress(u))
                },
                enabled = url.isNotBlank(),
            ) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = com.musibox.app.themes.AppTheme.card,
    )
}
