package com.musibox.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musibox.app.browser.BrowserStore
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.components.neoGlass

/** Ícone do site (favicon) num quadradinho escuro. */
@Composable
fun SiteBadge(url: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .neoGlass(RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) { Favicon(url, size * 0.62f) }
}

/** Abas abertas, em cartões (como o Chrome). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(
    tabs: List<BrowserStore.Tab>,
    currentId: String?,
    onSelect: (BrowserStore.Tab) -> Unit,
    onClose: (BrowserStore.Tab) -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.musibox.app.themes.AppTheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Abas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${tabs.size} aberta(s)", style = MaterialTheme.typography.bodySmall, color = Neo.Text2)
            }
            if (tabs.isNotEmpty()) TextButton(onClick = onCloseAll) { Text("Fechar todas", color = Neo.Text2) }
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .bounceClick(shape = RoundedCornerShape(20.dp), onClick = onNewTab)
                    .background(Neo.Accent)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = Neo.OnGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nova aba", color = Neo.OnGreen, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                val current = tab.id == currentId
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier
                        .bounceClick(pressed = 0.95f, shape = shape) { onSelect(tab) }
                        .background(Brush.verticalGradient(listOf(Color(0xFF242424), Color(0xFF1A1A1A))))
                        .border(if (current) 2.dp else 1.dp, if (current) Neo.EdgeBright else Neo.Edge, shape)
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SiteBadge(tab.url, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tab.title.ifBlank { BrowserStore.hostOf(tab.url) },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onClose(tab) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, "Fechar aba", tint = Neo.Text2, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(com.musibox.app.themes.AppTheme.line, Color(0xFF1F3A2A)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteBadge(tab.url, 44.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(BrowserStore.hostOf(tab.url), color = Neo.Artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
