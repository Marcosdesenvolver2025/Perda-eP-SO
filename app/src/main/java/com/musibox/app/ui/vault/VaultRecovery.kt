package com.musibox.app.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.AppContainer
import com.musibox.app.core.findActivity
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.sync.SignInResult
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.settings.startGoogleSignIn
import com.musibox.app.ui.theme.Mb
import com.musibox.app.vault.Biometrics
import com.musibox.app.vault.PinManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Configurar formas de recuperação (cofre desbloqueado)

@Composable
fun VaultRecoverScreen(nav: NavController) {
    VaultGate { RecoverySettings(nav) }
}

@Composable
private fun RecoverySettings(nav: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val user by container.auth.user.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var newCode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val biometricHardware = remember { Biometrics.isAvailable(context) }
    val hasCode = remember(refresh) { container.pin.hasRecoveryCode() }
    val linkedEmail = remember(refresh) { container.pin.linkedGoogleEmail() }
    val linkedUid = remember(refresh) { container.pin.linkedGoogleUid() }

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Recuperação do PIN", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Se você esquecer o PIN, poderá criar um novo sem perder os arquivos usando uma destas formas. Ative pelo menos uma.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            RecoveryCard(
                icon = Icons.Rounded.Fingerprint,
                title = "Impressão digital",
                status = when {
                    !biometricHardware -> "Nenhuma digital cadastrada neste aparelho"
                    settings.vaultBiometric -> "Ativada"
                    else -> "Desativada"
                },
                active = settings.vaultBiometric && biometricHardware,
                actionText = if (settings.vaultBiometric) "Desativar" else "Ativar",
                enabled = biometricHardware,
            ) {
                if (settings.vaultBiometric) {
                    scope.launch { container.settings.setVaultBiometric(false) }
                } else {
                    val act = context.findActivity() as? FragmentActivity ?: return@RecoveryCard
                    Biometrics.authenticate(act, "Ativar impressão digital", "Confirme para usar no cofre", onSuccess = {
                        scope.launch { container.settings.setVaultBiometric(true) }
                        snack("Impressão digital ativada.")
                    }, onError = { msg -> if (msg != null) snack(msg) })
                }
            }

            Spacer(Modifier.height(12.dp))
            RecoveryCard(
                icon = Icons.Rounded.AccountCircle,
                title = "Conta Google",
                status = when {
                    !container.auth.isConfigured -> "Login com Google ainda não configurado no Firebase"
                    linkedUid != null -> "Vinculada: ${linkedEmail ?: "sua conta"}"
                    user != null -> "Vincular ${user?.email ?: "sua conta"}"
                    else -> "Entre com o Google para vincular"
                },
                active = linkedUid != null,
                actionText = if (linkedUid != null) "Desvincular" else "Vincular",
                enabled = container.auth.isConfigured && !busy,
            ) {
                if (linkedUid != null) {
                    container.pin.unlinkGoogle()
                    refresh++
                    snack("Conta Google desvinculada do cofre.")
                } else {
                    busy = true
                    scope.launch {
                        val linked = linkGoogleAccount(container, context.findActivity(), snack)
                        busy = false
                        refresh++
                        if (linked != null) snack("Cofre vinculado a $linked.")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            RecoveryCard(
                icon = Icons.Rounded.Password,
                title = "Código de recuperação",
                status = if (hasCode) {
                    "Criado em " + SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(container.pin.recoveryCodeCreatedAt()))
                } else {
                    "Um código de 16 letras para anotar em local seguro"
                },
                active = hasCode,
                actionText = if (hasCode) "Gerar novo" else "Gerar",
            ) {
                scope.launch {
                    newCode = container.pin.createRecoveryCode()
                    refresh++
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    newCode?.let { code -> RecoveryCodeDialog(code) { newCode = null } }
}

/** Vincula a conta Google atual (ou pede login) ao cofre. Retorna o e-mail vinculado. */
suspend fun linkGoogleAccount(container: AppContainer, activity: android.app.Activity?, onMessage: (String) -> Unit): String? {
    val current = container.auth.currentUser
    if (current == null) {
        if (!startGoogleSignIn(container, activity, onMessage)) return null
    }
    val user = container.auth.currentUser ?: return null
    container.pin.linkGoogle(user.uid, user.email)
    return user.email ?: user.name ?: "sua conta"
}

@Composable
private fun RecoveryCard(
    icon: ImageVector,
    title: String,
    status: String,
    active: Boolean,
    actionText: String,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Mb.colors.card)
            .border(1.dp, if (active) Mb.colors.vault.copy(alpha = 0.6f) else Mb.colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Mb.colors.vault.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Mb.colors.vault) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.CheckCircle, "Ativo", tint = Mb.colors.success, modifier = Modifier.size(16.dp))
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction, enabled = enabled) { Text(actionText, color = if (enabled) Mb.colors.vault else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Mostra o código uma única vez, com confirmação de que foi anotado. */
@Composable
fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.Key, null, tint = Mb.colors.vault) },
        title = { Text("Seu código de recuperação") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Anote este código em papel ou num lugar seguro. Ele aparece só agora e permite criar um novo PIN se você esquecer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mb.colors.vault.copy(alpha = 0.14f))
                        .padding(vertical = 14.dp),
                )
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copiado" else "Copiar")
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { saved = !saved },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(saved, { saved = it }, colors = CheckboxDefaults.colors(checkedColor = Mb.colors.vault))
                    Text("Anotei o código em um lugar seguro", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone, enabled = saved) { Text("Concluir") } },
        containerColor = Mb.colors.cardHigh,
    )
}

// ---------------------------------------------------------------------------
// "Esqueci o PIN" (cofre bloqueado)

private enum class ForgotMode { MENU, CODE }

@Composable
fun ForgotPinPanel(
    biometricAvailable: Boolean,
    onVerified: () -> Unit,
    onWipeRequested: () -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ForgotMode.MENU) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val linkedUid = container.pin.linkedGoogleUid()
    val linkedEmail = container.pin.linkedGoogleEmail()
    val hasCode = container.pin.hasRecoveryCode()
    val googleReady = linkedUid != null && container.auth.isConfigured

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { if (mode == ForgotMode.CODE) mode = ForgotMode.MENU else onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
            Spacer(Modifier.width(6.dp))
            Text("Recuperar acesso", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(84.dp)
                .clip(CircleShape)
                .background(Mb.colors.vaultGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.LockReset, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
        Spacer(Modifier.height(16.dp))

        if (mode == ForgotMode.CODE) {
            RecoveryCodeEntry(onVerified = onVerified)
            return@Column
        }

        Text(
            "Confirme que é você para criar um novo PIN. Seus arquivos continuam no cofre.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        ForgotOption(
            Icons.Rounded.Fingerprint,
            "Usar impressão digital",
            if (biometricAvailable) "Toque no sensor do aparelho" else "Não foi ativada no cofre",
            enabled = biometricAvailable,
        ) {
            val act = context.findActivity() as? FragmentActivity ?: return@ForgotOption
            Biometrics.authenticate(act, "Confirme sua identidade", "Para criar um novo PIN", onSuccess = onVerified, onError = { msg ->
                if (msg != null) message = msg
            })
        }
        Spacer(Modifier.height(10.dp))
        ForgotOption(
            Icons.Rounded.AccountCircle,
            "Entrar com a conta Google",
            when {
                linkedUid == null -> "Nenhuma conta Google vinculada ao cofre"
                !container.auth.isConfigured -> "Login com Google indisponível"
                else -> "Use ${linkedEmail ?: "a conta vinculada"}"
            },
            enabled = googleReady && !busy,
        ) {
            busy = true
            message = null
            scope.launch {
                message = verifyGoogleOwner(container, context.findActivity(), linkedUid.orEmpty(), linkedEmail) { onVerified() }
                busy = false
            }
        }
        Spacer(Modifier.height(10.dp))
        ForgotOption(
            Icons.Rounded.Password,
            "Usar código de recuperação",
            if (hasCode) "O código de 16 letras que você anotou" else "Nenhum código foi criado",
            enabled = hasCode,
        ) { mode = ForgotMode.CODE }

        if (busy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Mb.colors.vault)
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Mb.colors.danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Nenhuma opção funciona?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        TextButton(onClick = onWipeRequested, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.DeleteForever, null, tint = Mb.colors.danger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Apagar o cofre e começar de novo", color = Mb.colors.danger)
        }
    }
}

/** Confirma que quem está usando é o dono da conta vinculada. Retorna mensagem de erro ou null. */
private suspend fun verifyGoogleOwner(
    container: AppContainer,
    activity: android.app.Activity?,
    linkedUid: String,
    linkedEmail: String?,
    onOk: () -> Unit,
): String? {
    if (activity == null) return "Não foi possível abrir o login."
    val current = container.auth.currentUser
    val result = when {
        current?.uid == linkedUid -> container.auth.reauthenticate(activity)
        current == null -> {
            var msg: String? = null
            val ok = startGoogleSignIn(container, activity) { msg = it }
            if (!ok) return msg?.takeUnless { it.startsWith("Login cancelado") }
            SignInResult.Success(container.auth.currentUser ?: return "Não foi possível entrar.")
        }
        else -> return "Você está conectado com outra conta. Em Configurações → Conta, saia dela e entre com ${linkedEmail ?: "a conta vinculada"}."
    }
    return when (result) {
        is SignInResult.Success -> if (result.user.uid == linkedUid) {
            onOk()
            null
        } else {
            "Esta não é a conta vinculada ao cofre${linkedEmail?.let { " ($it)" } ?: ""}."
        }
        SignInResult.Canceled -> null
        is SignInResult.Error -> result.message
    }
}

@Composable
private fun RecoveryCodeEntry(onVerified: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Digite o código de recuperação", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Formato: XXXX-XXXX-XXXX-XXXX",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.uppercase().take(24); error = null },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mb.colors.vault, unfocusedBorderColor = Mb.colors.border),
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Mb.colors.danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        GradientButton(
            "Confirmar",
            onClick = {
                checking = true
                scope.launch {
                    when (val r = container.pin.verifyRecoveryCode(code)) {
                        PinManager.VerifyResult.Ok -> onVerified()
                        is PinManager.VerifyResult.Wrong -> error = "Código incorreto. ${r.attemptsLeft} tentativa(s) restante(s)."
                        is PinManager.VerifyResult.LockedOut -> error = "Muitas tentativas. Tente de novo em ${(r.remainingMs / 60_000) + 1} min."
                    }
                    checking = false
                }
            },
            enabled = code.count { it.isLetterOrDigit() } == 16,
            loading = checking,
            brush = Mb.colors.vaultGradient,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ForgotOption(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Mb.colors.card)
            .border(1.dp, Mb.colors.border, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (enabled) Mb.colors.vault.copy(alpha = 0.18f) else Mb.colors.border),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (enabled) Mb.colors.vault else MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Confirmação final para apagar o cofre (último recurso). */
@Composable
fun WipeVaultDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var confirmText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apagar o cofre?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Todos os arquivos do cofre serão apagados deste aparelho e um novo PIN será criado. Esta ação não pode ser desfeita.",
                )
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Digite APAGAR para confirmar") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmText.trim().equals("APAGAR", ignoreCase = true)) {
                Text("Apagar cofre", color = Mb.colors.danger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Mb.colors.cardHigh,
    )
}
