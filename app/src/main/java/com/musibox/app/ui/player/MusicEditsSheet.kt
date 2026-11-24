package com.musibox.app.ui.player

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.media.EditCategory
import com.musibox.app.media.MusicEdit
import com.musibox.app.media.MusicEdits
import com.musibox.app.themes.AppTheme

/** Music Edits: escolher um efeito para a música que está tocando (Go, Plus e Pro). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicEditsSheet(
    unlocked: Boolean,
    onLocked: () -> Unit,
    onSaveToPlaylist: (MusicEdit) -> Unit,
    onPicked: (MusicEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val current by MusicEdits.current.collectAsStateWithLifecycle()
    val accent = AppTheme.themeAccent
    val onAccent = if (accent.luminance() > 0.3f) Color(0xFF0A0A0A) else Color.White

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),
        ) {
            Text("✨ Music Edits", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Escolha um efeito: a música muda na hora. Toque em Salvar para guardar ela assim numa playlist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!unlocked) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .clickable(onClick = onLocked)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Disponível nos planos Go, Plus e Pro. Toque para ver os planos.", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            EditCard(MusicEdit.NONE, current == MusicEdit.NONE, unlocked = true, accent = accent, modifier = Modifier.fillMaxWidth()) {
                onPicked(MusicEdit.NONE)
            }
            EditCategory.entries.forEach { cat ->
                Spacer(Modifier.height(16.dp))
                Text(cat.label, fontWeight = FontWeight.Bold, color = accent, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                MusicEdit.entries.filter { it.category == cat }.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { e ->
                            EditCard(e, current == e, unlocked, accent, Modifier.weight(1f)) {
                                if (unlocked) onPicked(e) else onLocked()
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (unlocked && current != MusicEdit.NONE) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { onSaveToPlaylist(current) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvar na playlist com ${current.label}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EditCard(
    edit: MusicEdit,
    selected: Boolean,
    unlocked: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.18f) else AppTheme.cardHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else AppTheme.line, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(edit.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    edit.label,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    selected -> Icon(Icons.Rounded.CheckCircle, null, tint = accent, modifier = Modifier.size(18.dp))
                    !unlocked -> Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                edit.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
