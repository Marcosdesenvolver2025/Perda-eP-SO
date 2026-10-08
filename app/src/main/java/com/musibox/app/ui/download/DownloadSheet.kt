package com.musibox.app.ui.download

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.core.Fmt
import com.musibox.app.core.Permissions
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.download.MediaInfo
import com.musibox.app.download.OptionKind
import com.musibox.app.download.Platform
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.ExternalActions
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.PlatformIcon
import com.musibox.app.ui.theme.Mb

/** Folha "Baixar" no estilo Snaptube: prévia, lista de formatos com tamanho, destino e botão. */
@Composable
fun DownloadSheet(vm: LinkDownloadViewModel, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Mb.colors.cardHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            DownloadSheetContent(
                vm,
                onEnqueued = { msg ->
                    onMessage(msg)
                    onDismiss()
                },
                onError = onMessage,
                errorActions = {
                    val url = (vm.state.value as? AnalyzeState.Error)?.url
                    if (url != null) {
                        TextButton(onClick = { ExternalActions.openUrl(context, url) }) { Text("Abrir no app oficial") }
                    }
                },
            )
        }
    }
}

@Composable
fun DownloadSheetContent(
    vm: LinkDownloadViewModel,
    onEnqueued: (String) -> Unit,
    onError: (String) -> Unit,
    errorActions: @Composable RowScope.() -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stage by vm.stage.collectAsStateWithLifecycle()
    when (val s = state) {
        AnalyzeState.Idle, is AnalyzeState.Loading -> LoadingBlock(stage, (s as? AnalyzeState.Loading)?.firstRun == true)
        is AnalyzeState.Error -> ErrorBlock(s, onRetry = { vm.retry() }, actions = errorActions)
        is AnalyzeState.Ready -> ReadyBlock(vm, s.info, onEnqueued, onError)
    }
}

@Composable
private fun LoadingBlock(stage: String?, firstRun: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(0.86f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Mb.colors.accentGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(stage ?: "Lendo o link…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (firstRun) "Na primeira vez o motor de download é preparado. Pode levar até 1 minuto."
            else "Buscando os formatos disponíveis. Vídeos do YouTube podem levar alguns segundos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun ErrorBlock(s: AnalyzeState.Error, onRetry: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Mb.colors.danger.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (s.offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline, null, tint = Mb.colors.danger)
            }
            Spacer(Modifier.width(12.dp))
            Text(s.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        if (!s.detail.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    .border(1.dp, Mb.colors.border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text("Detalhes técnicos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(s.detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString("${s.message}\n${s.detail}\n${s.url.orEmpty()}"))
                    copied = true
                }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copiado" else "Copiar detalhes")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.url != null) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tentar de novo")
                }
            }
            actions()
        }
    }
}

@Composable
private fun ReadyBlock(vm: LinkDownloadViewModel, info: MediaInfo, onEnqueued: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var showMore by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val kinds = listOf(OptionKind.AUDIO, OptionKind.VIDEO, OptionKind.PHOTO).filter { k -> info.options.any { it.kind == k } }
    val kind = vm.selected?.kind ?: kinds.firstOrNull() ?: OptionKind.AUDIO

    Column(Modifier.fillMaxWidth()) {
        MediaHeader(info)
        Spacer(Modifier.height(16.dp))

        // Música | Vídeo
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Mb.colors.card)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            kinds.forEach { k ->
                KindTab(Modifier.weight(1f), kindIcon(k), kindLabel(k), kind == k) { vm.chooseKind(k) }
            }
        }
        Spacer(Modifier.height(6.dp))
        val options = info.options.filter { it.kind == kind }
        options.forEach { opt ->
            FormatRow(opt, opt.id == vm.selected?.id, kindIcon(opt.kind)) {
                vm.selected = opt
            }
        }
        if (info.moreOptions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showMore = !showMore }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mais formatos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Icon(if (showMore) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(showMore) {
                Column {
                    info.moreOptions.forEach { opt ->
                        FormatRow(opt, opt.id == vm.selected?.id, if (opt.kind == OptionKind.AUDIO) Icons.Rounded.MusicNote else Icons.Rounded.Movie) {
                            vm.selected = opt
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Mb.colors.border)
        Text("Salvar em", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DestChip(Modifier.weight(1f), Icons.Rounded.PhoneAndroid, "Galeria", "Fora do app", vm.destination == SaveDestination.GALLERY, MaterialTheme.colorScheme.primary) {
                vm.destination = SaveDestination.GALLERY
            }
            DestChip(Modifier.weight(1f), Icons.Rounded.Lock, "Cofre", "Privado", vm.destination == SaveDestination.VAULT, Mb.colors.vault) {
                vm.destination = SaveDestination.VAULT
            }
        }
        if (vm.destination == null && settings.askDestination) {
            Spacer(Modifier.height(6.dp))
            Text("Escolha onde o arquivo será salvo.", style = MaterialTheme.typography.bodySmall, color = Mb.colors.warning)
        }

        Spacer(Modifier.height(10.dp))
        if (rename) {
            OutlinedTextField(
                value = vm.fileName,
                onValueChange = { vm.fileName = it.take(150) },
                label = { Text("Nome do arquivo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mb.colors.border),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { rename = true }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    vm.fileName.ifBlank { info.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("Renomear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(14.dp))
        val sel = vm.selected
        GradientButton(
            text = when {
                sel == null -> "Escolha um formato"
                vm.destination == null -> "Escolha onde salvar"
                sel.estimatedBytes > 0 -> "Baixar • ${Fmt.size(sel.estimatedBytes)}"
                else -> "Baixar"
            },
            icon = Icons.Rounded.Download,
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !Permissions.hasNotifications(context)) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.enqueue(onEnqueued, onError)
            },
            enabled = sel != null && vm.destination != null,
            loading = vm.enqueueing,
            brush = if (vm.destination == SaveDestination.VAULT) Mb.colors.vaultGradient else Mb.colors.accentGradient,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun kindIcon(k: OptionKind) = when (k) {
    OptionKind.AUDIO -> Icons.Rounded.MusicNote
    OptionKind.VIDEO -> Icons.Rounded.Movie
    OptionKind.PHOTO -> Icons.Rounded.Image
}

private fun kindLabel(k: OptionKind) = when (k) {
    OptionKind.AUDIO -> "Música"
    OptionKind.VIDEO -> "Vídeo"
    OptionKind.PHOTO -> "Foto"
}

@Composable
private fun MediaHeader(info: MediaInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f),
        ) {
            Artwork(info.thumbnail, Modifier.fillMaxSize(), corner = 14.dp, fallbackIcon = Icons.Rounded.Movie)
            if (info.durationSec > 0) {
                Text(
                    Fmt.durationSec(info.durationSec),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(info.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.platform != Platform.OTHER) {
                    PlatformIcon(info.platform, 18.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    info.uploader ?: info.platform.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KindTab(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Mb.colors.accentGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DestChip(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Mb.colors.card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else Mb.colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
