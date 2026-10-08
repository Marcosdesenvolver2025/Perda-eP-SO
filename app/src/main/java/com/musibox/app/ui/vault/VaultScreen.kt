package com.musibox.app.ui.vault

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.VaultCategory
import com.musibox.app.data.db.VaultItemEntity
import com.musibox.app.storage.DeleteResult
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.OverflowMenu
import com.musibox.app.ui.components.StateMessage
import com.musibox.app.ui.components.TextInputDialog
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb
import com.musibox.app.vault.VaultThumb

private data class VaultTab(val category: String, val label: String, val icon: ImageVector, val mimes: Array<String>)

private val vaultTabs = listOf(
    VaultTab(VaultCategory.PHOTO, "Fotos", Icons.Rounded.Image, arrayOf("image/*")),
    VaultTab(VaultCategory.VIDEO, "Vídeos", Icons.Rounded.Movie, arrayOf("video/*")),
    VaultTab(VaultCategory.AUDIO, "Áudios", Icons.Rounded.MusicNote, arrayOf("audio/*")),
    VaultTab(VaultCategory.FILE, "Arquivos", Icons.AutoMirrored.Rounded.InsertDriveFile, arrayOf("*/*")),
)

const val EXPORT_WARNING = "Este arquivo ficará visível fora do cofre. Deseja continuar?"

@Composable
fun VaultScreen(nav: NavController) {
    VaultGate { VaultContent(nav) }
}

private sealed interface VaultDialog {
    data class Export(val ids: List<String>, val remove: Boolean) : VaultDialog
    data class Delete(val ids: List<String>) : VaultDialog
    data class Rename(val item: VaultItemEntity) : VaultDialog
    data class Move(val ids: List<String>) : VaultDialog
    data class Share(val id: String, val share: Boolean) : VaultDialog
    data class ImportMode(val uris: List<Uri>) : VaultDialog
}

@Composable
private fun VaultContent(nav: NavController) {
    val vm = appViewModel { VaultViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snack = rememberSnack()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var selectMode by remember { mutableStateOf(false) }
    var fabOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<VaultDialog?>(null) }

    val all = items
    val current = vaultTabs[tab]
    val inTab = all.orEmpty().filter { it.category == current.category }
    val folders = inTab.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    val visible = inTab
        .filter { folder == null || it.folder == folder }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    fun clearSelection() {
        selection = emptySet()
        selectMode = false
    }

    BackHandler(enabled = selectMode || fabOpen || searching) {
        when {
            fabOpen -> fabOpen = false
            selectMode -> clearSelection()
            searching -> { searching = false; query = "" }
        }
    }

    val deleteOriginalsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        snack(
            if (result.resultCode == Activity.RESULT_OK) "Arquivos movidos para o cofre."
            else "Arquivos copiados para o cofre. Os originais foram mantidos.",
        )
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) dialog = VaultDialog.ImportMode(uris)
    }

    fun startPick(t: VaultTab) {
        fabOpen = false
        vm.expectExternal()
        pickLauncher.launch(t.mimes)
    }

    fun runImport(uris: List<Uri>, move: Boolean) {
        vm.import(uris, folder.orEmpty()) { imported, message ->
            if (!move || imported.isEmpty()) {
                snack(message)
                return@import
            }
            when (val r = vm.deleteOriginals(imported)) {
                DeleteResult.Deleted -> snack("Arquivos movidos para o cofre.")
                is DeleteResult.NeedsUserConfirmation -> {
                    vm.expectExternal()
                    deleteOriginalsLauncher.launch(IntentSenderRequest.Builder(r.intentSender).build())
                }
                is DeleteResult.Failed -> snack("Arquivos copiados para o cofre. ${r.message} O original foi mantido.")
            }
        }
    }

    fun openItem(item: VaultItemEntity) {
        when (item.category) {
            VaultCategory.PHOTO -> nav.navigate(Routes.vaultViewer(item.id))
            VaultCategory.VIDEO, VaultCategory.AUDIO -> nav.navigate(Routes.vaultPlayer(item.id))
            else -> dialog = VaultDialog.Share(item.id, share = false)
        }
    }

    fun itemMenu(item: VaultItemEntity) = listOf(
        MenuAction("Abrir", Icons.AutoMirrored.Rounded.OpenInNew) { openItem(item) },
        MenuAction("Renomear", Icons.Rounded.Edit) { dialog = VaultDialog.Rename(item) },
        MenuAction("Mover para pasta", Icons.AutoMirrored.Rounded.DriveFileMove) { dialog = VaultDialog.Move(listOf(item.id)) },
        MenuAction("Exportar", Icons.Rounded.Upload) { dialog = VaultDialog.Export(listOf(item.id), remove = false) },
        MenuAction("Compartilhar", Icons.Rounded.Share) { dialog = VaultDialog.Share(item.id, share = true) },
        MenuAction("Remover do cofre", Icons.Rounded.LockOpen) { dialog = VaultDialog.Export(listOf(item.id), remove = true) },
        MenuAction("Excluir permanentemente", Icons.Rounded.DeleteForever, destructive = true) { dialog = VaultDialog.Delete(listOf(item.id)) },
    )

    val onItemClick: (VaultItemEntity) -> Unit = { item ->
        if (selectMode) {
            selection = if (item.id in selection) selection - item.id else selection + item.id
            if (selection.isEmpty()) selectMode = false
        } else {
            openItem(item)
        }
    }
    val onItemLongClick: (VaultItemEntity) -> Unit = { item ->
        selectMode = true
        selection = selection + item.id
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---------- Topo ----------
            if (selectMode) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { clearSelection() }) { Icon(Icons.Rounded.Close, "Cancelar seleção") }
                    Text("${selection.size} selecionado(s)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selection = visible.mapTo(HashSet()) { it.id } }) { Icon(Icons.Rounded.SelectAll, "Selecionar tudo") }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cofre", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${Fmt.plural(all.orEmpty().size, "item", "itens")} • ${Fmt.size(all.orEmpty().sumOf { it.size })} • Criptografado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MbIconButton(Icons.Rounded.Lock, "Bloquear agora", { vm.lockNow() })
                    Spacer(Modifier.width(8.dp))
                    MbIconButton(Icons.Rounded.Checklist, "Selecionar", { selectMode = true })
                    Spacer(Modifier.width(8.dp))
                    MbIconButton(Icons.Rounded.Search, "Pesquisar", { searching = !searching; if (!searching) query = "" })
                    Spacer(Modifier.width(8.dp))
                    MbIconButton(Icons.Rounded.Settings, "Configurações do cofre", { nav.navigate(Routes.VAULT_SETTINGS) })
                }
            }
            AnimatedVisibility(searching && !selectMode) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Pesquisar no cofre") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Mb.colors.card,
                        unfocusedContainerColor = Mb.colors.card,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ---------- Abas ----------
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = Mb.colors.vault,
                divider = {},
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[tab]),
                        color = Mb.colors.vault,
                    )
                },
            ) {
                vaultTabs.forEachIndexed { i, t ->
                    val count = all.orEmpty().count { it.category == t.category }
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i; folder = null; clearSelection() },
                        text = {
                            Text(
                                if (count > 0) "${t.label} $count" else t.label,
                                fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (folders.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FolderChip("Todas", folder == null) { folder = null } }
                    items(folders) { f -> FolderChip(f, folder == f) { folder = f } }
                }
            }

            // ---------- Conteúdo ----------
            when {
                all == null -> LoadingState("Abrindo o cofre…")
                visible.isEmpty() -> StateMessage(
                    current.icon,
                    if (query.isNotBlank()) "Nada encontrado" else "Nenhum item em ${current.label.lowercase()}",
                    if (query.isNotBlank()) null else "Os arquivos do cofre ficam criptografados e não aparecem na galeria.",
                    actionText = if (query.isBlank()) "Adicionar ${current.label.lowercase()}" else null,
                    onAction = { startPick(current) },
                    tint = Mb.colors.vault,
                )
                current.category == VaultCategory.PHOTO -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(visible, key = { it.id }) { item ->
                        PhotoCell(item, settings.vaultHideThumbnails, item.id in selection, selectMode, { onItemClick(item) }, { onItemLongClick(item) })
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(visible, key = { it.id }) { item ->
                        VaultRow(
                            item, settings.vaultHideThumbnails, item.id in selection, selectMode,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                            menu = if (selectMode) emptyList() else itemMenu(item),
                        )
                    }
                }
            }
        }

        // ---------- Barra de seleção ----------
        if (selectMode && selection.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Mb.colors.cardHigh)
                    .border(1.dp, Mb.colors.border, RoundedCornerShape(22.dp))
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val ids = selection.toList()
                SelectionAction(Icons.Rounded.Upload, "Exportar") { dialog = VaultDialog.Export(ids, false) }
                SelectionAction(Icons.AutoMirrored.Rounded.DriveFileMove, "Mover") { dialog = VaultDialog.Move(ids) }
                SelectionAction(Icons.Rounded.LockOpen, "Remover") { dialog = VaultDialog.Export(ids, true) }
                SelectionAction(Icons.Rounded.DeleteForever, "Excluir", Mb.colors.danger) { dialog = VaultDialog.Delete(ids) }
            }
        } else if (!selectMode) {
            AddFab(Modifier.align(Alignment.BottomEnd), fabOpen, onToggle = { fabOpen = !fabOpen }, onPick = { startPick(it) })
        }

        vm.progress?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(p.label) },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { if (p.total > 0) (p.done.toFloat() / p.total) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Mb.colors.vault,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${p.done + 1} de ${p.total}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                containerColor = Mb.colors.cardHigh,
            )
        }
    }

    // ---------- Diálogos ----------
    when (val d = dialog) {
        null -> Unit
        is VaultDialog.ImportMode -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Adicionar ao cofre") },
            text = {
                Text(
                    "Copiar mantém o arquivo original onde está. Mover apaga o original depois de salvar no cofre " +
                        "(o Android pedirá sua confirmação).",
                )
            },
            confirmButton = {
                TextButton(onClick = { dialog = null; runImport(d.uris, move = true) }) { Text("Mover para o cofre") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null; runImport(d.uris, move = false) }) { Text("Copiar para o cofre") }
            },
            containerColor = Mb.colors.cardHigh,
        )
        is VaultDialog.Export -> ConfirmDialog(
            title = if (d.remove) "Remover do cofre?" else "Exportar?",
            message = EXPORT_WARNING + if (d.remove) "\n\nO item será salvo no aparelho e apagado do cofre." else "",
            confirmText = "Continuar",
            onConfirm = {
                dialog = null
                clearSelection()
                vm.export(d.ids, d.remove) { snack(it) }
            },
            onDismiss = { dialog = null },
        )
        is VaultDialog.Delete -> ConfirmDialog(
            title = "Excluir permanentemente?",
            message = "${Fmt.plural(d.ids.size, "item será apagado", "itens serão apagados")} do cofre. Esta ação não pode ser desfeita.",
            confirmText = "Excluir",
            destructive = true,
            onConfirm = {
                dialog = null
                clearSelection()
                vm.delete(d.ids) { snack(it) }
            },
            onDismiss = { dialog = null },
        )
        is VaultDialog.Rename -> TextInputDialog(
            title = "Renomear",
            initial = d.item.name.substringBeforeLast('.'),
            label = "Nome",
            confirmText = "Salvar",
            onConfirm = { dialog = null; vm.rename(d.item.id, it) },
            onDismiss = { dialog = null },
        )
        is VaultDialog.Move -> MoveDialog(
            folders = all.orEmpty().map { it.folder }.filter { it.isNotBlank() }.distinct().sorted(),
            onChoose = { target ->
                dialog = null
                clearSelection()
                vm.move(d.ids, target) { snack(it) }
            },
            onDismiss = { dialog = null },
        )
        is VaultDialog.Share -> ConfirmDialog(
            title = if (d.share) "Compartilhar?" else "Abrir com outro app?",
            message = EXPORT_WARNING,
            confirmText = "Continuar",
            onConfirm = {
                dialog = null
                vm.handOff(d.id, d.share) { snack(it) }
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Mb.colors.vault.copy(alpha = 0.2f),
            selectedLabelColor = Mb.colors.vault,
        ),
    )
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun PhotoCell(
    item: VaultItemEntity,
    hideThumb: Boolean,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Artwork(
            if (hideThumb || !item.hasThumb) null else VaultThumb(item.id),
            Modifier.fillMaxSize(),
            corner = 6.dp,
            fallbackIcon = Icons.Rounded.Image,
        )
        if (selectMode) SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier) {
    Icon(
        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        if (selected) "Selecionado" else "Não selecionado",
        tint = if (selected) Mb.colors.vault else Color.White,
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f)),
    )
}

@Composable
private fun VaultRow(
    item: VaultItemEntity,
    hideThumb: Boolean,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: List<MenuAction>,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (item.category) {
            VaultCategory.VIDEO -> Icons.Rounded.Movie
            VaultCategory.AUDIO -> Icons.Rounded.AudioFile
            else -> Icons.AutoMirrored.Rounded.InsertDriveFile
        }
        Box {
            Artwork(
                if (hideThumb || !item.hasThumb) null else VaultThumb(item.id),
                Modifier.size(width = if (item.category == VaultCategory.VIDEO) 108.dp else 60.dp, height = 64.dp),
                corner = 12.dp,
                fallbackIcon = icon,
            )
            if (item.category == VaultCategory.VIDEO) {
                Icon(
                    Icons.Rounded.PlayArrow, null, tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
            }
            if (selectMode) SelectMark(selected, Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val details = buildList {
                if (item.durationMs > 0) add(Fmt.duration(item.durationMs))
                add(Fmt.size(item.size))
                if (item.folder.isNotBlank()) add(item.folder)
            }.joinToString("  |  ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OverflowMenu(menu)
    }
}

@Composable
private fun AddFab(modifier: Modifier, open: Boolean, onToggle: () -> Unit, onPick: (VaultTab) -> Unit) {
    Column(
        modifier.padding(end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(open, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vaultTabs.reversed().forEach { t ->
                    Row(
                        Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Mb.colors.vaultGradient)
                            .clickable { onPick(t) }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text(t.label.removeSuffix("s").let { if (t.category == VaultCategory.AUDIO) "Áudio" else it }, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        Row(
            Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .then(if (open) Modifier.background(Mb.colors.cardHigh) else Modifier.background(Mb.colors.vaultGradient))
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (open) Icons.Rounded.Close else Icons.Rounded.Add, null, tint = Color.White)
            if (!open) {
                Spacer(Modifier.width(8.dp))
                Text("ADICIONAR", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun MoveDialog(folders: List<String>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog("Nova pasta", "", "Nome da pasta", "Mover", onConfirm = { onChoose(it) }, onDismiss = { creating = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover para pasta") },
        text = {
            Column {
                MoveOption("Nova pasta…", Icons.Rounded.Add) { creating = true }
                MoveOption("Sem pasta", Icons.AutoMirrored.Rounded.InsertDriveFile) { onChoose("") }
                folders.forEach { f -> MoveOption(f, Icons.AutoMirrored.Rounded.DriveFileMove) { onChoose(f) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Mb.colors.cardHigh,
    )
}

@Composable
private fun MoveOption(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Mb.colors.vault)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
