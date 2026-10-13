package com.musibox.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.R
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.main.MainTabs

/**
 * Barra de baixo em vidro com ícones 3D (Início, Navegador, Cofre, Mais) e o botão Baixar
 * como uma esfera iluminada no centro. Acompanha a página do deslize.
 */
@Composable
fun BottomBar(selectedPage: Int, onSelect: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 6.dp, top = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(36.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .shadow(20.dp, shape, ambientColor = Color(0xFF0F6BFF), spotColor = Color(0xFF0F6BFF))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1A30), Color(0xFF050B17))))
                .border(
                    1.2.dp,
                    Brush.horizontalGradient(listOf(Color(0xFF1EC8FF), Color(0xFF1B3D78), Color(0xFF233A70), Color(0xFF8B5CFF))),
                    shape,
                )
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem3D(R.drawable.ic3d_home, "Início", selectedPage == MainTabs.HOME, big = true) { onSelect(MainTabs.HOME) }
            NavItem3D(R.drawable.ic3d_browser, "Navegador", selectedPage == MainTabs.BROWSER) { onSelect(MainTabs.BROWSER) }
            Spacer(Modifier.width(84.dp))
            NavItem3D(R.drawable.ic3d_vault, "Cofre", selectedPage == MainTabs.VAULT) { onSelect(MainTabs.VAULT) }
            NavItem3D(R.drawable.ic3d_more, "Mais", selectedPage == MainTabs.MORE) { onSelect(MainTabs.MORE) }
        }
        DownloadOrb(selectedPage == MainTabs.DOWNLOAD, Modifier.align(Alignment.BottomCenter).offset(y = (-6).dp)) {
            onSelect(MainTabs.DOWNLOAD)
        }
    }
}

@Composable
private fun NavItem3D(icon: Int, label: String, selected: Boolean, big: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, label = "navScale")
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0x5519E6F5), Color.Transparent))),
                )
            }
            Image(
                painterResource(icon),
                null,
                Modifier
                    .size(if (big) 42.dp else 36.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Text(
            label,
            color = if (selected) Neo.Cyan else Color(0xFFDDE6F3),
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(width = if (selected) 22.dp else 0.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Neo.Cyan),
        )
    }
}

/** Esfera 3D do botão Baixar: anel de energia, aro escuro, esfera azul com reflexo e o ícone. */
@Composable
private fun DownloadOrb(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(88.dp)
            .semantics {
                contentDescription = "Baixar"
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            // brilho externo
            drawCircle(Brush.radialGradient(listOf(Color(0x552F7BFF), Color.Transparent), radius = r), radius = r)
            // anel de energia
            drawCircle(
                Brush.sweepGradient(listOf(Color(0xFF1EC8FF), Color(0xFF2F5BFF), Color(0xFF9B5CFF), Color(0xFF2F7BFF), Color(0xFF1EC8FF))),
                radius = r * 0.9f,
                style = Stroke(width = (if (selected) 5.dp else 4.dp).toPx()),
            )
            // aro escuro
            drawCircle(Color(0xFF040812), radius = r * 0.84f)
        }
        Box(
            Modifier
                .size(66.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0xFF1E6BFF), spotColor = Color(0xFF1E6BFF))
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // esfera
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF4A98FF), Color(0xFF1C45F0), Color(0xFF0A1C9E)),
                        center = Offset(w * 0.42f, h * 0.36f),
                        radius = w * 0.78f,
                    ),
                )
                // reflexo de vidro no alto
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(w * 0.36f, h * 0.24f), radius = w * 0.42f),
                )
                // ícone: seta + bandeja
                val c = Color(0xFFD7F6FF)
                val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val arrow = Path().apply {
                    moveTo(w * 0.5f, h * 0.24f)
                    lineTo(w * 0.5f, h * 0.58f)
                    moveTo(w * 0.36f, h * 0.45f)
                    lineTo(w * 0.5f, h * 0.59f)
                    lineTo(w * 0.64f, h * 0.45f)
                }
                drawPath(arrow, c, style = stroke)
                val tray = Path().apply {
                    moveTo(w * 0.28f, h * 0.58f)
                    lineTo(w * 0.28f, h * 0.73f)
                    lineTo(w * 0.72f, h * 0.73f)
                    lineTo(w * 0.72f, h * 0.58f)
                }
                drawPath(tray, c, style = stroke)
            }
        }
    }
}
