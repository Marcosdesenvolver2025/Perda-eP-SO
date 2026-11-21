package com.musibox.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musibox.app.themes.AppTheme

/**
 * Tocar com a tela desligada: alguns celulares (principalmente Samsung) param apps "otimizados" na bateria
 * mesmo com a música tocando. Aqui o app mostra se está liberado e leva a pessoa direto para a tela certa.
 */
object BackgroundPlay {
    private const val PREFS = "player"
    private const val KEY_TIP = "bg_tip_shown"

    fun isUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** Abre a página do app nas configurações (lá fica "Bateria" → "Sem restrições"). */
    fun openSettings(context: Context) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(details) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Aviso mostrado uma vez só, na primeira música, se o app ainda estiver limitado pela bateria. */
    fun shouldShowTip(context: Context): Boolean =
        !isUnrestricted(context) && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TIP, false)

    fun markTipShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TIP, true).apply()
    }
}

@Composable
fun BackgroundPlayDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val samsung = BackgroundPlay.isSamsung
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Música parando com a tela desligada?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Para economizar bateria, o celular pode fechar o MusiBox depois de alguns minutos com a tela desligada. " +
                        "Libere o app uma vez e a música não para mais:",
                )
                Spacer(Modifier.height(12.dp))
                Text("1. Toque em Abrir configurações.", fontWeight = FontWeight.SemiBold)
                Text("2. Toque em Bateria e escolha Sem restrições.", fontWeight = FontWeight.SemiBold)
                if (samsung) {
                    Spacer(Modifier.height(12.dp))
                    Text("No Samsung, confira também:", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Configurações → Bateria → Limites de uso em segundo plano. Se o MusiBox estiver em \"Apps em suspensão\" " +
                            "ou \"Apps em suspensão profunda\", tire de lá e coloque em \"Apps que nunca entram em suspensão\".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Deixe também o modo de economia de energia desligado enquanto ouve música.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                BackgroundPlay.openSettings(context)
            }) { Text("Abrir configurações") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
        containerColor = AppTheme.card,
    )
}
