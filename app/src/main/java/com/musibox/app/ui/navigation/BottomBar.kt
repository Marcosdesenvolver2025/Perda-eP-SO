package com.musibox.app.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.musibox.app.ui.theme.Mb

/**
 * Menu de baixo em "doca" flutuante de vidro, com brilho neon na borda e o botão Baixar
 * como uma esfera 3D iluminada no centro.
 */
@Composable
fun BottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Color(0xFF1E8BFF), spotColor = Color(0xFF1E8BFF))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF18213A), Color(0xFF0B0F1C))))
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(Color(0x551EC8FF), Color.White.copy(alpha = 0.18f), Color(0x559B6BFF))),
                    RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Home, "Início", currentRoute == Routes.HOME) { onNavigate(Routes.HOME) }
            NavItem(Icons.Rounded.MusicNote, "Músicas", currentRoute == Routes.LIBRARY) { onNavigate(Routes.library()) }
            Spacer(Modifier.size(70.dp))
            NavItem(Icons.Rounded.Lock, "Cofre", currentRoute == Routes.VAULT) { onNavigate(Routes.VAULT) }
            NavItem(Icons.Rounded.GridView, "Mais", currentRoute == Routes.MORE) { onNavigate(Routes.MORE) }
        }
        CenterDownloadButton(currentRoute == Routes.DOWNLOAD, Modifier.align(Alignment.BottomCenter).offset(y = (-18).dp)) {
            onNavigate(Routes.download())
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color(0xFF1EC8FF) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                // halo neon atrás do ícone selecionado
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0x661EC8FF), Color.Transparent))),
                )
            }
            Icon(icon, label, tint = color, modifier = Modifier.size(25.dp))
        }
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(width = if (selected) 18.dp else 0.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF1EC8FF), Color(0xFF9B6BFF)))),
        )
    }
}

@Composable
private fun CenterDownloadButton(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(78.dp), contentAlignment = Alignment.Center) {
        // anel de energia
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.sweepGradient(listOf(Color(0xFF1EC8FF), Color(0xFF9B6BFF), Color(0xFFFF4D8D), Color(0xFF1EC8FF))),
                style = Stroke(width = if (selected) 4.dp.toPx() else 2.5.dp.toPx()),
            )
        }
        Box(
            Modifier
                .size(64.dp)
                .shadow(18.dp, CircleShape, ambientColor = Color(0xFF1E8BFF), spotColor = Color(0xFF1E8BFF))
                .clip(CircleShape)
                .background(Mb.colors.accentGradient)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // esfera: luz no alto à esquerda e sombra embaixo
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width * 0.32f, size.height * 0.26f), radius = size.minDimension * 0.5f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), center = Offset(size.width * 0.42f, size.height * 0.36f), radius = size.minDimension * 0.72f),
                )
            }
            Icon(Icons.Rounded.Download, "Baixar", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
