package com.musibox.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musibox.app.core.Fmt
import com.musibox.app.core.findActivity
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MusiLogo
import com.musibox.app.ui.components.rememberAudioPermission
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.settings.startGoogleSignIn
import com.musibox.app.ui.theme.Mb
import com.musibox.app.ui.theme.MbPalette
import kotlinx.coroutines.launch

private data class Slide(val icon: ImageVector, val brush: Brush, val title: String, val text: String)

private enum class Step { INTRO, ACCOUNT, PERMISSION, INDEXING }

/** Primeira abertura: apresentação → conta (opcional) → permissão de músicas → indexação. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.INTRO) }

    val finish: () -> Unit = {
        scope.launch {
            container.settings.setOnboardingDone(true)
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { s ->
            when (s) {
                Step.INTRO -> IntroStep(onNext = { step = if (container.auth.isConfigured) Step.ACCOUNT else Step.PERMISSION })
                Step.ACCOUNT -> AccountStep(onNext = { step = Step.PERMISSION })
                Step.PERMISSION -> PermissionStep(onGranted = { step = Step.INDEXING }, onSkip = finish)
                Step.INDEXING -> IndexingStep(onDone = finish)
            }
        }
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    val slides = listOf(
        Slide(Icons.Rounded.Headphones, Brush.linearGradient(listOf(MbPalette.BlueBright, MbPalette.BlueDeep)),
            "Suas músicas em um só lugar", "Ouça as músicas do seu aparelho com um player completo, em segundo plano e sem internet."),
        Slide(Icons.Rounded.Download, Brush.linearGradient(listOf(Color(0xFFFF5A4E), Color(0xFFE0201C))),
            "Baixe pelo Compartilhar", "No YouTube, TikTok ou Instagram, toque em Compartilhar e escolha \"Baixar com MusiBox\"."),
        Slide(Icons.Rounded.Lock, Brush.linearGradient(listOf(MbPalette.Purple, Color(0xFF4F5BFF))),
            "Cofre privado", "Guarde fotos, vídeos e arquivos criptografados, protegidos por PIN ou biometria."),
    )
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        MusiLogo(iconSize = 30.dp)
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val slide = slides[page]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .background(slide.brush),
                    contentAlignment = Alignment.Center,
                ) { Icon(slide.icon, null, tint = Color.White, modifier = Modifier.size(76.dp)) }
                Spacer(Modifier.height(36.dp))
                Text(slide.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(slide.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(width = if (pager.currentPage == i) 22.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else Mb.colors.border),
                )
            }
        }
        GradientButton(
            if (pager.currentPage < slides.lastIndex) "Próximo" else "Começar",
            onClick = {
                if (pager.currentPage < slides.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onNext()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AccountStep(onNext: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Mb.colors.accentGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CloudSync, null, tint = Color.White, modifier = Modifier.size(60.dp)) }
        Spacer(Modifier.height(30.dp))
        Text("Proteja e sincronize seus dados", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(
            "Entre com sua Conta Google para manter configurações, playlists, favoritos e outros dados compatíveis sincronizados. " +
                "Assim você poderá recuperá-los ao trocar de celular ou reinstalar o aplicativo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "O login é opcional. Músicas, fotos e vídeos continuam no aparelho e o cofre nunca é enviado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Mb.colors.warning, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(36.dp))
        GradientButton(
            "Continuar com Google",
            onClick = {
                busy = true
                scope.launch {
                    val ok = startGoogleSignIn(container, context.findActivity()) { msg -> message = msg }
                    busy = false
                    if (ok) {
                        snack(message ?: "Conectado.")
                        onNext()
                    }
                }
            },
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNext, enabled = !busy) { Text("Continuar sem conta") }
    }
}

@Composable
private fun PermissionStep(onGranted: () -> Unit, onSkip: () -> Unit) {
    val permission = rememberAudioPermission(onGranted = onGranted)
    LaunchedEffect(permission.granted) { if (permission.granted) onGranted() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MbPalette.Pink, MbPalette.Red))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(60.dp)) }
        Spacer(Modifier.height(30.dp))
        Text("Acesso às suas músicas", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(
            "Para montar sua biblioteca, o MusiBox precisa ler os arquivos de áudio do aparelho (MP3, M4A, FLAC, OGG, OPUS e outros). " +
                "Ele não acessa suas fotos nem envia nada para a internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (permission.permanentlyDenied) {
            Spacer(Modifier.height(12.dp))
            Text(
                "A permissão foi negada. Sem ela a aba Músicas fica indisponível, mas downloads e cofre funcionam normalmente.",
                color = Mb.colors.warning,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(36.dp))
        GradientButton(
            if (permission.permanentlyDenied) "Abrir configurações" else "Permitir acesso",
            onClick = if (permission.permanentlyDenied) permission.openSettings else permission.request,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip) { Text("Agora não") }
    }
}

@Composable
private fun IndexingStep(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    var count by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) { count = container.music.rescan() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (count < 0) {
            CircularProgressIndicator(Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("Procurando suas músicas…", style = MaterialTheme.typography.titleLarge)
        } else {
            Icon(Icons.Rounded.CheckCircle, null, tint = Mb.colors.success, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                if (count == 0) "Nenhuma música encontrada." else "${Fmt.plural(count, "música encontrada", "músicas encontradas")}!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (count == 0) "Você pode baixar músicas ou copiar arquivos para o aparelho a qualquer momento."
                else "Tudo pronto para tocar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            GradientButton("Ir para o início", onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}
