package com.musibox.app.ui.pro

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.MainActivity
import com.musibox.app.ads.DownloadQuota
import com.musibox.app.billing.ProManager
import com.musibox.app.billing.ProPlan
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Abre a tela do Pró (de qualquer lugar, inclusive do "Compartilhar"). */
object ProLauncher {
    fun open(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PRO, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun fmtDate(ms: Long): String = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")).format(Date(ms))

/** Tela "MusiBox Pró": benefícios, planos e assinatura pela Stripe. */
@Composable
fun ProScreen(nav: NavController) {
    val context = LocalContext.current
    val state by ProManager.state.collectAsStateWithLifecycle()
    val credits by DownloadQuota.credits.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var selected by rememberSaveable { mutableStateOf(ProPlan.MONTHLY) }
    var busy by remember { mutableStateOf(false) }
    val signedIn = com.google.firebase.FirebaseApp.getApps(context).isNotEmpty() &&
        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)

    Column(Modifier.fillMaxSize()) {
        MbTopBar("MusiBox Pró", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Neo.Green),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.WorkspacePremium, null, tint = Neo.OnGreen, modifier = Modifier.size(50.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("Baixe sem anúncios", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            Text(
                "Com o Pró você baixa quantos vídeos e músicas quiser, sem assistir anúncios.",
                color = Neo.Text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Situação atual
            Spacer(Modifier.height(18.dp))
            if (state.active) {
                StatusCard(
                    Icons.Rounded.CheckCircle,
                    "Você é Pró ✓",
                    when {
                        state.renews -> "Assinatura ativa • renova em ${fmtDate(state.until)}"
                        state.until > 0 -> "Vale até ${fmtDate(state.until)}"
                        else -> "Ativo"
                    },
                    highlight = true,
                )
                if (state.fromSubscription) {
                    TextButton(onClick = {
                        busy = true
                        scope.launch {
                            runCatching { ProManager.portalUrl() }
                                .onSuccess { openExternal(context, it) }
                                .onFailure { snack(it.message ?: "Não foi possível abrir.") }
                            busy = false
                        }
                    }) { Text("Gerenciar ou cancelar assinatura", color = Neo.Green) }
                }
            } else {
                StatusCard(
                    Icons.Rounded.Download,
                    "Plano grátis",
                    if (credits > 0) "Você ainda tem ${if (credits == 1) "1 download grátis" else "$credits downloads grátis"} antes do próximo anúncio."
                    else "Próximo download: assista a 1 anúncio e libere mais ${DownloadQuota.PER_CYCLE}.",
                )
            }

            // Benefícios
            Spacer(Modifier.height(18.dp))
            Benefit(Icons.Rounded.Block, "Sem anúncios", "Nenhum anúncio para baixar.")
            Benefit(Icons.Rounded.Download, "Downloads sem parar", "Sem o limite de ${DownloadQuota.PER_CYCLE} por vez.")
            Benefit(Icons.Rounded.Favorite, "Apoie o MusiBox", "Ajuda a manter o app sempre atualizado.")

            // Planos
            Spacer(Modifier.height(18.dp))
            Text("Escolha seu plano", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ProPlan.entries.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan == selected,
                    badge = when (plan) {
                        ProPlan.MONTHLY -> "Economize 13%"
                        ProPlan.THREE_DAYS -> "Para testar"
                        else -> null
                    },
                ) { selected = plan }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Pagamento seguro pela Stripe (cartão e outros meios disponíveis). Assinaturas renovam sozinhas até você cancelar. " +
                    "É preciso estar com a conta Google conectada para o Pró valer em qualquer aparelho.",
                color = Neo.Artist,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            when {
                !ProManager.paymentsConfigured -> GradientButton("Pagamentos em configuração", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
                !signedIn -> GradientButton("Entrar com Google para assinar", onClick = { nav.navigate(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth())
                else -> GradientButton(
                    if (selected.oneTime) "Comprar ${selected.title} • ${selected.price}" else "Assinar • ${selected.price}${selected.period}",
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { ProManager.createCheckout(selected) }
                                .onSuccess { url -> nav.navigate(Routes.proCheckout(url)) }
                                .onFailure { snack(it.message ?: "Não foi possível iniciar o pagamento.") }
                            busy = false
                        }
                    },
                    enabled = !busy && selected.priceId.isNotBlank(),
                    loading = busy,
                    icon = Icons.Rounded.Lock,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, highlight: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) Color(0xFF12301E) else Color(0xFF1E1E1E))
            .border(1.dp, if (highlight) Neo.Green else Color(0xFF2A2A2A), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Neo.Green, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Benefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Neo.Green, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(text, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanCard(plan: ProPlan, selected: Boolean, badge: String?, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Neo.Green else Color(0xFF2A2A2A), label = "planBorder")
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(pressed = 0.97f, shape = shape, onClick = onClick)
            .background(if (selected) Color(0xFF16261C) else Color(0xFF1E1E1E))
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) Neo.Green else Color(0xFF5A5A5A), CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (selected) Neo.Green else Color.Transparent),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Neo.Green)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) { Text(badge, color = Neo.OnGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Text(plan.detail, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(plan.price, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            if (plan.period.isNotBlank()) Text(plan.period, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Página de pagamento da Stripe dentro do app. Quando a Stripe volta para o endereço de
 * sucesso/cancelamento, a tela fecha sozinha (o Pró é ativado pelo Firestore).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ProCheckoutScreen(nav: NavController, url: String) {
    val context = LocalContext.current
    val snack = rememberSnack()
    var progress by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Fechar", tint = Color.White) }
            Icon(Icons.Rounded.Lock, null, tint = Neo.Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pagamento seguro • Stripe", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { openExternal(context, url) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Abrir no navegador", tint = Color.White)
            }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Neo.Green,
                trackColor = Color.Transparent,
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url.toString()
                            return when {
                                target.startsWith(ProManager.successUrl) -> {
                                    snack("Pagamento recebido! Ativando o Pró…")
                                    nav.popBackStack()
                                    true
                                }
                                target.startsWith(ProManager.cancelUrl) -> {
                                    nav.popBackStack()
                                    true
                                }
                                request.url.scheme == "http" || request.url.scheme == "https" -> false
                                else -> {
                                    // Apps de banco / Pix
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    true
                                }
                            }
                        }
                    }
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
