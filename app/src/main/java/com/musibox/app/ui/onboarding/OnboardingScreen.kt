package com.musibox.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.musibox.app.core.Permissions
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
import com.musibox.app.ui.account.LoginContent
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

private enum class Step { INTRO, LOGIN, PERMISSION, INDEXING }

/** Primeira abertura: funcionalidades → entrar (Google ou e-mail, opcional) → permissão → indexação. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.INTRO) }
    val afterIntro = if (container.auth.isConfigured && container.auth.currentUser == null) Step.LOGIN else Step.PERMISSION

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
                Step.INTRO -> IntroStep(onNext = { step = afterIntro })
                Step.LOGIN -> LoginContent(onDone = { step = Step.PERMISSION }, onSkip = {
                    scope.launch { container.settings.setLoginSkipped(true) }
                    step = Step.PERMISSION
                })
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
            "Suas músicas em um só lugar", "Toque as músicas do aparelho num player completo, em segundo plano e sem internet."),
        Slide(Icons.Rounded.Download, Brush.linearGradient(listOf(Color(0xFFFF5A4E), Color(0xFFE0201C))),
            "Baixe de qualquer site", "YouTube, TikTok, Instagram, Kwai e outros: compartilhe o link ou use o navegador do app e toque em Baixar."),
        Slide(Icons.Rounded.Lock, Brush.linearGradient(listOf(MbPalette.Purple, Color(0xFF4F5BFF))),
            "Cofre privado", "Fotos, vídeos e arquivos criptografados, protegidos por PIN, digital e código de recuperação."),
        Slide(Icons.Rounded.CloudSync, Brush.linearGradient(listOf(Color(0xFF2ECC71), Color(0xFF1E8BFF))),
            "Sua conta guarda tudo", "Entre com o Google e suas playlists, favoritos e configurações voltam até depois de reinstalar."),
    )
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        MusiLogo(iconSize = 30.dp)
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val slide = slides[page]
            // Transição em "cubo 3D" entre as páginas
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        cameraDistance = 18f * density
                        transformOrigin = TransformOrigin(if (offset < 0) 0f else 1f, 0.5f)
                        rotationY = (-offset * 70f).coerceIn(-90f, 90f)
                        alpha = 1f - (kotlin.math.abs(offset) * 0.6f).coerceAtMost(1f)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                HoloOrb(slide)
                Spacer(Modifier.height(40.dp))
                Text(
                    slide.title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    slide.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(width = if (pager.currentPage == i) 26.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) Mb.colors.accentGradient else Brush.linearGradient(listOf(Mb.colors.border, Mb.colors.border))),
                )
            }
        }
        GradientButton(
            if (pager.currentPage < slides.lastIndex) "Próximo" else "Começar",
            onClick = {
                if (pager.currentPage < slides.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onNext()
            },
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        )
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Pular", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Orbe "holográfico" em 3D: esfera com brilho, anel inclinado e o ícone da funcionalidade. */
@Composable
private fun HoloOrb(slide: Slide) {
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            // halo
            drawCircle(Brush.radialGradient(listOf(Color(0x551EC8FF), Color.Transparent), radius = r), radius = r)
        }
        // anel inclinado (perspectiva)
        Canvas(
            Modifier
                .size(210.dp)
                .graphicsLayer { rotationX = 68f; cameraDistance = 12f * density },
        ) {
            drawCircle(Brush.sweepGradient(listOf(MbPalette.BlueBright, MbPalette.Purple, Color.Transparent, MbPalette.BlueBright)), style = Stroke(width = 6.dp.toPx()))
        }
        // esfera com sombreamento (luz no alto à esquerda)
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(slide.brush),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.32f, size.height * 0.28f),
                        radius = size.minDimension * 0.55f,
                    ),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        center = Offset(size.width * 0.4f, size.height * 0.35f),
                        radius = size.minDimension * 0.75f,
                    ),
                )
            }
            Icon(slide.icon, null, tint = Color.White, modifier = Modifier.size(62.dp))
        }
    }
}

@Composable
private fun PermissionStep(onGranted: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= 33 && !Permissions.hasNotifications(context)) {
            runCatching { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
    val permission = rememberAudioPermission(onGranted = { askNotifications(); onGranted() })
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
