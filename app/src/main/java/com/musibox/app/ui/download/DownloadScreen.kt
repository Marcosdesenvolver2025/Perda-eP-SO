package com.musibox.app.ui.download

import android.app.Activity
import android.content.ContentUris
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.DownloadEntity
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.download.Platform
import com.musibox.app.storage.DeleteResult
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.ExternalActions
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.MorePlatformsDialog
import com.musibox.app.ui.components.OfflineBanner
import com.musibox.app.ui.components.PlatformTiles
import com.musibox.app.ui.components.SectionTitle
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb

private val downloadTabs = listOf("Link", "Pesquisar", "Downloads")

@Composable
fun DownloadScreen(nav: NavController, initialTab: Int, initialUrl: String?) {
    val linkVm = appViewModel { LinkDownloadViewModel(it) }
    val centerVm = appViewModel { DownloadCenterViewModel(it) }
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val snack = rememberSnack()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var handledInitial by rememberSaveable { mutableStateOf(false) }
    val online by centerVm.online.collectAsStateWithLifecycle()
    val settings by linkVm.settings.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<List<Uri>?>(null) }
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(initialUrl) {
        if (!handledInitial && !initialUrl.isNullOrBlank()) {
            handledInitial = true
            if (initialTab == 1) {
                centerVm.query = initialUrl
                centerVm.runSearch()
            } else {
                linkVm.link = initialUrl
                linkVm.analyze(initialUrl)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            if (settings.askDestination) pendingImport = uris
            else centerVm.importFiles(uris, settings.defaultDestination) { snack(it) }
        }
    }
    val startImport = { importLauncher.launch(arrayOf("audio/*", "video/*")) }
    val actions = rememberDownloadActions(centerVm, nav)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        MbTopBar("Baixar", onBack = { nav.popBackStack() })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            downloadTabs.forEachIndexed { i, label ->
                SegmentTab(label, tab == i, Modifier.weight(1f)) { tab = i }
            }
            MbIconButton(Icons.Rounded.FileOpen, "Importar arquivo do aparelho", startImport, size = 46.dp)
        }
        OfflineBanner(!online)

        when (tab) {
            0 -> LinkTab(linkVm, centerVm, nav, actions, onImport = startImport, onMore = { showMore = true }, onSeeAll = { tab = 2 })
            1 -> SearchTab(centerVm, onPick = { url ->
                tab = 0
                linkVm.link = url
                linkVm.analyze(url)
            }, onImport = startImport)
            2 -> DownloadsTab(centerVm, actions)
        }
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
    if (showMore) {
        MorePlatformsDialog(onDismiss = { showMore = false }, onPaste = {
            showMore = false
            tab = 0
        })
    }
    actions.Dialogs()
}

@Composable
private fun SegmentTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Mb.colors.card)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Mb.colors.border,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LinkTab(
    vm: LinkDownloadViewModel,
    center: DownloadCenterViewModel,
    nav: NavController,
    actions: DownloadActionsHost,
    onImport: () -> Unit,
    onMore: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val snack = rememberSnack()
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by center.downloads.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Mb.colors.card)
                .border(1.dp, Mb.colors.border, RoundedCornerShape(22.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
            TextField(
                value = vm.link,
                onValueChange = { vm.link = it },
                placeholder = { Text("Cole o link aqui...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    vm.analyze(vm.link)
                }),
                trailingIcon = {
                    if (vm.link.isNotEmpty()) {
                        IconButton(onClick = { vm.reset() }) { Icon(Icons.Rounded.Cancel, "Limpar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Mb.colors.accentGradient)
                    .clickable {
                        focus.clearFocus()
                        val text = clipboard.getText()?.text
                        if (text.isNullOrBlank()) {
                            if (vm.link.isNotBlank()) vm.analyze(vm.link) else snack("Nenhum link copiado.")
                        } else {
                            vm.link = text
                            vm.analyze(text)
                        }
                    }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (vm.link.isNotBlank() && state !is AnalyzeState.Ready) "Buscar" else "Colar", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(14.dp))
        PlatformTiles(onPlatform = { p ->
            ExternalActions.openPlatform(context, p)
            snack("No ${p.label}, toque em Compartilhar e escolha \"Baixar com MusiBox\".")
        }, onMore = onMore)

        Spacer(Modifier.height(16.dp))
        if (state is AnalyzeState.Idle) {
            MbCard(Modifier.fillMaxWidth(), background = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Abra o vídeo no YouTube, TikTok ou Instagram, toque em Compartilhar e escolha \"Baixar com MusiBox\". " +
                            "Ou copie o link e toque em Colar.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        DownloadOptionsPanel(
            vm,
            onEnqueued = { msg ->
                snack(msg)
                vm.reset()
            },
            onError = { snack(it) },
            onImportFile = onImport,
        )

        Spacer(Modifier.height(20.dp))
        val list = downloads.orEmpty()
        SectionTitle("Downloads recentes", actionText = if (list.isNotEmpty()) "Ver todos" else null, onAction = onSeeAll)
        if (list.isEmpty()) {
            Text(
                "Os arquivos que você baixar aparecem aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            list.take(4).forEach { d -> DownloadRow(d, actions.actions) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SearchTab(vm: DownloadCenterViewModel, onPick: (String) -> Unit, onImport: () -> Unit) {
    val state by vm.search.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Pesquisar no YouTube") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.runSearch()
            }),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Mb.colors.card,
                unfocusedContainerColor = Mb.colors.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (val s = state) {
            SearchState.Idle -> Column {
                MbCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = onImport,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FileOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Importar arquivo do aparelho", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Traga músicas ou vídeos que você já tem para a pasta do MusiBox ou para o cofre.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                StateMessage(Icons.Rounded.Search, "Pesquise músicas e vídeos", "Digite um nome e toque em pesquisar no teclado.")
            }
            SearchState.Loading -> LoadingState("Pesquisando…")
            is SearchState.Error -> StateMessage(Icons.Rounded.SearchOff, s.message, actionText = "Tentar novamente", onAction = { vm.runSearch() })
            is SearchState.Results -> if (s.items.isEmpty()) {
                StateMessage(Icons.Rounded.SearchOff, "Nada encontrado para \"${s.query}\"")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(s.items, key = { it.url }) { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(r.url) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                Artwork(r.thumbnail, Modifier.size(width = 120.dp, height = 68.dp), corner = 12.dp)
                                if (r.durationSec > 0) {
                                    Text(
                                        Fmt.durationSec(r.durationSec),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (r.channel != null) {
                                    Text(r.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsTab(vm: DownloadCenterViewModel, actions: DownloadActionsHost) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val list = downloads
    when {
        list == null -> LoadingState("Carregando downloads…")
        list.isEmpty() -> StateMessage(
            Icons.Rounded.DownloadDone,
            "Nenhum download ainda",
            "Compartilhe um vídeo com o MusiBox ou cole um link na aba Link.",
        )
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.id }) { d -> DownloadRow(d, actions.actions, Modifier.padding(horizontal = 4.dp)) }
        }
    }
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

@Suppress("unused")
private fun platformOf(name: String) = runCatching { Platform.valueOf(name) }.getOrDefault(Platform.OTHER)
