package com.musibox.app.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.core.findActivity
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.data.prefs.AutoLock
import com.musibox.app.ui.components.ChoiceDialog
import com.musibox.app.ui.components.ConfirmDialog
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbCard
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.SettingDivider
import com.musibox.app.ui.components.SettingItem
import com.musibox.app.ui.components.SettingSwitch
import com.musibox.app.ui.components.SettingsSection
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb
import com.musibox.app.vault.Biometrics
import com.musibox.app.vault.PinManager
import com.musibox.app.vault.VaultBackup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultSettingsScreen(nav: NavController) {
    VaultGate { VaultSettingsContent(nav) }
}

@Composable
private fun VaultSettingsContent(nav: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var lockDialog by remember { mutableStateOf(false) }
    var wipeDialog by remember { mutableStateOf(false) }
    val biometricHardware = remember { Biometrics.isAvailable(context) }
    val vault = Mb.colors.vault

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Configurações do cofre", onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SettingsSection("Segurança") {
                    SettingItem(Icons.Rounded.Key, "Alterar PIN", "PIN de 6 dígitos", onClick = { nav.navigate(Routes.VAULT_CHANGE_PIN) }, tint = vault)
                    SettingDivider()
                    SettingSwitch(
                        Icons.Rounded.Fingerprint,
                        "Biometria",
                        if (biometricHardware) "Impressão digital ou rosto, conforme o aparelho" else "Nenhuma biometria cadastrada neste aparelho",
                        checked = settings.vaultBiometric && biometricHardware,
                        enabled = biometricHardware,
                        tint = vault,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                scope.launch { container.settings.setVaultBiometric(false) }
                            } else {
                                val act = context.findActivity() as? FragmentActivity
                                if (act != null) {
                                    Biometrics.authenticate(act, "Ativar biometria", "Confirme para usar no cofre", onSuccess = {
                                        scope.launch { container.settings.setVaultBiometric(true) }
                                        snack("Biometria ativada.")
                                    }, onError = { msg -> if (msg != null) snack(msg) })
                                }
                            }
                        },
                    )
                    SettingDivider()
                    SettingItem(Icons.Rounded.Timer, "Bloqueio automático", settings.vaultAutoLock.label, onClick = { lockDialog = true }, tint = vault)
                }
            }
            item {
                SettingsSection("Privacidade") {
                    SettingSwitch(
                        Icons.Rounded.Screenshot,
                        "Bloquear capturas de tela",
                        "Impede prints e oculta o cofre na tela de apps recentes",
                        checked = settings.vaultSecureScreen,
                        tint = vault,
                        onCheckedChange = { v -> scope.launch { container.settings.setVaultSecureScreen(v) } },
                    )
                    SettingDivider()
                    SettingSwitch(
                        Icons.Rounded.VisibilityOff,
                        "Ocultar miniaturas",
                        "Mostra só ícones na lista do cofre",
                        checked = settings.vaultHideThumbnails,
                        tint = vault,
                        onCheckedChange = { v -> scope.launch { container.settings.setVaultHideThumbnails(v) } },
                    )
                }
            }
            item {
                SettingsSection("Backup do cofre") {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.CloudOff, null, tint = Mb.colors.warning)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Backup na nuvem: DESATIVADO", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Seus arquivos do cofre ficam somente neste aparelho, criptografados. Entrar com a Conta Google " +
                                    "sincroniza playlists, favoritos e configurações, mas nunca envia fotos, vídeos ou arquivos do cofre. " +
                                    "Enviar o cofre para a nuvem significaria guardar seus arquivos privados em um servidor; por isso o MusiBox não faz isso.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SettingDivider()
                    SettingItem(
                        Icons.Rounded.Backup,
                        "Backup manual protegido por senha",
                        "Crie um arquivo criptografado para guardar onde quiser ou restaure em outro aparelho",
                        onClick = { nav.navigate(Routes.VAULT_BACKUP) },
                        tint = vault,
                    )
                }
            }
            item {
                SettingsSection("Zona de risco") {
                    SettingItem(
                        Icons.Rounded.DeleteForever,
                        "Apagar todo o cofre",
                        "Remove permanentemente todos os itens do cofre",
                        onClick = { wipeDialog = true },
                        tint = Mb.colors.danger,
                        titleColor = Mb.colors.danger,
                    )
                }
            }
        }
    }

    if (lockDialog) {
        ChoiceDialog(
            title = "Bloqueio automático",
            description = "Quando o cofre deve pedir o PIN novamente.",
            options = AutoLock.entries,
            selected = settings.vaultAutoLock,
            label = { it.label },
            onSelect = { v ->
                lockDialog = false
                scope.launch { container.settings.setVaultAutoLock(v) }
            },
            onDismiss = { lockDialog = false },
        )
    }
    if (wipeDialog) {
        ConfirmDialog(
            title = "Apagar todo o cofre?",
            message = "Todos os itens do cofre serão apagados permanentemente. Esta ação não pode ser desfeita.",
            confirmText = "Apagar tudo",
            destructive = true,
            onConfirm = {
                wipeDialog = false
                scope.launch {
                    container.vault.wipeAll()
                    snack("Cofre apagado.")
                    nav.popBackStack()
                }
            },
            onDismiss = { wipeDialog = false },
        )
    }
}

/** Alterar o PIN: confirma o atual e cria um novo. */
@Composable
fun VaultChangePinScreen(nav: NavController) {
    VaultGate {
        val container = LocalAppContainer.current
        val scope = rememberCoroutineScope()
        val snack = rememberSnack()
        var verified by remember { mutableStateOf(false) }
        var entry by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Column(Modifier.fillMaxSize()) {
            MbTopBar("Alterar PIN", onBack = { nav.popBackStack() })
            if (!verified) {
                PinScreenLayout(
                    icon = Icons.Rounded.Key,
                    title = "PIN atual",
                    subtitle = "Digite o PIN que você usa hoje.",
                    entry = entry,
                    error = error,
                    onDigit = { d ->
                        if (entry.length < 6) {
                            entry += d
                            error = null
                            if (entry.length == 6) {
                                val typed = entry
                                scope.launch {
                                    when (val r = container.pin.verify(typed)) {
                                        PinManager.VerifyResult.Ok -> verified = true
                                        is PinManager.VerifyResult.Wrong -> error = "PIN incorreto."
                                        is PinManager.VerifyResult.LockedOut -> error = "Muitas tentativas. Aguarde ${(r.remainingMs / 1000) + 1} s."
                                    }
                                    entry = ""
                                }
                            }
                        }
                    },
                    onBackspace = { entry = entry.dropLast(1) },
                )
            } else {
                PinSetupFlow(title = "Novo PIN", offerBiometric = false, onDone = {
                    snack("PIN alterado.")
                    nav.popBackStack()
                })
            }
        }
    }
}

/** Backup manual do cofre em arquivo protegido por senha. */
@Composable
fun VaultBackupScreen(nav: NavController) {
    VaultGate {
        val container = LocalAppContainer.current
        val scope = rememberCoroutineScope()
        val snack = rememberSnack()
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var restorePassword by remember { mutableStateOf("") }
        var progress by remember { mutableStateOf<Pair<String, Float>?>(null) }
        var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

        val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                val pwd = password.toCharArray()
                progress = "Criando backup…" to 0f
                scope.launch {
                    try {
                        val n = container.vaultBackup.export(uri, pwd) { p -> progress = "Criando backup…" to p }
                        snack("Backup criado com $n itens.")
                        password = ""
                        confirm = ""
                    } catch (e: Exception) {
                        snack(e.message ?: "Não foi possível criar o backup.")
                    } finally {
                        pwd.fill(' ')
                        progress = null
                    }
                }
            }
        }
        val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) restoreUri = uri
        }

        Column(Modifier.fillMaxSize()) {
            MbTopBar("Backup do cofre", onBack = { nav.popBackStack() })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                MbCard(Modifier.fillMaxWidth(), background = Mb.colors.vaultSoft) {
                    Text(
                        "O backup é um único arquivo criptografado com a senha que você escolher. Guarde-o onde quiser " +
                            "(pen drive, computador, Google Drive). Sem a senha ninguém consegue abrir — nem você. " +
                            "Nada é enviado automaticamente para a internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("Criar backup", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PasswordField(password, { password = it }, "Senha (mínimo 8 caracteres)")
                Spacer(Modifier.height(8.dp))
                PasswordField(confirm, { confirm = it }, "Confirmar senha")
                if (confirm.isNotEmpty() && confirm != password) {
                    Text("As senhas não conferem.", color = Mb.colors.danger, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    "Escolher onde salvar o backup",
                    onClick = {
                        container.vaultLock.expectExternalActivity()
                        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
                        createLauncher.launch("MusiBox-cofre-$date.mbxvault")
                    },
                    icon = Icons.Rounded.Backup,
                    enabled = password.length >= 8 && password == confirm && progress == null,
                    brush = Mb.colors.vaultGradient,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(28.dp))
                Text("Restaurar backup", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Os itens do backup são adicionados ao cofre atual (nada é apagado).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    "Escolher arquivo de backup",
                    onClick = {
                        container.vaultLock.expectExternalActivity()
                        openLauncher.launch(arrayOf("*/*"))
                    },
                    icon = Icons.Rounded.Restore,
                    enabled = progress == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                progress?.let { (label, value) ->
                    Spacer(Modifier.height(20.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth(), color = Mb.colors.vault)
                }
            }
        }

        restoreUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { restoreUri = null },
                title = { Text("Senha do backup") },
                text = { PasswordField(restorePassword, { restorePassword = it }, "Senha") },
                confirmButton = {
                    TextButton(enabled = restorePassword.isNotEmpty(), onClick = {
                        val pwd = restorePassword.toCharArray()
                        restoreUri = null
                        restorePassword = ""
                        progress = "Restaurando…" to 0f
                        scope.launch {
                            try {
                                val n = container.vaultBackup.import(uri, pwd) { p -> progress = "Restaurando…" to p }
                                snack("$n itens restaurados no cofre.")
                            } catch (e: VaultBackup.WrongPasswordException) {
                                snack("Senha incorreta ou arquivo danificado.")
                            } catch (e: Exception) {
                                snack(e.message ?: "Não foi possível restaurar.")
                            } finally {
                                pwd.fill(' ')
                                progress = null
                            }
                        }
                    }) { Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancelar") } },
                containerColor = Mb.colors.cardHigh,
            )
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mb.colors.border),
        modifier = Modifier.fillMaxWidth(),
    )
}
