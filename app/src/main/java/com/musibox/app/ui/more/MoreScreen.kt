package com.musibox.app.ui.more

import androidx.compose.foundation.background
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

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Mais")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
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
                            Icon(if (user == null) Icons.Rounded.CloudSync else Icons.Rounded.Person, null, tint = androidx.compose.ui.graphics.Color.White)
                            user?.photoUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize().clip(CircleShape)) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user?.name ?: "Entrar com Google", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (user == null) "Sincronize playlists, favoritos e configurações" else sync.status.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SettingsSection("Biblioteca") {
                    SettingItem(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists", onClick = { nav.navigateTopLevel(Routes.library(4), restore = false) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.Favorite, "Favoritos", onClick = { nav.navigateTopLevel(Routes.library(5), restore = false) }, tint = androidx.compose.ui.graphics.Color(0xFFFF4D8D))
                    SettingDivider()
                    SettingItem(Icons.AutoMirrored.Rounded.TrendingUp, "Mais tocadas", onClick = { nav.navigateTopLevel(Routes.library(7), restore = false) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.History, "Histórico", "Reproduções, downloads e importações", onClick = { nav.navigate(Routes.history()) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.DownloadDone, "Gerenciador de downloads", onClick = { nav.navigateTopLevel(Routes.download(2), restore = false) })
                }
            }
            item {
                SettingsSection("Aplicativo") {
                    SettingItem(Icons.Rounded.Settings, "Configurações", onClick = { nav.navigate(Routes.SETTINGS) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.Storage, "Armazenamento", onClick = { nav.navigate(Routes.STORAGE) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.Info, "Sobre o MusiBox", "Versão ${BuildConfig.VERSION_NAME}", onClick = { about = true })
                }
            }
        }
    }

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("MusiBox ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "Player de músicas locais, downloads e cofre privado.\n\n" +
                        "• Suas músicas tocam direto do aparelho, sem internet.\n" +
                        "• O cofre é criptografado (AES-256) com chave protegida pelo Android Keystore.\n" +
                        "• A Conta Google sincroniza apenas playlists, favoritos, configurações e histórico (se ativado).\n\n" +
                        "Baixe somente conteúdo que você tem direito de salvar. Conteúdo protegido por DRM não é baixado.",
                )
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("Fechar") } },
            containerColor = Mb.colors.cardHigh,
        )
    }
}
