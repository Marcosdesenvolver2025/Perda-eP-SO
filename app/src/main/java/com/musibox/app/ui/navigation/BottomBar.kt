package com.musibox.app.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.R
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.pressScale
import com.musibox.app.ui.main.MainTabs

/**
 * Barra de baixo no estilo do novo visual: sem fundo próprio, ícones de contorno e a aba
 * escolhida em verde com um tracinho embaixo. Acompanha a página do deslize.
 */
@Composable
fun BottomBar(selectedPage: Int, onSelect: (Int) -> Unit) {
    // Sem faixa cinza: os ícones ficam direto sobre o fundo do app
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(rememberVectorPainter(Icons.Outlined.Home), "Início", selectedPage == MainTabs.HOME, Modifier.weight(1f)) { onSelect(MainTabs.HOME) }
            NavItem(rememberVectorPainter(Icons.Outlined.Language), "Navegador", selectedPage == MainTabs.BROWSER, Modifier.weight(1f)) { onSelect(MainTabs.BROWSER) }
            NavItem(painterResource(R.drawable.ic_nav_vault), "Cofre", selectedPage == MainTabs.VAULT, Modifier.weight(1f)) { onSelect(MainTabs.VAULT) }
            NavItem(rememberVectorPainter(Icons.Outlined.GridView), "Mais", selectedPage == MainTabs.MORE, Modifier.weight(1f)) { onSelect(MainTabs.MORE) }
        }
    }
}

@Composable
private fun NavItem(
    icon: Painter,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = 0.4f, stiffness = 450f), label = "navScale")
    val lineWidth by animateDpAsState(if (selected) 26.dp else 0.dp, spring(dampingRatio = 0.6f), label = "navLine")
    val interaction = remember { MutableInteractionSource() }
    // Enquanto baixa, o ícone de download "pulsa" de leve
    val bob = if (busy) {
        val t = rememberInfiniteTransition(label = "busy")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "bob").value
    } else {
        0f
    }
    Column(
        modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .pressScale(interaction, 0.88f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                icon,
                null,
                tint = if (selected) Color.White else Color(0xFFB3B3B3),
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationY = if (busy) (kotlin.math.sin(bob * 6.2832f) * 2.dp.toPx()) else 0f
                    },
            )
            if (busy) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Neo.Green),
                )
            }
        }
        Text(
            label,
            color = if (selected) Neo.Green else Color(0xFFB3B3B3),
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(width = lineWidth, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Neo.Green),
        )
    }
}
