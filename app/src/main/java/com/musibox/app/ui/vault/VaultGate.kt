package com.musibox.app.ui.vault

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.core.findActivity
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.NumberPad
import com.musibox.app.ui.components.PinDots
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.theme.Mb
import com.musibox.app.vault.Biometrics
import com.musibox.app.vault.PinManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Contador para manter FLAG_SECURE ligado enquanto qualquer tela do cofre estiver visível. */
private object SecureFlag {
    private var count = 0
    fun acquire(activity: Activity?) {
        if (count++ == 0) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    fun release(activity: Activity?) {
        count = (count - 1).coerceAtLeast(0)
        if (count == 0) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Protege as telas do cofre: na primeira vez cria o PIN; depois exige PIN/biometria
 * sempre que o cofre estiver bloqueado.
 */
@Composable
fun VaultGate(content: @Composable () -> Unit) {
    val container = LocalAppContainer.current
    val unlocked by container.vaultLock.unlocked.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var pinSet by remember { mutableStateOf(container.pin.isPinSet()) }
    val activity = LocalContext.current.findActivity()

    DisposableEffect(Unit) {
        container.vaultLock.onVaultScreenVisible()
        onDispose { container.vaultLock.onVaultScreenHidden() }
    }
    DisposableEffect(settings.vaultSecureScreen) {
        val secure = settings.vaultSecureScreen
        if (secure) SecureFlag.acquire(activity)
        onDispose { if (secure) SecureFlag.release(activity) }
    }

    when {
        !pinSet -> PinSetupFlow(
            title = "Proteja seu cofre",
            onDone = {
                pinSet = true
                container.vaultLock.unlock()
            },
        )
        !unlocked -> PinUnlock(
            biometricEnabled = settings.vaultBiometric,
            onUnlocked = { container.vaultLock.unlock() },
            onVaultReset = { pinSet = false },
        )
        else -> content()
    }
}

/** Criação de PIN (6 dígitos) + oferta de biometria. */
@Composable
fun PinSetupFlow(title: String, onDone: () -> Unit, offerBiometric: Boolean = true) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var askBiometric by remember { mutableStateOf(false) }

    if (askBiometric) {
        BiometricOffer(
            onEnable = {
                val act = context.findActivity() as? FragmentActivity
                if (act == null) {
                    onDone()
                } else {
                    Biometrics.authenticate(act, "Ativar biometria", "Confirme para usar no cofre", onSuccess = {
                        scope.launch { container.settings.setVaultBiometric(true) }
                        onDone()
                    }, onError = { onDone() })
                }
            },
            onSkip = onDone,
        )
        return
    }

    PinScreenLayout(
        icon = Icons.Rounded.Shield,
        title = if (first == null) title else "Confirme o PIN",
        subtitle = if (first == null) "Crie um PIN de 6 dígitos para abrir o cofre." else "Digite o mesmo PIN novamente.",
        entry = entry,
        error = error,
        onDigit = { d ->
            if (entry.length < 6) {
                entry += d
                error = null
                if (entry.length == 6) {
                    val typed = entry
                    val f = first
                    if (f == null) {
                        first = typed
                        entry = ""
                    } else if (f == typed) {
                        scope.launch {
                            container.pin.setPin(typed)
                            if (offerBiometric && Biometrics.isAvailable(context)) askBiometric = true else onDone()
                        }
                    } else {
                        error = "Os PINs não conferem. Tente novamente."
                        first = null
                        entry = ""
                    }
                }
            }
        },
        onBackspace = { entry = entry.dropLast(1) },
    )
}

@Composable
private fun BiometricOffer(onEnable: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconCircle(Icons.Rounded.Fingerprint)
        Spacer(Modifier.height(20.dp))
        Text("Usar biometria?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Abra o cofre com sua impressão digital ou rosto. O PIN continua funcionando.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("Ativar biometria", onEnable, brush = Mb.colors.vaultGradient, icon = Icons.Rounded.Fingerprint)
        TextButton(onClick = onSkip) { Text("Agora não") }
    }
}

/** Desbloqueio por PIN (com bloqueio progressivo) e biometria. */
@Composable
fun PinUnlock(biometricEnabled: Boolean, onUnlocked: () -> Unit, onVaultReset: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockout by remember { mutableLongStateOf(container.pin.lockoutRemainingMs()) }
    var forgot by remember { mutableStateOf(false) }
    var resettingPin by remember { mutableStateOf(false) }
    val biometricHardware = remember { Biometrics.isAvailable(context) }
    val biometricAvailable = biometricEnabled && biometricHardware

    val promptBiometric: () -> Unit = {
        val act = context.findActivity() as? FragmentActivity
        if (act != null) {
            Biometrics.authenticate(act, "Desbloquear cofre", null, onSuccess = onUnlocked, onError = { msg -> if (msg != null) error = msg })
        }
    }

    LaunchedEffect(Unit) { if (biometricAvailable && lockout == 0L) promptBiometric() }
    LaunchedEffect(lockout) {
        while (lockout > 0) {
            delay(1000)
            lockout = container.pin.lockoutRemainingMs()
        }
    }

    if (resettingPin) {
        PinSetupFlow(title = "Crie um novo PIN", offerBiometric = false, onDone = {
            snack("PIN alterado.")
            onUnlocked()
        })
        return
    }

    PinScreenLayout(
        icon = Icons.Rounded.Lock,
        title = "Cofre bloqueado",
        subtitle = if (lockout > 0) "Muitas tentativas. Tente novamente em ${(lockout / 1000) + 1} s." else "Digite seu PIN",
        entry = entry,
        error = error,
        enabled = lockout == 0L,
        extraKey = if (biometricAvailable) {
            { IconButton(onClick = promptBiometric) { Icon(Icons.Rounded.Fingerprint, "Usar biometria", tint = Mb.colors.vault, modifier = Modifier.size(34.dp)) } }
        } else null,
        onDigit = { d ->
            if (lockout == 0L && entry.length < 6) {
                entry += d
                error = null
                if (entry.length == 6) {
                    val typed = entry
                    scope.launch {
                        when (val r = container.pin.verify(typed)) {
                            PinManager.VerifyResult.Ok -> onUnlocked()
                            is PinManager.VerifyResult.Wrong -> {
                                error = "PIN incorreto. ${if (r.attemptsLeft == 1) "1 tentativa restante" else "${r.attemptsLeft} tentativas restantes"}."
                            }
                            is PinManager.VerifyResult.LockedOut -> {
                                error = "PIN incorreto."
                                lockout = r.remainingMs
                            }
                        }
                        entry = ""
                    }
                }
            }
        },
        onBackspace = { entry = entry.dropLast(1) },
        footer = { TextButton(onClick = { forgot = true }) { Text("Esqueci o PIN", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )

    if (forgot) {
        ForgotPinDialog(
            biometricAvailable = biometricAvailable,
            onUseBiometric = {
                forgot = false
                val act = context.findActivity() as? FragmentActivity ?: return@ForgotPinDialog
                Biometrics.authenticate(act, "Confirme sua identidade", "Para criar um novo PIN", onSuccess = { resettingPin = true }, onError = { })
            },
            onWipe = {
                forgot = false
                scope.launch {
                    container.vault.wipeAll()
                    container.pin.clear()
                    container.settings.setVaultBiometric(false)
                    snack("Cofre apagado. Crie um novo PIN.")
                    onVaultReset()
                }
            },
            onDismiss = { forgot = false },
        )
    }
}

@Composable
private fun ForgotPinDialog(
    biometricAvailable: Boolean,
    onUseBiometric: () -> Unit,
    onWipe: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmText by remember { mutableStateOf("") }
    var wiping by remember { mutableStateOf(!biometricAvailable) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Esqueci o PIN") },
        text = {
            Column {
                if (!wiping) {
                    Text("Use sua biometria para criar um novo PIN sem perder os arquivos.")
                } else {
                    Text(
                        "Sem o PIN não é possível abrir o cofre. Você pode apagar o cofre e todo o conteúdo dele para começar de novo. " +
                            "Esta ação não pode ser desfeita.",
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        label = { Text("Digite APAGAR para confirmar") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            if (!wiping) {
                TextButton(onClick = onUseBiometric) { Text("Usar biometria") }
            } else {
                TextButton(onClick = onWipe, enabled = confirmText.trim().equals("APAGAR", ignoreCase = true)) {
                    Text("Apagar cofre", color = Mb.colors.danger)
                }
            }
        },
        dismissButton = {
            if (!wiping) {
                TextButton(onClick = { wiping = true }) { Text("Apagar o cofre") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
        containerColor = Mb.colors.cardHigh,
    )
}

@Composable
fun PinScreenLayout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    entry: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true,
    extraKey: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        IconCircle(icon)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        PinDots(6, entry.length, error != null)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            if (error != null) Text(error, color = Mb.colors.danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        NumberPad(
            onDigit = { if (enabled) onDigit(it) },
            onBackspace = { if (enabled) onBackspace() },
            extraKey = extraKey,
        )
        Spacer(Modifier.height(12.dp))
        footer?.invoke()
    }
}

@Composable
private fun IconCircle(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(Mb.colors.vaultGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(42.dp))
    }
}
