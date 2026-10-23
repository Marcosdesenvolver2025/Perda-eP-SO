package com.musibox.app.ui.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.MainActivity
import com.musibox.app.billing.PlayBilling
import com.musibox.app.billing.Plan
import com.musibox.app.billing.ProPlan
import com.musibox.app.themes.Wallpapers
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.core.findActivity
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.components.rememberSnack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Abre a tela dos planos (de qualquer lugar). */
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

/** Benefícios dos planos. ready=false aparece como "chegando". */
private data class Perk(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val text: String, val ready: Boolean)

private val perks = listOf(
    Perk(Icons.Rounded.Block, "Bloqueador de anúncios", "Navegue sem propagandas e rastreadores nos sites.", true),
    Perk(Icons.Rounded.Equalizer, "Equalizador no player", "Estilos (pop, rock, funk…), graves e som 3D.", true),
    Perk(Icons.Rounded.TheaterComedy, "Ícone disfarçado do cofre", "O MusiBox aparece como Calculadora, Notas, Relógio ou Clima.", true),
    Perk(Icons.Rounded.Bedtime, "Timer de sono", "A música para sozinha na hora que você escolher.", true),
    Perk(Icons.Rounded.DarkMode, "Modo escuro em todos os sites", "Sites claros ficam escuros no navegador.", true),
    Perk(Icons.Rounded.Lock, "Abas privadas com senha", "A navegação privada só abre com o PIN do cofre.", true),
    Perk(Icons.Rounded.Shield, "Alerta de invasão no cofre", "Veja dia e hora de cada vez que alguém errou o PIN.", true),
)

/** Quantos temas exclusivos cada plano libera. */
private fun themesOf(plan: Plan) = Wallpapers.all.filter { PlayBilling.planUnlocks(plan, it) }

/** Tela dos planos MusiBox Go, Plus e Pro (pagamento pela Google Play). */
@Composable
fun ProScreen(nav: NavController) {
    val context = LocalContext.current
    val state by PlayBilling.pro.collectAsStateWithLifecycle()
    val prices by PlayBilling.prices.collectAsStateWithLifecycle()
    val snack = rememberSnack()
    var selected by rememberSaveable { mutableStateOf(ProPlan.PRO) }
    val current = state.plan
    val active = current != Plan.NONE

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Planos MusiBox", onBack = { nav.popBackStack() })
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
            Text("Go, Plus ou Pro", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            Text(
                "Todos liberam os recursos extras. O Plus traz mais recursos e o Pro tem tudo, com 35 temas exclusivos.",
                color = Neo.Text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(18.dp))
            if (state.owner) {
                StatusCard(Icons.Rounded.CheckCircle, "Conta do dono ✓", "Acesso total: todos os temas e o Pro sem limite.", highlight = true)
                Spacer(Modifier.height(8.dp))
            } else if (active) {
                StatusCard(
                    Icons.Rounded.CheckCircle,
                    "Seu plano: ${current.label} ✓",
                    when {
                        state.subscription && state.renews -> "Assinatura ativa • renova sozinha"
                        state.subscription -> "Assinatura cancelada • vale até o fim do período"
                        else -> "Go • vale até ${fmtDate(state.passUntil)}"
                    },
                    highlight = true,
                )
                if (state.subscription) {
                    TextButton(onClick = { runCatching { context.startActivity(PlayBilling.manageSubscriptionIntent(context)) } }) {
                        Text("Gerenciar ou cancelar assinatura", color = Neo.Green)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text("Escolha seu plano", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ProPlan.entries.reversed().forEach { plan ->
                PlanCard(
                    plan = plan,
                    price = prices[plan.priceKey] ?: plan.fallbackPrice,
                    selected = plan == selected,
                    themes = themesOf(plan.tier).size,
                    badge = when (plan) {
                        ProPlan.PRO -> "Tem tudo"
                        ProPlan.GO -> "Para testar"
                        else -> null
                    },
                ) { selected = plan }
                Spacer(Modifier.height(10.dp))
            }

            // Temas que o plano escolhido libera
            val list = themesOf(selected.tier)
            Text(
                "Temas do ${selected.title} (${list.size})",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
            )
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(list.size, key = { list[it].id }) { k ->
                    val w = list[k]
                    Box(
                        Modifier
                            .size(width = 76.dp, height = 150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .bounceClick(pressed = 0.94f, shape = RoundedCornerShape(12.dp)) { nav.navigate(Routes.wallpaper(w.id)) },
                    ) {
                        com.musibox.app.ui.components.WallpaperImage(w, Modifier.fillMaxSize(), thumbnail = true)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("O que cada plano tem", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PlanTable()
            Spacer(Modifier.height(16.dp))
            Text("Recursos extras (em todos os planos)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
            perks.forEach { Benefit(it) }

            Spacer(Modifier.height(10.dp))
            Text(
                "Pagamento seguro pela Google Play (cartão, Pix ou saldo do Google Play). Plus e Pro renovam sozinhos até você " +
                    "cancelar na Play Store; o Go vale 3 dias e não renova. O plano fica ligado à sua conta Google.",
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
            val price = prices[selected.priceKey] ?: selected.fallbackPrice
            GradientButton(
                if (selected.basePlanId == null) "Comprar ${selected.title} • $price" else "Assinar ${selected.title} • $price${selected.period}",
                onClick = {
                    val activity = context.findActivity()
                    if (activity == null) {
                        snack("Não foi possível abrir a Google Play.")
                    } else {
                        PlayBilling.buyPro(activity, selected) { snack(it) }
                    }
                },
                icon = Icons.Rounded.Lock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Linha da tabela: recurso e se Go, Plus e Pro têm (texto = quantidade). */
private data class Row3(val title: String, val go: String, val plus: String, val pro: String)

private val tableRows = listOf(
    Row3("Recursos extras de hoje", "✓", "✓", "✓"),
    Row3("Temas exclusivos", "5", "12", "35"),
    Row3("Velocidade da música 0,5x a 2x", "✓", "✓", "✓"),
    Row3("Transição suave entre músicas", "–", "✓", "✓"),
    Row3("Widget bonito na tela inicial", "–", "✓", "✓"),
    Row3("Volume mais alto que o máximo do celular", "–", "✓", "✓"),
    Row3("Criar toque de celular com trecho da música", "–", "–", "✓"),
    Row3("1 tema pago grátis por mês, à escolha", "–", "–", "✓"),
    Row3("Temas novos 1 semana antes de todo mundo", "–", "–", "✓"),
)

@Composable
private fun PlanTable() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(com.musibox.app.themes.AppTheme.card)
            .border(1.dp, com.musibox.app.themes.AppTheme.line, shape)
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("", modifier = Modifier.weight(1f))
            listOf("Go", "Plus", "Pro").forEach {
                Text(it, color = if (it == "Pro") Neo.Green else Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
            }
        }
        tableRows.forEachIndexed { i, r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (i % 2 == 0) Color.White.copy(alpha = 0.03f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 6.dp))
                listOf(r.go, r.plus, r.pro).forEach { v ->
                    Text(
                        v,
                        color = when (v) {
                            "–" -> Neo.Artist
                            else -> Neo.Green
                        },
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp),
                    )
                }
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
            .background(if (highlight) Neo.Green.copy(alpha = 0.14f) else com.musibox.app.themes.AppTheme.card)
            .border(1.dp, if (highlight) Neo.Green else com.musibox.app.themes.AppTheme.line, RoundedCornerShape(18.dp))
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
private fun Benefit(p: Perk) {
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
                .background(com.musibox.app.themes.AppTheme.card),
            contentAlignment = Alignment.Center,
        ) { Icon(p.icon, null, tint = Neo.Green, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (!p.ready) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "chegando",
                        color = Neo.Green,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Neo.Green, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(p.text, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanCard(plan: ProPlan, price: String, selected: Boolean, themes: Int, badge: String?, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Neo.Green else com.musibox.app.themes.AppTheme.line, label = "planBorder")
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(pressed = 0.97f, shape = shape, onClick = onClick)
            .background(if (selected) Neo.Green.copy(alpha = 0.10f) else com.musibox.app.themes.AppTheme.card)
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
            Text("$themes temas exclusivos", color = Neo.Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(plan.detail, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(price, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            if (plan.period.isNotBlank()) Text(plan.period, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}
