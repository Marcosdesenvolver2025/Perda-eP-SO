package com.musibox.app.ui.download

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.core.Fmt
import com.musibox.app.core.Permissions
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.download.DownloadOption
import com.musibox.app.download.MediaInfo
import com.musibox.app.download.OptionKind
import com.musibox.app.download.Platform
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.ExternalActions
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.PlatformIcon
import com.musibox.app.ui.theme.Mb

/** Painel com a pré-visualização e as escolhas do download (formato, destino e nome). */
@Composable
fun DownloadOptionsPanel(
    vm: LinkDownloadViewModel,
    onEnqueued: (String) -> Unit,
    onError: (String) -> Unit,
    onImportFile: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFormats by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    when (val s = state) {
        AnalyzeState.Idle -> Unit
        is AnalyzeState.Loading -> Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text("Carregando informações do link…", style = MaterialTheme.typography.bodyMedium)
            if (s.firstRun) {
                Text(
                    "Na primeira vez o motor de download é preparado; pode levar alguns segundos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
        is AnalyzeState.Error -> ErrorPanel(s, onRetry = { vm.retry() }, onImportFile = onImportFile, onOpenOfficial = {
            s.url?.let { ExternalActions.openUrl(context, it) }
        })
        is AnalyzeState.Ready -> {
            val info = s.info
            Column(Modifier.fillMaxWidth()) {
                PreviewCard(info)
                Spacer(Modifier.height(16.dp))
                Text("Opções de download", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                val selected = vm.selected
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val audioLabel = info.options.firstOrNull { it.kind == OptionKind.AUDIO && it.convertTo == "mp3" }?.let { "(MP3)" } ?: ""
                    KindChip(
                        Modifier.weight(1f), Icons.Rounded.MusicNote, "Apenas áudio", audioLabel,
                        selected?.kind == OptionKind.AUDIO,
                    ) { vm.chooseKind(OptionKind.AUDIO) }
                    if (info.options.any { it.kind == OptionKind.VIDEO }) {
                        KindChip(
                            Modifier.weight(1f), Icons.Rounded.Movie, "Vídeo", "(MP4)",
                            selected?.kind == OptionKind.VIDEO,
                        ) { vm.chooseKind(OptionKind.VIDEO) }
                    }
                    KindChip(Modifier.weight(1f), Icons.Rounded.Tune, "Outra qualidade", "(Selecionar)", false) { showFormats = true }
                }
                if (selected != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Selecionado: ${selected.label} • ${selected.detail}" +
                            if (selected.estimatedBytes > 0) " • ~${Fmt.size(selected.estimatedBytes)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text("Salvar em", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                DestinationPicker(vm.destination) { vm.destination = it }
                if (vm.destination == null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Escolha onde o arquivo será salvo.", style = MaterialTheme.typography.bodySmall, color = Mb.colors.warning)
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = vm.fileName,
                    onValueChange = { vm.fileName = it.take(150) },
                    label = { Text("Nome do arquivo") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mb.colors.border),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(18.dp))
                GradientButton(
                    text = "Baixar agora",
                    icon = Icons.Rounded.Download,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !Permissions.hasNotifications(context)) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.enqueue(onEnqueued, onError)
                    },
                    enabled = selected != null && vm.destination != null,
                    loading = vm.enqueueing,
                    modifier = Modifier.fillMaxWidth(),
                    brush = if (vm.destination == SaveDestination.VAULT) Mb.colors.vaultGradient else Mb.colors.accentGradient,
                )
                if (!compact) Spacer(Modifier.height(8.dp))
            }
            if (showFormats) {
                FormatPickerDialog(info, vm.selected, onSelect = { vm.selected = it; showFormats = false }, onDismiss = { showFormats = false })
            }
        }
    }
}

@Composable
private fun ErrorPanel(
    s: AnalyzeState.Error,
    onRetry: () -> Unit,
    onImportFile: (() -> Unit)?,
    onOpenOfficial: () -> Unit,
) {
    MbCard(Modifier.fillMaxWidth(), background = Mb.colors.danger.copy(alpha = 0.08f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (s.offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline, null, tint = Mb.colors.danger)
                Spacer(Modifier.width(10.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (s.url != null) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tentar de novo")
                    }
                    if (!s.offline) {
                        TextButton(onClick = onOpenOfficial) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir no app oficial")
                        }
                    }
                }
            }
            if (onImportFile != null) {
                TextButton(onClick = onImportFile) { Text("Já tem o arquivo? Importar do aparelho") }
            }
        }
    }
}

@Composable
fun PreviewCard(info: MediaInfo) {
    MbCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Artwork(info.thumbnail, Modifier.size(width = 128.dp, height = 76.dp), corner = 12.dp)
                if (info.durationSec > 0) {
                    Text(
                        Fmt.durationSec(info.durationSec),
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
                Text(info.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
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
}

@Composable
private fun KindChip(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MbCard(modifier.height(68.dp), onClick = onClick, selected = selected, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier
                .padding(horizontal = 10.dp)
                .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Galeria do dispositivo (Fora do app)" ou "Salvar no cofre (Dentro do app)". */
@Composable
fun DestinationPicker(selected: SaveDestination?, onSelect: (SaveDestination) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DestinationCard(
            Modifier.weight(1f), Icons.Rounded.PhoneAndroid, "Galeria do dispositivo", "(Fora do app)",
            selected == SaveDestination.GALLERY, MaterialTheme.colorScheme.primary,
        ) { onSelect(SaveDestination.GALLERY) }
        DestinationCard(
            Modifier.weight(1f), Icons.Rounded.Lock, "Salvar no cofre", "(Dentro do app)",
            selected == SaveDestination.VAULT, Mb.colors.vault,
        ) { onSelect(SaveDestination.VAULT) }
    }
}

@Composable
private fun DestinationCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    MbCard(modifier.height(78.dp), onClick = onClick, selected = selected, selectedColor = accent, shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Lista "Baixar como": Música / Vídeo com tamanhos, e "Mais formatos". */
@Composable
fun FormatList(info: MediaInfo, selected: DownloadOption?, onSelect: (DownloadOption) -> Unit) {
    var showMore by remember { mutableStateOf(false) }
    Column {
        val audio = info.options.filter { it.kind == OptionKind.AUDIO }
        val video = info.options.filter { it.kind == OptionKind.VIDEO }
        if (audio.isNotEmpty()) {
            GroupLabel("Música")
            audio.forEach { FormatRow(it, it.id == selected?.id, Icons.Rounded.MusicNote) { onSelect(it) } }
        }
        if (video.isNotEmpty()) {
            GroupLabel("Vídeo")
            video.forEach { FormatRow(it, it.id == selected?.id, Icons.Rounded.Movie) { onSelect(it) } }
        }
        if (info.moreOptions.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Mb.colors.border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showMore = !showMore }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mais formatos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("Todos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Icon(if (showMore) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(showMore) {
                Column {
                    info.moreOptions.forEach { opt ->
                        FormatRow(opt, opt.id == selected?.id, if (opt.kind == OptionKind.AUDIO) Icons.Rounded.MusicNote else Icons.Rounded.Movie) { onSelect(opt) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun FormatRow(option: DownloadOption, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge)
            if (option.detail.isNotBlank()) {
                Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (option.estimatedBytes > 0) {
            Text(Fmt.size(option.estimatedBytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, "Selecionado", tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = Mb.colors.border)
        }
    }
}

@Composable
fun FormatPickerDialog(info: MediaInfo, selected: DownloadOption?, onSelect: (DownloadOption) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Baixar como") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) { FormatList(info, selected, onSelect) }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        containerColor = Mb.colors.cardHigh,
    )
}

/** Diálogo "onde salvar" usado na importação de arquivos. */
@Composable
fun DestinationDialog(onChoose: (SaveDestination) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Onde salvar?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DestinationOption(
                    Icons.Rounded.PhoneAndroid,
                    "Salvar na galeria/dispositivo",
                    "O arquivo ficará acessível normalmente pelo Android e por outros aplicativos.",
                    MaterialTheme.colorScheme.primary,
                ) { onChoose(SaveDestination.GALLERY) }
                DestinationOption(
                    Icons.Rounded.Lock,
                    "Salvar no cofre",
                    "O arquivo ficará criptografado na área privada do app e não aparecerá na galeria.",
                    Mb.colors.vault,
                ) { onChoose(SaveDestination.VAULT) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Mb.colors.cardHigh,
    )
}

@Composable
private fun DestinationOption(icon: ImageVector, title: String, text: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Mb.colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
