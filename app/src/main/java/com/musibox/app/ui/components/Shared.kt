package com.musibox.app.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.data.db.Song
import com.musibox.app.download.Platform
import com.musibox.app.ui.theme.Mb
import kotlinx.coroutines.launch

@Composable
fun PlatformTiles(modifier: Modifier = Modifier, onPlatform: (Platform) -> Unit, onMore: () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Platform.YOUTUBE, Platform.TIKTOK, Platform.INSTAGRAM).forEach { p ->
            PlatformTile(Modifier.weight(1f), p.label, { PlatformIcon(p, 48.dp) }) { onPlatform(p) }
        }
        PlatformTile(Modifier.weight(1f), "Mais", { MorePlatformsIcon(48.dp) }, onMore)
    }
}

@Composable
private fun PlatformTile(modifier: Modifier, label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    MbCard(modifier.height(110.dp), onClick = onClick) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun MorePlatformsDialog(onDismiss: () -> Unit, onPaste: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Como baixar") },
        text = {
            Column {
                Text(
                    "1. Abra o vídeo ou música no app (YouTube, TikTok, Instagram, Facebook, X, Vimeo, SoundCloud, Dailymotion e muitos outros sites).\n" +
                        "2. Toque em Compartilhar.\n" +
                        "3. Escolha \"Baixar com MusiBox\".\n\n" +
                        "Você também pode copiar o link e colar na tela Baixar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Conteúdo protegido (DRM), privado ou que exige login não pode ser baixado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onPaste) { Text("Colar um link") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        containerColor = Mb.colors.cardHigh,
    )
}

/** Escolher uma playlist (ou criar uma nova) para adicionar músicas. */
@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val playlists by container.library.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        TextInputDialog(
            title = "Nova playlist",
            initial = "",
            label = "Nome da playlist",
            confirmText = "Criar",
            onConfirm = { name ->
                scope.launch {
                    val id = container.library.createPlaylist(name)
                    val added = container.library.addToPlaylist(id, songs)
                    snack(if (added > 0) "Adicionada à playlist \"$name\"." else "A playlist foi criada.")
                    onDismiss()
                }
            },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar à playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { creating = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Nova playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Mb.colors.border)
                }
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            "Você ainda não tem playlists.",
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(playlists, key = { it.playlist.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val added = container.library.addToPlaylist(p.playlist.id, songs)
                                    snack(
                                        if (added > 0) "Adicionada à playlist \"${p.playlist.name}\"."
                                        else "Essa música já está na playlist.",
                                    )
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.playlist.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${p.count} músicas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        containerColor = Mb.colors.cardHigh,
    )
}
