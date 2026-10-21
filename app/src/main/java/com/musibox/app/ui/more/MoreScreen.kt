package com.musibox.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.WorkspacePremium
import com.musibox.app.ui.components.intro
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.musibox.app.BuildConfig
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.SettingDivider
import com.musibox.app.ui.components.SettingItem
import com.musibox.app.ui.components.SettingsSection
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.navigation.navigateTopLevel
import com.musibox.app.ui.theme.Mb

@Composable
fun MoreScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val user by container.auth.user.collectAsStateWithLifecycle()
    val sync by container.sync.state.collectAsStateWithLifecycle()
    var about by remember { mutableStateOf(false) }
    val intro = com.musibox.app.ui.components.rememberIntroState()

    Column(Modifier.fillMaxSize()) {
        com.musibox.app.ui.components.PageTitle("Mais", "Conta, biblioteca e ajustes")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + com.musibox.app.ui.navigation.LocalBottomBarSpace.current)) {
            item(key = "pro") {
                val proState by com.musibox.app.billing.PlayBilling.pro.collectAsStateWithLifecycle()
                ProPromoCard(proState.plan, Modifier.intro("pro", 0, intro)) { nav.navigate(Routes.PRO) }
            }
            item(key = "walls") {
                WallpaperPromo(Modifier.intro("walls", 0, intro)) { com.musibox.app.ui.main.MainTabs.go(com.musibox.app.ui.main.MainTabs.THEMES) }
            }
            item { Box(Modifier.intro("more0", 0, intro)) {
                MbCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { nav.navigate(Routes.ACCOUNT) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Mb.colors.accentGradient),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (user == null) Icons.Rounded.CloudSync else Icons.Rounded.Person, null, tint = Mb.colors.onAccent)
                            user?.photoUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize().clip(CircleShape)) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user?.name ?: "Entrar com Google", style = MaterialTheme.typography.titleMedium)
                            if (user?.email != null) {
                                Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = com.musibox.app.ui.components.Neo.Artist, maxLines = 1)
                            }
                            Text(
                                if (user == null) "Seus dados voltam mesmo se reinstalar o app" else sync.status.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
            item(key = "vault") { Box(Modifier.intro("vault", 0, intro)) {
                MbCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { nav.navigate(Routes.VAULT) { launchSingleTop = true } },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Mb.colors.vault.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                androidx.compose.ui.graphics.vector.ImageVector.vectorResource(com.musibox.app.R.drawable.ic_nav_vault),
                                null,
                                tint = Mb.colors.vault,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Cofre", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Fotos, vídeos, áudios e arquivos protegidos com PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
            item { Box(Modifier.intro("more1", 1, intro)) {
                SettingsSection("Biblioteca") {
                    SettingItem(Icons.Rounded.LibraryMusic, "Biblioteca completa", "Músicas, artistas, álbuns e pastas", onClick = { nav.navigateTopLevel(Routes.library(0)) })
                    SettingDivider()
                    SettingItem(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists", onClick = { nav.navigateTopLevel(Routes.library(4), restore = false) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.Favorite, "Favoritos", onClick = { nav.navigateTopLevel(Routes.library(5), restore = false) }, tint = com.musibox.app.ui.components.Neo.Green)
                    SettingDivider()
                    SettingItem(Icons.AutoMirrored.Rounded.TrendingUp, "Mais tocadas", onClick = { nav.navigateTopLevel(Routes.library(7), restore = false) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.History, "Histórico", "Músicas que você tocou", onClick = { nav.navigate(Routes.history()) })
                }
            } }
            item { Box(Modifier.intro("more2", 2, intro)) {
                SettingsSection("Aplicativo") {
                    SettingItem(androidx.compose.material.icons.Icons.Rounded.Equalizer, "Equalizador", "Graves, estilos e som 3D • Pro", onClick = { nav.navigate(Routes.EQUALIZER) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.Settings, "Configurações", onClick = { nav.navigate(Routes.SETTINGS) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.Storage, "Armazenamento", onClick = { nav.navigate(Routes.STORAGE) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.Info, "Sobre o MusiBox", "Versão ${BuildConfig.VERSION_NAME}", onClick = { about = true })
                }
            } }
        }
    }

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("MusiBox ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "Player de músicas locais, navegador e cofre privado.\n\n" +
                        "• Suas músicas tocam direto do aparelho, sem internet.\n" +
                        "• O cofre é criptografado (AES-256) com chave protegida pelo Android Keystore.\n" +
                        "• A Conta Google sincroniza apenas playlists, favoritos, configurações e histórico (se ativado).\n" +
                        "• Os papéis de parede são criações originais do MusiBox.",
                )
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("Fechar") } },
            containerColor = Mb.colors.cardHigh,
        )
    }
}

/** Cartão "Papéis de parede": três miniaturas e o convite para a loja. */
@Composable
private fun WallpaperPromo(modifier: Modifier, onClick: () -> Unit) {
    val picks = remember { listOf("pm_colmeia_dourada", "pm_orbe_ambar", "pm_sol_de_fogo").mapNotNull { com.musibox.app.themes.Wallpapers.get(it) } }
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Mb.colors.card)
            .border(1.dp, Mb.colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
            picks.forEachIndexed { i, w ->
                com.musibox.app.ui.components.WallpaperImage(
                    w,
                    Modifier
                        .size(width = 38.dp, height = 70.dp)
                        .graphicsLayer { rotationZ = (i - 1) * 8f }
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.5.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), RoundedCornerShape(9.dp)),
                    thumbnail = true,
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("Papéis de parede", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(
                "${com.musibox.app.themes.Wallpapers.all.size} temas • nova coleção Premium 3D • a partir de R$ 0,50",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** Cartão do Pro na aba Mais: chama atenção com os três planos e prévias dos temas exclusivos. */
@Composable
private fun ProPromoCard(plan: com.musibox.app.billing.Plan, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val active = plan != com.musibox.app.billing.Plan.NONE
    val previews = remember {
        com.musibox.app.themes.Wallpapers.all.filter { it.pro && it.collection == com.musibox.app.themes.Wallpapers.PREMIUM }.take(4)
    }
    Column(
        modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = androidx.compose.ui.graphics.Color(0xFFFFB02A), spotColor = androidx.compose.ui.graphics.Color(0xFFFFB02A))
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xFF2A1A05), androidx.compose.ui.graphics.Color(0xFF3A1A4A), androidx.compose.ui.graphics.Color(0xFF0A1A3A)),
                ),
            )
            .border(1.dp, androidx.compose.ui.graphics.Color(0x66FFC857), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFFD86A), androidx.compose.ui.graphics.Color(0xFFFF9A2A)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.WorkspacePremium, null, tint = androidx.compose.ui.graphics.Color(0xFF2A1A05), modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (active) "Seu plano: ${plan.label} ✓" else "MusiBox Pro",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                )
                Text(
                    if (active) "Temas exclusivos e recursos extras liberados" else "Temas que não se compram + recursos extras",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            previews.forEach { w ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.62f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                ) {
                    com.musibox.app.ui.components.WallpaperImage(w, Modifier.fillMaxSize(), thumbnail = true)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Go", "3 dias", androidx.compose.ui.graphics.Color(0xFF2AD0FF)),
                Triple("Plus", "semanal", androidx.compose.ui.graphics.Color(0xFFB07AFF)),
                Triple("Pro", "mensal", androidx.compose.ui.graphics.Color(0xFFFFC857)),
            ).forEach { (name, period, c) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.copy(alpha = if (plan.label == name) 0.35f else 0.14f))
                        .border(1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(name, color = c, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(period, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            }
        }
        if (!active) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFFD86A), androidx.compose.ui.graphics.Color(0xFFFF9A2A))))
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Ver planos • a partir de R$ 4,99", color = androidx.compose.ui.graphics.Color(0xFF2A1A05), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}
