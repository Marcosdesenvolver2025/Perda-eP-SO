package com.musibox.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.DownloadStatus
import com.musibox.app.ui.theme.Mb

/**
 * Indicador de download em qualquer tela do app ("Baixando… 45%") e aviso
 * "Download concluído" quando termina.
 */
@Composable
fun DownloadIndicator(visible: Boolean, onOpen: () -> Unit) {
    val container = LocalAppContainer.current
    val snack = rememberSnack()
    val list by container.downloads.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val active = list.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }

    // Avisos de conclusão/falha dentro do app
    val previous = remember { HashMap<String, String>() }
    LaunchedEffect(list) {
        for (d in list) {
            val before = previous[d.id]
            if (before != null && before != d.status) {
                when (d.status) {
                    DownloadStatus.COMPLETED -> snack("Download concluído: ${d.title}")
                    DownloadStatus.FAILED -> snack("Falha no download: ${d.title}")
                }
            }
            previous[d.id] = d.status
        }
    }

    AnimatedVisibility(
        visible && active.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val first = active.firstOrNull() ?: return@AnimatedVisibility
        val progress = if (active.isEmpty()) 0f else active.map { it.progress.coerceIn(0f, 1f) }.average().toFloat()
        val percent = (progress * 100).toInt()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0E2A6E), Color(0xFF2B1A6B))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(34.dp),
                    color = Color(0xFF1EC8FF),
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 3.dp,
                )
                Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (first.status == DownloadStatus.QUEUED && first.progress <= 0f) "Preparando download…"
                    else "Baixando… $percent%" + if (active.size > 1) " • ${Fmt.plural(active.size, "arquivo", "arquivos")}" else "",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    first.title,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Ver", color = Color(0xFF1EC8FF), style = MaterialTheme.typography.labelLarge)
        }
    }
}
