package com.musibox.app.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.musibox.app.billing.PlayBilling
import com.musibox.app.browser.AdBlocker
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.vault.PinManager
import kotlinx.coroutines.launch

/**
 * Abre a navegação privada. Com o Pro e a opção "Senha nas abas privadas" ligada,
 * pede o PIN do cofre antes.
 */
@Composable
fun rememberPrivateOpener(onOpen: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Navegação privada") },
            text = {
                Column {
                    Text("Digite o PIN do cofre para abrir as abas privadas.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) pin = v; error = null },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { e -> { Text(e) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val typed = pin
                    scope.launch {
                        when (val r = container.pin.verify(typed)) {
                            PinManager.VerifyResult.Ok -> {
                                asking = false
                                pin = ""
                                onOpen()
                            }
                            is PinManager.VerifyResult.Wrong -> error = "PIN incorreto."
                            is PinManager.VerifyResult.LockedOut -> error = "Muitas tentativas. Aguarde ${r.remainingMs / 1000 + 1} s."
                        }
                        pin = ""
                    }
                }, enabled = pin.length >= 4) { Text("Abrir") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancelar") } },
            containerColor = com.musibox.app.themes.AppTheme.card,
        )
    }

    return {
        val needsPin = PlayBilling.isProNow() && AdBlocker.privateLock(context) && container.pin.isPinSet()
        if (needsPin) {
            pin = ""
            error = null
            asking = true
        } else {
            onOpen()
        }
    }
}
