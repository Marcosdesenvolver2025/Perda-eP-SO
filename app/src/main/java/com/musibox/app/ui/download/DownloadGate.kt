package com.musibox.app.ui.download

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musibox.app.ads.DownloadQuota
import com.musibox.app.ads.RewardedAds
import com.musibox.app.billing.ProManager
import com.musibox.app.core.findActivity
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.theme.Mb

/**
 * Portão dos downloads: 2 grátis, depois 1 anúncio libera mais 2. Quem é Pró baixa direto.
 */
@Stable
class DownloadGate internal constructor(private val context: android.content.Context, private val onOpenPro: () -> Unit) {
    internal var pending by mutableStateOf<(() -> Unit)?>(null)
    internal var loadingAd by mutableStateOf(false)
    internal var note by mutableStateOf<String?>(null)

    /** Chame antes de começar um download. [action] roda agora ou depois do anúncio. */
    fun request(action: () -> Unit) {
        if (ProManager.isProNow()) {
            action()
            return
        }
        if (DownloadQuota.consume(context)) {
            action()
            if (DownloadQuota.credits.value == 0) RewardedAds.preload(context)
            return
        }
        note = null
        pending = action
        RewardedAds.preload(context)
    }

    internal fun dismiss() {
        pending = null
        loadingAd = false
        note = null
    }

    internal fun watchAd() {
        val activity = context.findActivity() ?: return
        loadingAd = true
        note = null
        RewardedAds.show(
            activity,
            onReward = {
                DownloadQuota.refill(context)
                DownloadQuota.consume(context)
                val run = pending
                dismiss()
                Toast.makeText(context, "Pronto! Mais ${DownloadQuota.PER_CYCLE} downloads liberados.", Toast.LENGTH_SHORT).show()
                run?.invoke()
            },
            onUnavailable = {
                // Sem anúncio agora (sem internet ou sem anúncio disponível): não trava a pessoa.
                DownloadQuota.refill(context)
                DownloadQuota.consume(context)
                val run = pending
                dismiss()
                Toast.makeText(context, "Nenhum anúncio disponível agora — download liberado.", Toast.LENGTH_SHORT).show()
                run?.invoke()
            },
            onClosed = {
                loadingAd = false
                note = "Assista o anúncio até o fim para liberar os downloads."
            },
        )
    }

    internal fun openPro() {
        dismiss()
        onOpenPro()
    }
}

@Composable
fun rememberDownloadGate(onOpenPro: () -> Unit): DownloadGate {
    val context = LocalContext.current
    val gate = remember(context) { DownloadGate(context, onOpenPro) }
    if (gate.pending != null) DownloadGateDialog(gate)
    return gate
}

@Composable
private fun DownloadGateDialog(gate: DownloadGate) {
    AlertDialog(
        onDismissRequest = { if (!gate.loadingAd) gate.dismiss() },
        icon = {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Mb.colors.accentGradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.OndemandVideo, null, tint = Mb.colors.onAccent, modifier = Modifier.size(34.dp)) }
        },
        title = { Text("Libere mais ${DownloadQuota.PER_CYCLE} downloads", textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Você usou seus ${DownloadQuota.PER_CYCLE} downloads grátis. Assista a um anúncio curto e baixe mais ${DownloadQuota.PER_CYCLE} vídeos ou músicas.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                gate.note?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Mb.colors.warning, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
                GradientButton(
                    if (gate.loadingAd) "Carregando anúncio…" else "Assistir anúncio",
                    onClick = { gate.watchAd() },
                    enabled = !gate.loadingAd,
                    loading = gate.loadingAd,
                    icon = Icons.Rounded.OndemandVideo,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF242424))
                        .then(Modifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { gate.openPro() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.WorkspacePremium, null, tint = Mb.colors.vault)
                        Spacer(Modifier.width(8.dp))
                        Text("Seja Pró — sem anúncios", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { gate.dismiss() }, enabled = !gate.loadingAd) { Text("Agora não") } },
        containerColor = Mb.colors.cardHigh,
    )
}
