package com.musibox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.musibox.app.core.Fmt
import com.musibox.app.core.Permissions
import com.musibox.app.storage.StorageInfo
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LoadingState
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.rememberPermissionState
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.theme.Mb
import com.musibox.app.ui.theme.MbPalette
import kotlinx.coroutines.launch

@Composable
fun StorageScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var info by remember { mutableStateOf<StorageInfo?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val visual = rememberPermissionState(Permissions.visualMedia, { Permissions.hasImages(context) || Permissions.hasVideos(context) }, onGranted = { reload++ })

    LaunchedEffect(reload) { info = container.storage.load() }

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Armazenamento", onBack = { nav.popBackStack() })
        val i = info
        if (i == null) {
            LoadingState("Calculando espaço…")
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val rows = listOf(
                Triple("Músicas", i.music, Icons.Rounded.MusicNote) to MbPalette.Pink,
                Triple("Vídeos", i.videos, Icons.Rounded.Movie) to MbPalette.Orange,
                Triple("Imagens", i.images, Icons.Rounded.Image) to MbPalette.Green,
                Triple("Cofre", i.vault, Icons.Rounded.Lock) to MbPalette.PurpleBright,
                Triple("Downloads do MusiBox", i.downloads, Icons.Rounded.Download) to MbPalette.Blue,
                Triple("Cache", i.cache, Icons.Rounded.Cached) to MbPalette.TextSecondary,
            )
            val max = rows.maxOf { it.first.second ?: 0L }.coerceAtLeast(1)
            MbCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    rows.forEach { (row, color) ->
                        val (label, size, icon) = row
                        StorageRow(icon, label, size, max, color, onRequest = if (size == null && label != "Músicas") visual.request else null)
                        Spacer(Modifier.height(14.dp))
                    }
                    if (i.appTotal != null) {
                        StorageRow(Icons.Rounded.PhoneAndroid, "Total usado pelo app", i.appTotal, max.coerceAtLeast(i.appTotal), MaterialTheme.colorScheme.primary, null)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Limpar o cache apaga apenas arquivos temporários e miniaturas. Suas músicas, os arquivos baixados, o cofre, " +
                    "playlists e favoritos não são apagados. Downloads pausados continuam de onde pararam.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            GradientButton(
                "Limpar cache",
                onClick = { confirm = true },
                icon = Icons.Rounded.CleaningServices,
                loading = clearing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Limpar cache?",
            message = "Somente arquivos temporários serão apagados (${Fmt.size(info?.cache ?: 0)}).",
            confirmText = "Limpar",
            onConfirm = {
                confirm = false
                clearing = true
                scope.launch {
                    val freed = container.storage.clearCache()
                    clearing = false
                    snack("Cache limpo: ${Fmt.size(freed)} liberados.")
                    reload++
                }
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun StorageRow(icon: ImageVector, label: String, size: Long?, max: Long, color: Color, onRequest: (() -> Unit)?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (size != null) {
                Text(Fmt.size(size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (onRequest != null) {
                TextButton(onClick = onRequest) { Text("Permitir para calcular") }
            } else {
                Text("Sem permissão", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (size != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (size.toFloat() / max).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = Mb.colors.border,
                drawStopIndicator = {},
            )
        }
    }
}
