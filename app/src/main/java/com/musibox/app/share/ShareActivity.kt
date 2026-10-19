package com.musibox.app.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musibox.app.MainActivity
import com.musibox.app.MusiBoxApp
import com.musibox.app.core.Fmt
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.LocalSnackbar
import com.musibox.app.ui.components.LogoMark
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.theme.Mb
import com.musibox.app.ui.theme.MusiBoxTheme
import kotlinx.coroutines.launch

/**
 * Recebe o que o usuário compartilha de outros apps:
 * fotos/vídeos/áudios → "Salvar no cofre".
 */
class ShareActivity : ComponentActivity() {

    private val container get() = (application as MusiBoxApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val payload = parse(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            MusiBoxTheme(s.themeMode) {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalSnackbar provides remember { androidx.compose.material3.SnackbarHostState() },
                ) {
                    when (payload) {
                        is SharePayload.Media -> VaultSheet(payload.uris, onClose = { finish() })
                        SharePayload.Nothing -> ErrorSheet(onClose = { finish() })
                    }
                }
            }
        }
    }

    private sealed interface SharePayload {
        data class Media(val uris: List<Uri>) : SharePayload
        data object Nothing : SharePayload
    }

    private fun parse(intent: Intent?): SharePayload {
        intent ?: return SharePayload.Nothing
        val type = intent.type.orEmpty()
        val uris = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        return if (uris.isNotEmpty()) SharePayload.Media(uris) else SharePayload.Nothing
    }

    @Composable
    private fun SheetHeader(title: String) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            LogoMark(26.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
    }

    @Composable
    private fun VaultSheet(uris: List<Uri>, onClose: () -> Unit) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0) }
        val pinSet = remember { container.pin.isPinSet() }

        ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }, sheetState = sheetState, containerColor = Mb.colors.cardHigh) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            ) {
                SheetHeader("Salvar no cofre")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = Mb.colors.vault, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (pinSet) "${Fmt.plural(uris.size, "arquivo será copiado", "arquivos serão copiados")} para a área privada e criptografada do MusiBox. O original não é apagado."
                        else "Crie o PIN do cofre no MusiBox antes de guardar arquivos nele.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(18.dp))
                if (!pinSet) {
                    GradientButton("Abrir o cofre", onClick = {
                        startActivity(
                            Intent(this@ShareActivity, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_VAULT, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        onClose()
                    }, brush = Mb.colors.vaultGradient, modifier = Modifier.fillMaxWidth())
                } else {
                    GradientButton(
                        text = if (busy) "Salvando ${progress + 1} de ${uris.size}…" else "Copiar para o cofre",
                        icon = Icons.Rounded.Lock,
                        loading = busy,
                        brush = Mb.colors.vaultGradient,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true
                            scope.launch {
                                var ok = 0
                                var error: String? = null
                                uris.forEachIndexed { i, uri ->
                                    progress = i
                                    try {
                                        val item = container.vault.importUri(uri, source = "share")
                                        ok++
                                    } catch (e: Exception) {
                                        error = e.message
                                    }
                                }
                                val msg = when {
                                    ok == uris.size -> if (ok == 1) "Arquivo salvo no cofre." else "$ok arquivos salvos no cofre."
                                    ok == 0 -> error ?: "Não foi possível salvar no cofre."
                                    else -> "$ok de ${uris.size} arquivos salvos no cofre."
                                }
                                Toast.makeText(this@ShareActivity, msg, Toast.LENGTH_LONG).show()
                                busy = false
                                onClose()
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun ErrorSheet(onClose: () -> Unit) {
        ModalBottomSheet(onDismissRequest = onClose, containerColor = Mb.colors.cardHigh) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            ) {
                SheetHeader("MusiBox")
                Text("Nenhuma foto, vídeo ou áudio foi encontrado no conteúdo compartilhado.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose) { Text("Fechar", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
