package com.musibox.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (available) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(artModel, Modifier.size(artSize), corner = 12.dp)
            if (badge != null) Box(Modifier.align(Alignment.BottomEnd)) { badge() }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (available) MaterialTheme.colorScheme.onSurfaceVariant else Mb.colors.warning,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onPlayPause != null) {
            Spacer(Modifier.width(8.dp))
            MbIconButton(
                if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isCurrent && isPlaying) "Pausar" else "Tocar",
                onPlayPause,
                size = 42.dp,
                background = MaterialTheme.colorScheme.surfaceContainerHighest,
                enabled = available,
            )
        }
        OverflowMenu(menu)
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
