package com.musibox.app.ui.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WorkspacePremium
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.MainActivity
import com.musibox.app.billing.Plan
import com.musibox.app.billing.PlayBilling
import com.musibox.app.billing.ProPlan
import com.musibox.app.core.findActivity
import com.musibox.app.themes.AppTheme
import com.musibox.app.themes.Wallpapers
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.WallpaperImage
import com.musibox.app.ui.components.bounceClick
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
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

/** Um benefício do plano (ícone, título e explicação curta). */
private data class Perk(val icon: ImageVector, val title: String, val text: String)

/** Recursos extras que todos os planos têm. */
private val extras = listOf(
    Perk(Icons.Rounded.Block, "Bloqueador de anúncios", "Navegue sem propagandas e rastreadores."),
    Perk(Icons.Rounded.Equalizer, "Equalizador", "Estilos (pop, rock, funk…), graves e som 3D."),
    Perk(Icons.Rounded.Bedtime, "Timer de sono", "A música para sozinha na hora que você escolher."),
    Perk(Icons.Rounded.DarkMode, "Sites escuros", "Sites claros ficam escuros no navegador."),
    Perk(Icons.Rounded.Lock, "Abas privadas com PIN", "A navegação privada só abre com o PIN do cofre."),
    Perk(Icons.Rounded.TheaterComedy, "Ícone disfarçado", "O MusiBox aparece como Calculadora, Notas, Relógio ou Clima."),
    Perk(Icons.Rounded.Shield, "Alerta de invasão", "Veja quando alguém errou o PIN do cofre."),
)

/** O que cada plano oferece (só o que vem nele). */
private fun perksOf(plan: ProPlan, themes: Int): List<Perk> {
    val list = mutableListOf(
        Perk(Icons.Rounded.Wallpaper, "$themes temas exclusivos", "Temas que não se compram, só no plano."),
        Perk(Icons.Rounded.Speed, "Velocidade da música", "Toque de 0,5x a 2x."),
    )
    if (plan != ProPlan.GO) {
        list += Perk(Icons.Rounded.Tune, "Transição suave", "Uma música entra devagar na outra.")
        list += Perk(Icons.Rounded.Widgets, "Widget na tela inicial", "Capa, música e botões direto na tela do celular.")
        list += Perk(Icons.AutoMirrored.Rounded.VolumeUp, "Volume extra", "Mais alto que o máximo do celular.")
    }
    if (plan == ProPlan.PRO) {
        list += Perk(Icons.Rounded.NotificationsActive, "Criar toque de celular", "Escolha um trecho da música e use como toque.")
        list += Perk(Icons.Rounded.CardGiftcard, "1 tema pago grátis por mês", "Você escolhe qual tema quer ganhar.")
        list += Perk(Icons.Rounded.NewReleases, "Temas novos antes", "Use os lançamentos 1 semana antes de todo mundo.")
    }
    return list
}

/** Temas exclusivos que o plano libera. */
private fun themesOf(plan: Plan) = Wallpapers.all.filter { PlayBilling.planUnlocks(plan, it) }

/** Tela dos planos: escolha Go, Plus ou Pro e veja só o que vem naquele plano. */
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
        ) {
            if (state.owner) {
                StatusCard("Conta do dono ✓", "Acesso total: todos os temas e o Pro sem limite.")
                Spacer(Modifier.height(14.dp))
            } else if (active) {
                StatusCard(
                    "Seu plano: ${current.label} ✓",
                    when {
                        state.subscription && state.renews -> "Assinatura ativa • renova sozinha"
                        state.subscription -> "Assinatura cancelada • vale até o fim do período"
                        else -> "Go • vale até ${fmtDate(state.passUntil)}"
                    },
                )
                if (state.subscription) {
                    TextButton(onClick = { runCatching { context.startActivity(PlayBilling.manageSubscriptionIntent(context)) } }) {
                        Text("Gerenciar ou cancelar assinatura", color = Neo.Green)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Seletor: Go • Plus • Pro
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(AppTheme.card)
                    .border(1.dp, AppTheme.line, RoundedCornerShape(28.dp))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf(ProPlan.GO, ProPlan.PLUS, ProPlan.PRO).forEach { p ->
                    val sel = p == selected
                    val bg by animateColorAsState(if (sel) Neo.Green else Color.Transparent, label = "planTab")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(bg)
                            .bounceClick(pressed = 0.94f, shape = RoundedCornerShape(23.dp)) { selected = p },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(p.title, color = if (sel) Neo.OnGreen else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            AnimatedContent(targetState = selected, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "plano") { plan ->
                val themes = themesOf(plan.tier)
                val price = prices[plan.priceKey] ?: plan.fallbackPrice
                Column {
                    PlanHero(plan, price)
                    Spacer(Modifier.height(18.dp))
                    Text("O que vem no ${plan.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    perksOf(plan, themes.size).forEach { PerkRow(it, highlight = true) }
                    Spacer(Modifier.height(14.dp))
                    Text("Temas do ${plan.title} (${themes.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(themes.size, key = { themes[it].id }) { k ->
                            val w = themes[k]
                            Box(
                                Modifier
                                    .size(width = 80.dp, height = 160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .bounceClick(pressed = 0.94f, shape = RoundedCornerShape(12.dp)) { nav.navigate(Routes.wallpaper(w.id)) },
                            ) { WallpaperImage(w, Modifier.fillMaxSize(), thumbnail = true) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("E também todos os recursos extras", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    extras.forEach { PerkRow(it, highlight = false) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Pagamento seguro pela Google Play (cartão, Pix ou saldo do Google Play). Plus e Pro renovam sozinhos até você " +
                    "cancelar na Play Store; o Go vale 3 dias e não renova. O plano fica ligado à sua conta Google.",
                color = Neo.Artist,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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
                    if (activity == null) snack("Não foi possível abrir a Google Play.") else PlayBilling.buyPro(activity, selected) { snack(it) }
                },
                icon = Icons.Rounded.WorkspacePremium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlanHero(plan: ProPlan, price: String) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neo.Green.copy(alpha = 0.30f), AppTheme.accent2.copy(alpha = 0.14f), AppTheme.card)))
            .border(1.dp, Neo.Green.copy(alpha = 0.6f), shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Neo.Green),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.WorkspacePremium, null, tint = Neo.OnGreen, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("MusiBox ${plan.title}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(
                    when (plan) {
                        ProPlan.GO -> "Para testar por 3 dias"
                        ProPlan.PLUS -> "Mais recursos, toda semana"
                        ProPlan.PRO -> "Tem tudo do MusiBox"
                    },
                    color = Neo.Text2,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(price, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
            Text(plan.period, color = Neo.Text2, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Text(plan.detail, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusCard(title: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Neo.Green.copy(alpha = 0.14f))
            .border(1.dp, Neo.Green, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Neo.Green, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PerkRow(p: Perk, highlight: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (highlight) Neo.Green.copy(alpha = 0.18f) else AppTheme.card),
            contentAlignment = Alignment.Center,
        ) { Icon(p.icon, null, tint = Neo.Green, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(p.text, color = Neo.Text2, style = MaterialTheme.typography.bodySmall)
        }
        if (highlight) Icon(Icons.Rounded.CheckCircle, null, tint = Neo.Green, modifier = Modifier.size(20.dp))
    }
}
