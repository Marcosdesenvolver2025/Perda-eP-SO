package com.musibox.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.R

/** Paleta do visual "neon" (fundo azul-marinho, vidro, bordas que brilham). */
object Neo {
    val Cyan = Color(0xFF19E6F5)
    val Blue = Color(0xFF1E8BFF)
    val BlueDeep = Color(0xFF1B4DFF)
    val Purple = Color(0xFF8B5CFF)
    val Link = Color(0xFF22B8F0)
    val Artist = Color(0xFF7FB2E6)
    val Text2 = Color(0xFFB9C6DA)
    val GlassTop = Color(0xFF0E1C33)
    val GlassBottom = Color(0xFF070F1E)
    val Glass = Brush.verticalGradient(listOf(GlassTop, GlassBottom))
    val Edge = Brush.linearGradient(listOf(Color(0xFF2D4C78), Color(0xFF15253F), Color(0xFF2B3F70)))
    val EdgeBright = Brush.linearGradient(listOf(Color(0xFF1EC8FF), Color(0xFF4A6BFF), Color(0xFF9B5CFF)))
    val Accent = Brush.linearGradient(listOf(Color(0xFF2F7BFF), Color(0xFF1B3DFF)))
}

/** Fundo de vidro com borda suave. */
fun Modifier.neoGlass(shape: Shape, bright: Boolean = false): Modifier = this
    .clip(shape)
    .background(Neo.Glass)
    .border(1.dp, if (bright) Neo.EdgeBright else Neo.Edge, shape)

@Composable
fun GlassCircleButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 46.dp, dot: Boolean = false) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .neoGlass(CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.48f))
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 3.dp)
                    .size(10.dp)
                    .shadow(6.dp, CircleShape, ambientColor = Neo.Blue, spotColor = Neo.Blue)
                    .clip(CircleShape)
                    .background(Neo.Blue),
            )
        }
    }
}

/** Topo das telas principais: logo 3D, nome e os botões de busca, avisos e configurações. */
@Composable
fun BrandHeader(onSearch: () -> Unit, onBell: () -> Unit, bellDot: Boolean, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.logo_note3d), null, Modifier.size(50.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Musi") }
                    withStyle(SpanStyle(color = Color(0xFF1E9BFF))) { append("Box") }
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                letterSpacing = (-0.5).sp,
            )
            Text(
                "Sua música, do seu jeito.",
                fontSize = 12.5.sp,
                color = Neo.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCircleButton(Icons.Rounded.Search, "Pesquisar", onSearch)
            GlassCircleButton(Icons.Rounded.NotificationsNone, "Avisos e downloads", onBell, dot = bellDot)
            GlassCircleButton(Icons.Rounded.Settings, "Configurações", onSettings)
        }
    }
}

/** Campo de busca "de mentira" que abre a tela de busca. */
@Composable
fun NeoSearchPill(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .neoGlass(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Neo.Text2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(placeholder, color = Neo.Text2, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun NeoChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .height(42.dp)
            .then(
                if (selected) Modifier
                    .shadow(12.dp, shape, ambientColor = Neo.Blue, spotColor = Neo.Blue)
                    .clip(shape)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2F7BFF), Color(0xFF1840F0))))
                    .border(1.dp, Color(0xFF6FA8FF), shape)
                else Modifier.neoGlass(shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else Color(0xFF8FC3FF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun NeoSectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
        if (action != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, color = Neo.Link, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Neo.Link, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Título grande das páginas (Navegador, Baixar, Cofre, Mais) com ações à direita. */
@Composable
fun PageTitle(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Neo.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}
