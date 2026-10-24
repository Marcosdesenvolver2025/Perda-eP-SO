package com.musibox.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.R
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.pressScale
import com.musibox.app.ui.main.MainTabs
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Altura da cápsula e o espaço até a barra de gestos. */
object FloatingBar {
    val Height = 64.dp
    val BottomGap = 10.dp
}

/**
 * Espaço que a cápsula (e o mini player, quando aparece) ocupa no rodapé das telas principais.
 * As listas usam isso no fim para o último item não ficar escondido atrás da barra.
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/**
 * Barra de navegação flutuante em formato de cápsula: fundo escuro de vidro fosco,
 * borda fina, e a aba escolhida com um destaque verde que desliza até ela.
 */
@Composable
fun FloatingNavBar(selectedPage: Int, hazeState: HazeState?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    // vidro da barra tingido com a cor do tema
    val barBg = com.musibox.app.themes.AppTheme.bg
    val barTint = com.musibox.app.themes.AppTheme.surface
    BoxWithConstraints(
        modifier
            .fillMaxWidth(0.84f)
            .widthIn(max = 440.dp)
            .height(FloatingBar.Height)
            .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(hazeState) {
                        backgroundColor = barBg
                        tints = listOf(HazeTint(barTint.copy(alpha = 0.86f)))
                        blurRadius = 22.dp
                        noiseFactor = 0f
                    }
                } else {
                    Modifier.background(barTint.copy(alpha = 0.95f))
                },
            )
            .border(1.dp, Color.White.copy(alpha = 0.11f), shape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        val slot: Dp = maxWidth / MainTabs.COUNT
        val x by animateDpAsState(slot * selectedPage, spring(dampingRatio = 0.75f, stiffness = 420f), label = "navPill")
        // Destaque verde atrás da aba escolhida
        Box(
            Modifier
                .offset(x = x + 2.dp)
                .width(slot - 4.dp)
                .fillMaxHeight()
                .clip(shape)
                .background(Neo.Green.copy(alpha = 0.17f)),
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            NavItem(painterResource(R.drawable.ic_brand_note), "Música", selectedPage == MainTabs.HOME, Modifier.weight(1f)) { onSelect(MainTabs.HOME) }
            NavItem(rememberVectorPainter(Icons.Outlined.Language), "Navegador", selectedPage == MainTabs.BROWSER, Modifier.weight(1f)) { onSelect(MainTabs.BROWSER) }
            NavItem(rememberVectorPainter(Icons.Outlined.Palette), "Temas", selectedPage == MainTabs.THEMES, Modifier.weight(1f)) { onSelect(MainTabs.THEMES) }
            NavItem(painterResource(R.drawable.ic_nav_more), "Mais", selectedPage == MainTabs.MORE, Modifier.weight(1f)) { onSelect(MainTabs.MORE) }
        }
    }
}

@Composable
private fun NavItem(icon: Painter, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Neo.Green else Color.White, label = "navTint")
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "navScale")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .pressScale(interaction, 0.88f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            icon,
            null,
            tint = tint,
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Text(
            label,
            color = tint,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * Desfoque suave no rodapé (só na página Música): o conteúdo que passa por trás da cápsula
 * fica levemente embaçado, mais forte embaixo e sumindo para cima.
 */
@Composable
fun BottomFadeBlur(hazeState: HazeState?, height: Dp, alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { this.alpha = alpha() }
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(hazeState) {
                        backgroundColor = Color.Black
                        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.22f)))
                        blurRadius = 9.dp
                        noiseFactor = 0f
                        progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
                    }
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                },
            ),
    )
}
