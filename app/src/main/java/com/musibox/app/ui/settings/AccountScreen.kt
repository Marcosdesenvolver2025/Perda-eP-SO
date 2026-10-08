package com.musibox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.musibox.app.AppContainer
import com.musibox.app.core.Fmt
import com.musibox.app.core.findActivity
import com.musibox.app.sync.RestoreMode
import com.musibox.app.sync.SignInResolution
import com.musibox.app.sync.SignInResult
import com.musibox.app.sync.SyncStatus
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.SettingDivider
import com.musibox.app.ui.components.SettingItem
import com.musibox.app.ui.components.SettingsSection
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.theme.Mb
import kotlinx.coroutines.launch

/** Inicia o login com Google e decide o que fazer com os dados. */
suspend fun startGoogleSignIn(container: AppContainer, activity: android.app.Activity?, onMessage: (String) -> Unit): Boolean {
    if (activity == null) return false
    return when (val r = container.auth.signIn(activity)) {
        is SignInResult.Success -> {
            container.sync.onSignedIn(r.user)
            onMessage("Conectado como ${r.user.email ?: r.user.name ?: "sua conta"}.")
            true
        }
        SignInResult.Canceled -> {
            onMessage("Login cancelado. Você pode entrar depois em Configurações.")
            false
        }
        is SignInResult.Error -> {
            onMessage(r.message)
            false
        }
    }
}

@Composable
fun AccountScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val user by container.auth.user.collectAsStateWithLifecycle()
    val sync by container.sync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var busy by remember { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var restoreDialog by remember { mutableStateOf(false) }
    var appSize by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { appSize = runCatching { container.storage.load().appTotal }.getOrNull() }

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Conta e sincronização", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            val u = user
            if (!container.auth.isConfigured) {
                MbCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "O login com Google não está configurado nesta versão do aplicativo.",
                        Modifier.padding(16.dp),
                    )
                }
            } else if (u == null) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Mb.colors.accentGradient),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.CloudSync, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
                    Spacer(Modifier.height(18.dp))
                    Text("Proteja e sincronize seus dados", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Entre com sua Conta Google para manter configurações, playlists, favoritos e outros dados compatíveis " +
                            "sincronizados. Assim você poderá recuperá-los ao trocar de celular ou reinstalar o aplicativo.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Suas músicas, fotos e vídeos continuam no aparelho. Nada do cofre é enviado.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    GradientButton(
                        "Continuar com Google",
                        onClick = {
                            busy = true
                            scope.launch {
                                startGoogleSignIn(container, context.findActivity()) { snack(it) }
                                busy = false
                            }
                        },
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Person, null, modifier = Modifier.size(34.dp))
                        if (u.photoUrl != null) {
                            AsyncImage(u.photoUrl, contentDescription = "Foto da conta", modifier = Modifier.fillMaxSize().clip(CircleShape))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(u.name ?: "Conta Google", style = MaterialTheme.typography.titleLarge)
                        Text(u.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SyncStatusCard(sync.status, sync.lastSyncAt, sync.error, sync.pending)
                SettingsSection("Dados") {
                    SettingItem(Icons.Rounded.Schedule, "Último backup/sincronização", if (sync.lastSyncAt > 0) Fmt.dateTime(sync.lastSyncAt) else "Ainda não sincronizado")
                    SettingDivider()
                    SettingItem(Icons.Rounded.Storage, "Espaço usado pelo app neste aparelho", appSize?.let { Fmt.size(it) } ?: "Calculando…")
                    SettingDivider()
                    SettingItem(Icons.Rounded.CloudUpload, "O que é sincronizado", "Playlists, favoritos, configurações, última música e (se ativado) histórico. Arquivos de música e do cofre não são enviados.")
                }
                SettingsSection("Ações") {
                    SettingItem(Icons.Rounded.Sync, "Sincronizar agora", null, onClick = {
                        if (sync.status == SyncStatus.NEEDS_SETUP) {
                            scope.launch { container.sync.checkPendingSetup() }
                        } else {
                            scope.launch {
                                busy = true
                                val ok = container.sync.performSync()
                                busy = false
                                snack(if (ok) "Sincronizado." else container.sync.state.value.error ?: "Sincronização pendente. Tentaremos de novo quando houver internet.")
                            }
                        }
                    }, trailing = if (busy) ({ CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }) else null)
                    SettingDivider()
                    SettingItem(Icons.Rounded.CloudDownload, "Restaurar dados", "Traz playlists, favoritos e configurações da nuvem (combina com os deste aparelho)", onClick = { restoreDialog = true })
                    SettingDivider()
                    SettingItem(Icons.AutoMirrored.Rounded.Logout, "Sair da conta", null, onClick = { signOutDialog = true })
                    SettingDivider()
                    SettingItem(
                        Icons.Rounded.DeleteForever, "Excluir dados da nuvem", "Apaga o que está salvo na sua conta. Os arquivos do aparelho não são tocados.",
                        onClick = { deleteDialog = true }, tint = Mb.colors.danger, titleColor = Mb.colors.danger,
                    )
                }
            }
        }
    }

    if (restoreDialog) {
        ConfirmDialog(
            title = "Restaurar dados",
            message = "Playlists, favoritos, histórico sincronizado e configurações da sua conta serão trazidos para este aparelho e combinados com os atuais. Nada será apagado.",
            confirmText = "Restaurar",
            onConfirm = {
                restoreDialog = false
                scope.launch {
                    busy = true
                    val r = container.sync.restoreNow()
                    busy = false
                    snack(if (r.isSuccess) "Dados restaurados." else r.exceptionOrNull()?.message ?: "Não foi possível restaurar.")
                }
            },
            onDismiss = { restoreDialog = false },
        )
    }

    if (signOutDialog) {
        AlertDialog(
            onDismissRequest = { signOutDialog = false },
            title = { Text("Sair da conta") },
            text = {
                Text(
                    "Deseja manter os dados deste aparelho?\n\nSair não apaga os dados da nuvem. Suas músicas e o cofre nunca são apagados. " +
                        "Se escolher apagar, só playlists, favoritos e histórico deste aparelho serão removidos.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    signOutDialog = false
                    scope.launch { container.sync.signOut(keepLocalData = true); snack("Você saiu da conta. Os dados foram mantidos.") }
                }) { Text("Manter dados") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { signOutDialog = false }) { Text("Cancelar") }
                    TextButton(onClick = {
                        signOutDialog = false
                        scope.launch { container.sync.signOut(keepLocalData = false); snack("Você saiu da conta.") }
                    }) { Text("Apagar deste aparelho", color = Mb.colors.danger) }
                }
            },
            containerColor = Mb.colors.cardHigh,
        )
    }

    if (deleteDialog) {
        ConfirmDialog(
            title = "Excluir dados da nuvem?",
            message = "Serão apagados da sua conta: playlists, favoritos, histórico sincronizado, configurações e a última música. " +
                "Os dados deste aparelho, suas músicas e o cofre NÃO serão apagados. Depois disso você sairá da conta.\n\n" +
                "Para confirmar, entre novamente com a sua Conta Google.",
            confirmText = "Confirmar e excluir",
            destructive = true,
            onConfirm = {
                deleteDialog = false
                scope.launch {
                    val act = context.findActivity() ?: return@launch
                    when (val auth = container.auth.reauthenticate(act)) {
                        is SignInResult.Success -> {
                            busy = true
                            val r = container.sync.deleteCloudData()
                            busy = false
                            snack(if (r.isSuccess) "Dados da nuvem excluídos. Você saiu da conta." else r.exceptionOrNull()?.message ?: "Não foi possível excluir.")
                        }
                        SignInResult.Canceled -> snack("Exclusão cancelada.")
                        is SignInResult.Error -> snack(auth.message)
                    }
                }
            },
            onDismiss = { deleteDialog = false },
        )
    }
}

@Composable
private fun SyncStatusCard(status: SyncStatus, lastSyncAt: Long, error: String?, pending: Int) {
    val (icon, color) = when (status) {
        SyncStatus.SYNCED -> Icons.Rounded.CloudDone to Mb.colors.success
        SyncStatus.SYNCING -> Icons.Rounded.CloudSync to MaterialTheme.colorScheme.primary
        SyncStatus.OFFLINE -> Icons.Rounded.CloudOff to Mb.colors.warning
        SyncStatus.PENDING -> Icons.Rounded.SyncProblem to Mb.colors.warning
        SyncStatus.ERROR -> Icons.Rounded.ErrorOutline to Mb.colors.danger
        SyncStatus.NEEDS_SETUP -> Icons.Rounded.SyncProblem to Mb.colors.warning
        SyncStatus.SIGNED_OUT -> Icons.Rounded.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
    }
    MbCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        background = color.copy(alpha = 0.10f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(status.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                val detail = when {
                    error != null && status == SyncStatus.ERROR -> error
                    pending > 0 -> "${Fmt.plural(pending, "alteração aguardando", "alterações aguardando")} envio"
                    lastSyncAt > 0 -> "Última sincronização: ${Fmt.dateTime(lastSyncAt)}"
                    else -> null
                }
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Diálogos globais de restauração/conflito depois do login. */
@Composable
fun SyncResolutionHost() {
    val container = LocalAppContainer.current
    val resolution by container.sync.resolution.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }

    fun resolve(mode: RestoreMode) {
        working = true
        scope.launch {
            runCatching { container.sync.resolve(mode) }
            working = false
        }
    }

    when (val r = resolution) {
        null -> Unit
        is SignInResolution.OfferRestore -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.CloudDownload, null) },
            title = { Text("Encontramos dados sincronizados da sua conta.") },
            text = { Text("Deseja restaurar playlists, favoritos, configurações e o histórico salvos na nuvem?") },
            confirmButton = { TextButton(onClick = { resolve(RestoreMode.CLOUD) }, enabled = !working) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { resolve(RestoreMode.LATER) }, enabled = !working) { Text("Agora não") } },
            containerColor = Mb.colors.cardHigh,
        )
        is SignInResolution.Conflict -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Existem dados neste dispositivo e dados sincronizados na sua conta.") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceRow(Icons.Rounded.CloudSync, "Combinar dados", "Junta playlists e favoritos dos dois lados, sem perder nada. (Recomendado)") { resolve(RestoreMode.MERGE) }
                    ChoiceRow(Icons.Rounded.CloudUpload, "Usar dados deste aparelho", "A nuvem passa a ter o que está neste celular.") { resolve(RestoreMode.LOCAL) }
                    ChoiceRow(Icons.Rounded.CloudDownload, "Restaurar dados da nuvem", "Este celular passa a ter o que está na sua conta.") { resolve(RestoreMode.CLOUD) }
                    if (working) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
            },
            confirmButton = {},
            containerColor = Mb.colors.cardHigh,
        )
        is SignInResolution.OtherAccount -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Dados de outra conta") },
            text = {
                Text(
                    "As playlists, favoritos e histórico deste aparelho pertencem a outra Conta Google. Para não misturar dados de " +
                        "contas diferentes, eles serão substituídos pelos da conta atual. Suas músicas e o cofre não são afetados.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    working = true
                    scope.launch { runCatching { container.sync.replaceOtherAccountData() }; working = false }
                }, enabled = !working) { Text("Substituir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { container.sync.signOut(keepLocalData = true) }
                }) { Text("Cancelar e sair") }
            },
            containerColor = Mb.colors.cardHigh,
        )
        is SignInResolution.CheckFailed -> AlertDialog(
            onDismissRequest = { container.sync.dismissResolution() },
            title = { Text("Não foi possível verificar sua conta") },
            text = { Text("${r.message}\n\nVerifique a internet. Você pode tentar de novo em Configurações > Conta e sincronização.") },
            confirmButton = {
                TextButton(onClick = {
                    container.sync.dismissResolution()
                    scope.launch { container.sync.checkPendingSetup() }
                }) { Text("Tentar de novo") }
            },
            dismissButton = { TextButton(onClick = { container.sync.dismissResolution() }) { Text("Depois") } },
            containerColor = Mb.colors.cardHigh,
        )
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    MbCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
