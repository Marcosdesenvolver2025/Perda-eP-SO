package com.musibox.app.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.DownloadEntity
import com.musibox.app.data.db.DownloadKind
import com.musibox.app.data.db.DownloadStatus
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.download.Platform
import com.musibox.app.ui.components.Artwork
import com.musibox.app.ui.components.MbIconButton
import com.musibox.app.ui.components.MenuAction
import com.musibox.app.ui.components.OverflowMenu
import com.musibox.app.ui.components.PlatformIcon
import com.musibox.app.ui.theme.Mb

class DownloadActions(
    val onOpen: (DownloadEntity) -> Unit,
    val onShare: (DownloadEntity) -> Unit,
    val onPause: (DownloadEntity) -> Unit,
    val onResume: (DownloadEntity) -> Unit,
    val onCancel: (DownloadEntity) -> Unit,
    val onRetry: (DownloadEntity) -> Unit,
    val onMoveToVault: (DownloadEntity) -> Unit,
    val onDelete: (DownloadEntity) -> Unit,
    val onForget: (DownloadEntity) -> Unit,
)

fun DownloadEntity.statusText(): String {
    val inVault = destination == SaveDestination.VAULT.name
    val formatShort = (mimeType?.substringAfter('/')?.uppercase()?.let { if (it == "MPEG") "MP3" else it } ?: targetExt.uppercase())
    return when (status) {
        DownloadStatus.RUNNING -> buildString {
            append("${(progress * 100).toInt()}%")
            if (speedBps > 0) append(" • ${Fmt.speed(speedBps)}")
            if (totalBytes > 0) append(" • ${Fmt.size(totalBytes)}")
        }
        DownloadStatus.QUEUED -> errorMessage ?: "Na fila…"
        DownloadStatus.PAUSED -> "Pausado • ${(progress * 100).toInt()}%"
        DownloadStatus.COMPLETED -> buildString {
            append(formatShort)
            if (totalBytes > 0) append(" • ${Fmt.size(totalBytes)}")
            append(if (inVault) " • No cofre" else " • No aparelho")
            if (kind == DownloadKind.IMPORT) append(" • Importado")
        }
        DownloadStatus.FAILED -> errorMessage ?: "Não foi possível baixar este arquivo."
        DownloadStatus.CANCELED -> "Cancelado"
        else -> status
    }
}

@Composable
fun DownloadRow(d: DownloadEntity, actions: DownloadActions, modifier: Modifier = Modifier) {
    val platform = runCatching { Platform.valueOf(d.platform) }.getOrDefault(Platform.OTHER)
    val inVault = d.destination == SaveDestination.VAULT.name
    val active = d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.QUEUED || d.status == DownloadStatus.PAUSED
    val color = when (d.status) {
        DownloadStatus.FAILED -> Mb.colors.danger
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.PAUSED, DownloadStatus.CANCELED -> Mb.colors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = d.status == DownloadStatus.COMPLETED) { actions.onOpen(d) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Artwork(
                    d.thumbnailUrl,
                    Modifier.size(width = 96.dp, height = 60.dp),
                    corner = 12.dp,
                    fallbackIcon = when {
                        d.isAudio -> Icons.Rounded.MusicNote
                        d.mimeType?.startsWith("video/") == true -> Icons.Rounded.Movie
                        d.kind == DownloadKind.IMPORT -> Icons.AutoMirrored.Rounded.InsertDriveFile
                        else -> Icons.Rounded.Movie
                    },
                )
                if (d.isAudio && d.status == DownloadStatus.COMPLETED && d.thumbnailUrl != null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(3.dp)) {
                    if (inVault) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Mb.colors.vault),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Lock, "No cofre", tint = Color.White, modifier = Modifier.size(13.dp)) }
                    } else if (platform != Platform.OTHER && platform != Platform.LOCAL) {
                        PlatformIcon(platform, 22.dp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (d.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(d.statusText(), style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            when (d.status) {
                DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                    MbIconButton(Icons.Rounded.Pause, "Pausar", { actions.onPause(d) }, size = 40.dp, background = MaterialTheme.colorScheme.surfaceContainerHighest)
                DownloadStatus.PAUSED ->
                    MbIconButton(Icons.Rounded.PlayArrow, "Continuar", { actions.onResume(d) }, size = 40.dp, background = MaterialTheme.colorScheme.surfaceContainerHighest)
                DownloadStatus.FAILED, DownloadStatus.CANCELED ->
                    if (d.kind == DownloadKind.DOWNLOAD) {
                        MbIconButton(Icons.Rounded.Refresh, "Tentar novamente", { actions.onRetry(d) }, size = 40.dp, background = MaterialTheme.colorScheme.surfaceContainerHighest)
                    }
                else -> Unit
            }
            OverflowMenu(
                buildList {
                    if (d.status == DownloadStatus.COMPLETED) {
                        add(MenuAction("Abrir", Icons.AutoMirrored.Rounded.OpenInNew) { actions.onOpen(d) })
                        if (!inVault) {
                            add(MenuAction("Compartilhar", Icons.Rounded.Share) { actions.onShare(d) })
                            add(MenuAction("Mover para o cofre", Icons.Rounded.Lock) { actions.onMoveToVault(d) })
                        }
                    }
                    if (active) add(MenuAction("Cancelar", Icons.Rounded.Close, destructive = true) { actions.onCancel(d) })
                    if (d.status == DownloadStatus.FAILED || d.status == DownloadStatus.CANCELED) {
                        if (d.kind == DownloadKind.DOWNLOAD) add(MenuAction("Tentar novamente", Icons.Rounded.Refresh) { actions.onRetry(d) })
                    }
                    if (!active) add(MenuAction("Remover do histórico", Icons.Rounded.HistoryToggleOff) { actions.onForget(d) })
                    if (d.status == DownloadStatus.COMPLETED) {
                        add(MenuAction("Excluir arquivo", Icons.Rounded.DeleteOutline, destructive = true) { actions.onDelete(d) })
                    }
                },
            )
        }
        if (active) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { d.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (d.status == DownloadStatus.PAUSED) Mb.colors.warning else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}
