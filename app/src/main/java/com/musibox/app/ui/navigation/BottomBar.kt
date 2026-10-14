package com.musibox.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.drawscope.rotate
import com.musibox.app.ui.components.pressScale
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
fun BottomBar(selectedPage: Int, downloading: Boolean = false, onSelect: (Int) -> Unit) {
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
                .shadow(20.dp, shape, ambientColor = Color(0xFF6A3DFF), spotColor = Color(0xFF6A3DFF))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF17174A), Color(0xFF08081E))))
                .border(
                    1.4.dp,
                    Brush.horizontalGradient(listOf(Color(0xFF19C6FF), Color(0xFF3A4BFF), Color(0xFF8B3DFF), Color(0xFFE03DFF))),
                    shape,
                )
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem3D(R.drawable.ic3d_home, "Início", selectedPage == MainTabs.HOME, Color(0xFFFF4FA3), big = true) { onSelect(MainTabs.HOME) }
            NavItem3D(R.drawable.ic3d_browser, "Navegador", selectedPage == MainTabs.BROWSER, Color(0xFF2BE38B)) { onSelect(MainTabs.BROWSER) }
            Spacer(Modifier.width(84.dp))
            NavItem3D(R.drawable.ic3d_vault, "Cofre", selectedPage == MainTabs.VAULT, Color(0xFFFFB238)) { onSelect(MainTabs.VAULT) }
            NavItem3D(R.drawable.ic3d_more, "Mais", selectedPage == MainTabs.MORE, Color(0xFFB76BFF)) { onSelect(MainTabs.MORE) }
        }
        DownloadOrb(selectedPage == MainTabs.DOWNLOAD, downloading, Modifier.align(Alignment.BottomCenter).offset(y = (-6).dp)) {
            onSelect(MainTabs.DOWNLOAD)
        }
    }
}

@Composable
private fun NavItem3D(icon: Int, label: String, selected: Boolean, accent: Color, big: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (selected) 1.14f else 1f,
        spring(dampingRatio = 0.38f, stiffness = 420f),
        label = "navScale",
    )
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "navLift")
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .pressScale(interaction, 0.88f)
            .clickable(
                interactionSource = interaction,
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
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent))),
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
                        translationY = -lift * 3.dp.toPx()
                    },
            )
        }
        Text(
            label,
            color = if (selected) accent else Color(0xFFDDE2F5),
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(width = if (selected) 22.dp else 0.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
    }
}

/** Esfera 3D do botão Baixar: anel de energia, aro escuro, esfera azul com reflexo e o ícone. */
@Composable
private fun DownloadOrb(selected: Boolean, downloading: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Anel gira e a esfera "respira" só enquanto há downloads em andamento.
    val spin = if (downloading) {
        val t = rememberInfiniteTransition(label = "orb")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "orbSpin").value
    } else {
        0f
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(88.dp)
            .pressScale(interaction, 0.88f)
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
            drawCircle(Brush.radialGradient(listOf(Color(0x668B3DFF), Color.Transparent), radius = r), radius = r)
            // anel de energia (gira durante downloads)
            rotate(spin) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color(0xFF19C6FF), Color(0xFF3A4BFF), Color(0xFF8B3DFF), Color(0xFFE03DFF), Color(0xFF19C6FF))),
                    radius = r * 0.9f,
                    style = Stroke(width = (if (selected || downloading) 5.dp else 4.dp).toPx()),
                )
            }
            // aro escuro
            drawCircle(Color(0xFF040812), radius = r * 0.84f)
        }
        Box(
            Modifier
                .size(66.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0xFF8B3DFF), spotColor = Color(0xFF8B3DFF))
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // esfera
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF5C8DFF), Color(0xFF4433F0), Color(0xFF7A1FC8)),
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
