package com.musibox.app.ui.download

import android.app.Activity
import android.content.ContentUris
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.DownloadEntity
import com.musibox.app.data.db.DownloadStatus
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.download.LinkUtils
import com.musibox.app.download.SearchResult
import com.musibox.app.storage.DeleteResult
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.ExternalActions
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.OfflineBanner
import com.musibox.app.ui.components.SectionTitle
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb

/**
 * Central de downloads inspirada no Snaptube: busca/link no topo, sites que abrem no
 * navegador interno, link copiado detectado, downloads em andamento e pesquisa no YouTube.
 */
@Composable
fun DownloadScreen(nav: NavController, initialTab: Int, initialUrl: String?) {
    val linkVm = appViewModel { LinkDownloadViewModel(it) }
    val centerVm = appViewModel { DownloadCenterViewModel(it) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    val snack = rememberSnack()
    var handledInitial by rememberSaveable { mutableStateOf(false) }
    val online by centerVm.online.collectAsStateWithLifecycle()
    val settings by linkVm.settings.collectAsStateWithLifecycle()
    val downloads by centerVm.downloads.collectAsStateWithLifecycle()
    val search by centerVm.search.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<List<Uri>?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var clipUrl by remember { mutableStateOf<String?>(null) }

    fun startDownload(url: String) {
        focus.clearFocus()
        linkVm.analyze(url)
        showSheet = true
    }

    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        focus.clearFocus()
        val url = LinkUtils.extractUrl(t)
        when {
            url != null -> startDownload(url)
            LinkUtils.looksLikeAddress(t) -> nav.navigate(Routes.browser(LinkUtils.normalizeAddress(t)))
            else -> centerVm.runSearch()
        }
    }

    LaunchedEffect(initialUrl, initialTab) {
        if (handledInitial) return@LaunchedEffect
        handledInitial = true
        when {
            initialTab == 2 -> nav.navigate(Routes.DOWNLOADS)
            initialTab == 1 && !initialUrl.isNullOrBlank() -> {
                centerVm.query = initialUrl
                centerVm.runSearch()
            }
            !initialUrl.isNullOrBlank() -> startDownload(initialUrl)
        }
    }

    // Link copiado (como no Snaptube): lido quando a tela está em foco.
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (focused) {
                val text = runCatching { clipboard.getText()?.text }.getOrNull()
                val url = LinkUtils.extractUrl(text)
                if (url != null && url != centerVm.handledClip) clipUrl = url
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            if (settings.askDestination) pendingImport = uris
            else centerVm.importFiles(uris, settings.defaultDestination) { snack(it) }
        }
    }
    val actions = rememberDownloadActions(centerVm, nav)
    val list = downloads.orEmpty()
    val active = list.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
    val showingSearch = search !is SearchState.Idle

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Cabeçalho
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showingSearch) {
                MbIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", { centerVm.clearSearch() })
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Baixar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Vídeos e músicas de qualquer site",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MbIconButton(Icons.Rounded.FileOpen, "Importar arquivo do aparelho", { importLauncher.launch(arrayOf("audio/*", "video/*")) })
            Spacer(Modifier.width(8.dp))
            Box {
                MbIconButton(Icons.Rounded.Folder, "Meus downloads", { nav.navigate(Routes.DOWNLOADS) })
                if (active.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Mb.colors.danger),
                        contentAlignment = Alignment.Center,
                    ) { Text("${active.size}", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        SearchBox(
            value = centerVm.query,
            onValueChange = { centerVm.query = it },
            onSubmit = { submit(centerVm.query) },
            onPaste = {
                val text = clipboard.getText()?.text
                if (text.isNullOrBlank()) snack("Nenhum link copiado.")
                else {
                    centerVm.query = text
                    submit(text)
                }
            },
            onClear = { centerVm.clearSearch() },
        )
        OfflineBanner(!online)

        when (val s = search) {
            SearchState.Idle -> HubHome(
                nav = nav,
                clipUrl = clipUrl,
                onClipDownload = { url ->
                    centerVm.handledClip = url
                    clipUrl = null
                    startDownload(url)
                },
                onClipDismiss = { url ->
                    centerVm.handledClip = url
                    clipUrl = null
                },
                active = active,
                recent = list.filter { it.status == DownloadStatus.COMPLETED }.take(3),
                actions = actions.actions,
            )
            SearchState.Loading -> LoadingState("Pesquisando no YouTube…")
            is SearchState.Error -> StateMessage(Icons.Rounded.SearchOff, s.message, actionText = "Tentar novamente", onAction = { centerVm.runSearch() })
            is SearchState.Results -> if (s.items.isEmpty()) {
                StateMessage(Icons.Rounded.SearchOff, "Nada encontrado para \"${s.query}\"")
            } else {
                SearchResults(
                    s.items,
                    onOpen = { nav.navigate(Routes.browser(it.url.replace("://www.youtube.com", "://m.youtube.com"))) },
                    onDownload = { startDownload(it.url) },
                )
            }
        }
    }

    if (showSheet) {
        DownloadSheet(linkVm, onDismiss = { showSheet = false }, onMessage = snack)
    }
    pendingImport?.let { uris ->
        DestinationDialog(
            onChoose = { dest ->
                pendingImport = null
                centerVm.importFiles(uris, dest) { snack(it) }
            },
            onDismiss = { pendingImport = null },
        )
    }
    actions.Dialogs()
}

@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(Mb.colors.card)
            .border(1.dp, Mb.colors.border, RoundedCornerShape(29.dp))
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    "Pesquise ou cole um link",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Cancel, "Limpar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(
            Modifier
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Mb.colors.accentGradient)
                .clickable { if (value.isBlank()) onPaste() else onSubmit() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (value.isBlank()) Icons.Rounded.ContentPaste else Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (value.isBlank()) "Colar" else "Ir", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HubHome(
    nav: NavController,
    clipUrl: String?,
    onClipDownload: (String) -> Unit,
    onClipDismiss: (String) -> Unit,
    active: List<DownloadEntity>,
    recent: List<DownloadEntity>,
    actions: DownloadActions,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (clipUrl != null) {
            item(key = "clip") { ClipboardCard(clipUrl, { onClipDownload(clipUrl) }, { onClipDismiss(clipUrl) }) }
        }
        item(key = "sites") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(6.dp))
                SectionTitle("Sites")
                Spacer(Modifier.height(10.dp))
                SitesGrid { site -> nav.navigate(Routes.browser(site.url)) }
            }
        }
        if (active.isNotEmpty()) {
            item(key = "activeTitle") {
                SectionTitle(
                    "Baixando agora",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    actionText = "Ver todos",
                    onAction = { nav.navigate(Routes.DOWNLOADS) },
                )
            }
            items(active.take(4), key = { "a_" + it.id }) { d -> DownloadRow(d, actions, Modifier.padding(horizontal = 4.dp)) }
        }
        item(key = "howto") { HowToCard() }
        if (recent.isNotEmpty()) {
            item(key = "recentTitle") {
                SectionTitle(
                    "Baixados recentemente",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    actionText = "Ver todos",
                    onAction = { nav.navigate(Routes.DOWNLOADS) },
                )
            }
            items(recent, key = { "r_" + it.id }) { d -> DownloadRow(d, actions, Modifier.padding(horizontal = 4.dp)) }
        }
    }
}

@Composable
private fun ClipboardCard(url: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val platform = LinkUtils.platformOf(url)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), Mb.colors.vault.copy(alpha = 0.14f))))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Link copiado detectado", style = MaterialTheme.typography.titleSmall)
            Text(
                if (platform.label != "Link") "${platform.label} • ${LinkUtils.hostOf(url)}" else url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Mb.colors.accentGradient)
                .clickable(onClick = onDownload)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Baixar", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Dispensar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun SitesGrid(onSite: (Site) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 840.dp -> 8
            maxWidth >= 600.dp -> 6
            else -> 4
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DownloadSites.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { site ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSite(site) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SiteIcon(site, 54.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(site.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HowToCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Mb.colors.heroGradient)
            .padding(18.dp),
    ) {
        Text("Baixe direto dos apps", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "No YouTube, TikTok ou Instagram:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            HowToStep(Modifier.weight(1f), Icons.Rounded.TouchApp, "1", "Abra o vídeo")
            HowToStep(Modifier.weight(1f), Icons.Rounded.Share, "2", "Toque em Compartilhar")
            HowToStep(Modifier.weight(1f), Icons.Rounded.Download, "3", "Escolha \"Baixar com MusiBox\"")
        }
    }
}

@Composable
private fun HowToStep(modifier: Modifier, icon: ImageVector, number: String, text: String) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.height(6.dp))
        Text(
            "$number. $text",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@Composable
private fun SearchResults(items: List<SearchResult>, onOpen: (SearchResult) -> Unit, onDownload: (SearchResult) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            val rows = if (wide) items.chunked(2) else items.map { listOf(it) }
            items(rows, key = { r -> r.joinToString { it.url } }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { r -> SearchCard(r, Modifier.weight(1f), { onOpen(r) }, { onDownload(r) }) }
                    if (row.size == 1 && wide) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SearchCard(r: SearchResult, modifier: Modifier, onOpen: () -> Unit, onDownload: () -> Unit) {
    Column(
        modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            Artwork(r.thumbnail, Modifier.fillMaxSize(), corner = 18.dp)
            if (r.durationSec > 0) {
                Text(
                    Fmt.durationSec(r.durationSec),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.channel != null) {
                    Text(r.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Mb.colors.accentGradient)
                    .clickable(onClick = onDownload),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Download, "Baixar", tint = Color.White) }
        }
    }
}

// ------------------------------------------------------------------
// Gerenciador de downloads ("Meus downloads")

private enum class DlFilter(val label: String) { ALL("Todos"), ACTIVE("Baixando"), DONE("Concluídos"), FAILED("Com erro") }

@Composable
fun DownloadsManagerScreen(nav: NavController) {
    val vm = appViewModel { DownloadCenterViewModel(it) }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val actions = rememberDownloadActions(vm, nav)
    var filter by rememberSaveable { mutableStateOf(DlFilter.ALL) }

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Meus downloads", onBack = { nav.popBackStack() }, subtitle = downloads?.let { Fmt.plural(it.size, "item", "itens") })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DlFilter.entries) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        val all = downloads
        val list = all.orEmpty().filter {
            when (filter) {
                DlFilter.ALL -> true
                DlFilter.ACTIVE -> it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED
                DlFilter.DONE -> it.status == DownloadStatus.COMPLETED
                DlFilter.FAILED -> it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED
            }
        }
        when {
            all == null -> LoadingState("Carregando downloads…")
            list.isEmpty() -> StateMessage(
                Icons.Rounded.DownloadDone,
                if (filter == DlFilter.ALL) "Nenhum download ainda" else "Nada aqui",
                if (filter == DlFilter.ALL) "Abra um site na aba Baixar ou compartilhe um vídeo com o MusiBox." else null,
                actionText = if (filter == DlFilter.ALL) "Ir para Baixar" else null,
                onAction = if (filter == DlFilter.ALL) ({ nav.popBackStack(); Unit }) else null,
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                items(list, key = { it.id }) { d -> DownloadRow(d, actions.actions, Modifier.padding(horizontal = 4.dp)) }
            }
        }
    }
    actions.Dialogs()
}

// ------------------------------------------------------------------
// Ações do gerenciador (com diálogos de confirmação e pedidos do sistema)

class DownloadActionsHost(
    val actions: DownloadActions,
    private val dialogs: @Composable () -> Unit,
) {
    @Composable
    fun Dialogs() = dialogs()
}

@Composable
fun rememberDownloadActions(vm: DownloadCenterViewModel, nav: NavController): DownloadActionsHost {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val snack = rememberSnack()
    var confirmDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingSystemDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val systemDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pending = pendingSystemDelete
        pendingSystemDelete = null
        if (pending != null) {
            val (id, isMove) = pending
            if (result.resultCode == Activity.RESULT_OK) {
                if (isMove) {
                    container.music.requestRescan()
                    snack("Arquivo movido para o cofre.")
                } else {
                    vm.forgetRecord(id)
                    container.music.requestRescan()
                    snack("Arquivo excluído.")
                }
            } else {
                snack(if (isMove) "Arquivo copiado para o cofre. O original foi mantido." else "O arquivo não foi excluído.")
            }
        }
    }

    val actions = remember(vm, nav) {
        DownloadActions(
            onOpen = { d ->
                val vaultId = d.vaultItemId
                val mime = d.mimeType.orEmpty()
                when {
                    vaultId != null -> when {
                        mime.startsWith("image/") -> nav.navigate(Routes.vaultViewer(vaultId))
                        mime.startsWith("video/") || mime.startsWith("audio/") -> nav.navigate(Routes.vaultPlayer(vaultId))
                        else -> nav.navigate(Routes.VAULT)
                    }
                    d.outputUri != null -> {
                        val uri = Uri.parse(d.outputUri)
                        val songId = runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)
                        val song = container.music.index.value.byId(songId)
                        if (mime.startsWith("audio/") && song != null) {
                            container.player.playSong(song)
                            nav.navigate(Routes.PLAYER)
                        } else if (!ExternalActions.openContent(context, uri, d.mimeType)) {
                            snack("Nenhum app para abrir este arquivo.")
                        }
                    }
                    else -> snack("Arquivo não encontrado neste dispositivo.")
                }
            },
            onShare = { d ->
                val uri = d.outputUri
                if (uri == null || !ExternalActions.shareContent(context, listOf(Uri.parse(uri)), d.mimeType)) {
                    snack("Não foi possível compartilhar.")
                }
            },
            onPause = { vm.pause(it.id) },
            onResume = { vm.resume(it.id) },
            onCancel = { vm.cancel(it.id); snack("Download cancelado.") },
            onRetry = { vm.retry(it.id) },
            onMoveToVault = { d ->
                snack("Movendo para o cofre…")
                vm.moveToVault(d.id) { result ->
                    result.fold(
                        onSuccess = { r ->
                            when (r) {
                                DeleteResult.Deleted -> snack("Arquivo movido para o cofre.")
                                is DeleteResult.NeedsUserConfirmation -> {
                                    pendingSystemDelete = d.id to true
                                    systemDelete.launch(IntentSenderRequest.Builder(r.intentSender).build())
                                }
                                is DeleteResult.Failed -> snack("Arquivo copiado para o cofre. O original foi mantido.")
                            }
                        },
                        onFailure = { snack(it.message ?: "Não foi possível mover para o cofre.") },
                    )
                }
            },
            onDelete = { confirmDelete = it },
            onForget = { vm.forgetRecord(it.id); snack("Removido do histórico. O arquivo foi mantido.") },
        )
    }

    return DownloadActionsHost(actions) {
        confirmDelete?.let { d ->
            val inVault = d.destination == SaveDestination.VAULT.name
            ConfirmDialog(
                title = "Excluir arquivo?",
                message = "\"${d.title}\" será apagado ${if (inVault) "do cofre" else "do aparelho"}. Esta ação não pode ser desfeita.",
                confirmText = "Excluir",
                destructive = true,
                onConfirm = {
                    confirmDelete = null
                    vm.remove(d.id, deleteFile = true) { r ->
                        when (r) {
                            DeleteResult.Deleted -> {
                                snack("Arquivo excluído.")
                            }
                            is DeleteResult.NeedsUserConfirmation -> {
                                pendingSystemDelete = d.id to false
                                systemDelete.launch(IntentSenderRequest.Builder(r.intentSender).build())
                            }
                            is DeleteResult.Failed -> snack(r.message)
                        }
                    }
                },
                onDismiss = { confirmDelete = null },
            )
        }
    }
}

