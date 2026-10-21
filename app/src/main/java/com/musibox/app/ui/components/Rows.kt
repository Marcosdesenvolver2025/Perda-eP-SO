package com.musibox.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.ui.theme.Mb

data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "Mais opções", tint = tint)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Mb.colors.cardHigh,
        ) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label, color = if (a.destructive) Mb.colors.danger else MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = {
                        Icon(a.icon, null, tint = if (a.destructive) Mb.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = {
                        open = false
                        a.onClick()
                    },
                )
            }
        }
    }
}

/** Linha de música/mídia: capa, título, subtítulo, Play/Pause e menu. */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    artModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    available: Boolean = true,
    onPlayPause: (() -> Unit)? = null,
    menu: List<MenuAction> = emptyList(),
    artSize: Dp = 52.dp,
    badge: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailingText: String? = null,
    divider: Boolean = true,
    /** Modo de seleção: null = normal; true/false = marcada ou não (troca o play/menu por um círculo). */
    selected: Boolean? = null,
) {
  Column(modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected == true) Neo.Green.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (available) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(artModel, Modifier.size(artSize + 4.dp), corner = 12.dp)
            if (badge != null) Box(Modifier.align(Alignment.BottomEnd)) { badge() }
            if (selected == true) {
                Box(
                    Modifier
                        .size(artSize + 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, "Selecionada", tint = Neo.Green, modifier = Modifier.size(30.dp)) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) Neo.Green else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (available) Neo.Artist else Mb.colors.warning,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent && available) {
            Spacer(Modifier.width(8.dp))
            Equalizer(isPlaying)
        } else if (trailingText != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailingText, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB3B3B3), maxLines = 1)
        }
        if (selected != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (selected) Neo.Green else Color.Transparent)
                    .border(2.dp, if (selected) Neo.Green else Color(0xFF6A6A6A), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Neo.OnGreen, modifier = Modifier.size(18.dp)) }
        } else if (onPlayPause != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .bounceClick(enabled = available, pressed = 0.82f, shape = CircleShape, onClick = onPlayPause)
                    .clip(CircleShape)
                    .background(if (isCurrent) Neo.Green else Color(0xFF242424)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isCurrent && isPlaying) "Pausar" else "Tocar",
                    tint = if (isCurrent) Neo.OnGreen else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (selected == null) OverflowMenu(menu)
    }
    // Linha fina separando as músicas (como no novo visual)
    if (divider) {
        HorizontalDivider(
            Modifier.padding(start = artSize + 30.dp, end = 12.dp),
            color = Color(0xFF242424),
            thickness = 1.dp,
        )
    }
  }
}

// ---------------- Configurações ----------------

@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Mb.colors.card)
                .border(1.dp, Mb.colors.border, RoundedCornerShape(20.dp)),
            content = content,
        )
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null && trailing == null,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) trailing()
        if (showChevron) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    SettingItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        tint = tint,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        },
        showChevron = false,
    )
}

@Composable
fun SettingDivider() = HorizontalDivider(Modifier.padding(start = 68.dp), color = Mb.colors.border)

// ---------------- PIN ----------------

@Composable
fun PinDots(length: Int, filled: Int, error: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(length) { i ->
            val target = when {
                error -> Mb.colors.danger
                i < filled -> Mb.colors.vault
                else -> Color.Transparent
            }
            val color by animateColorAsState(target, label = "pinDot")
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (error) Mb.colors.danger else Mb.colors.vault, CircleShape),
            )
        }
    }
}

@Composable
fun NumberPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    extraKey: (@Composable () -> Unit)? = null,
) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { d -> PadKey(Modifier.weight(1f), onClick = { onDigit(d) }) { Text(d.toString(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).aspectRatio(1.5f), contentAlignment = Alignment.Center) { extraKey?.invoke() }
            PadKey(Modifier.weight(1f), onClick = { onDigit('0') }) { Text("0", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
            PadKey(Modifier.weight(1f), onClick = onBackspace, filled = false) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, "Apagar", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun PadKey(modifier: Modifier, onClick: () -> Unit, filled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) Mb.colors.card else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun CenteredText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier.fillMaxWidth(), color = color, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
}
