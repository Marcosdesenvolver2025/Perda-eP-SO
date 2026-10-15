package com.musibox.app.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musibox.app.core.findActivity
import com.musibox.app.sync.SignInResult
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MusiLogo
import com.musibox.app.ui.theme.Mb
import com.musibox.app.ui.theme.MbPalette
import kotlinx.coroutines.launch

private enum class LoginStage { START, PASSWORD, RESET_SENT }

/**
 * Tela de entrada: "Continuar com Google" ou e-mail (entrar / criar conta / esqueci a senha).
 * Ao entrar, os dados da conta (playlists, favoritos, histórico, configurações) voltam sozinhos,
 * mesmo depois de reinstalar o app.
 */
@Composable
fun LoginContent(
    onDone: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable { mutableStateOf(LoginStage.START) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    suspend fun handle(r: SignInResult) {
        when (r) {
            is SignInResult.Success -> {
                busy = "Preparando sua conta…"
                runCatching { container.sync.onSignedIn(r.user) }
                busy = null
                onDone()
            }
            SignInResult.Canceled -> busy = null
            is SignInResult.Error -> {
                busy = null
                message = r.message
            }
        }
    }

    val legacyGoogle = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        scope.launch { handle(container.auth.finishLegacyGoogle(res.data)) }
    }

    fun google() {
        val activity = context.findActivity() ?: return
        message = null
        busy = "Entrando com o Google…"
        scope.launch {
            val r = container.auth.signIn(activity)
            if (container.auth.shouldTryLegacy(r)) {
                runCatching { legacyGoogle.launch(container.auth.legacyGoogleIntent(activity)) }
                    .onFailure { handle(r) }
            } else {
                handle(r)
            }
        }
    }

    val emailValid = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]{2,}$""").matches(email.trim())

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLogo) {
                MusiLogo(iconSize = 34.dp)
                Spacer(Modifier.height(28.dp))
            }
            SoundIllustration(Modifier.size(210.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                "Sua música com você,\nem qualquer aparelho",
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(28.dp))

            AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "login") { st ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (st) {
                        LoginStage.START -> {
                            PillButton(
                                onClick = { google() },
                                enabled = busy == null,
                                background = MaterialTheme.colorScheme.onBackground,
                            ) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center,
                                ) { Text("G", color = MbPalette.Blue, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Continuar com Google",
                                    color = MaterialTheme.colorScheme.background,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                HorizontalDivider(Modifier.weight(1f), color = Mb.colors.border)
                                Text("OU", Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(Modifier.weight(1f), color = Mb.colors.border)
                            }
                            Spacer(Modifier.height(18.dp))
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim(); message = null },
                                placeholder = { Text("Digite seu e-mail", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { if (emailValid) stage = LoginStage.PASSWORD }),
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Mb.colors.border,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.fillMaxWidth().height(58.dp),
                            )
                            if (emailValid) {
                                Spacer(Modifier.height(12.dp))
                                PillButton(onClick = { message = null; stage = LoginStage.PASSWORD }, enabled = busy == null, brush = Mb.colors.accentGradient) {
                                    Icon(Icons.Rounded.Email, null, tint = Mb.colors.onAccent)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Continuar com e-mail", color = Mb.colors.onAccent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        LoginStage.PASSWORD -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { stage = LoginStage.START; message = null; password = "" }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar")
                                }
                                Text(email, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Mb.colors.card)
                                    .padding(4.dp),
                            ) {
                                ModeTab(Modifier.weight(1f), "Entrar", !creating) { creating = false; message = null }
                                ModeTab(Modifier.weight(1f), "Criar conta", creating) { creating = true; message = null }
                            }
                            Spacer(Modifier.height(14.dp))
                            if (creating) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it.take(60) },
                                    label = { Text("Seu nome") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mb.colors.border),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; message = null },
                                label = { Text(if (creating) "Crie uma senha (mín. 6 caracteres)" else "Senha") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPassword) "Ocultar senha" else "Mostrar senha")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mb.colors.border),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            PillButton(
                                onClick = {
                                    focus.clearFocus()
                                    message = null
                                    busy = if (creating) "Criando sua conta…" else "Entrando…"
                                    scope.launch {
                                        handle(
                                            if (creating) container.auth.createAccount(name, email, password)
                                            else container.auth.signInWithEmail(email, password),
                                        )
                                    }
                                },
                                enabled = busy == null && password.length >= (if (creating) 6 else 1),
                                brush = Mb.colors.accentGradient,
                            ) {
                                Text(
                                    if (creating) "Criar conta" else "Entrar",
                                    color = Mb.colors.onAccent,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (!creating) {
                                TextButton(onClick = {
                                    message = null
                                    busy = "Enviando…"
                                    scope.launch {
                                        info = container.auth.sendPasswordReset(email)
                                        busy = null
                                        stage = LoginStage.RESET_SENT
                                    }
                                }, enabled = busy == null) { Text("Esqueci minha senha") }
                            }
                        }

                        LoginStage.RESET_SENT -> {
                            Text(info.orEmpty(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            PillButton(onClick = { stage = LoginStage.PASSWORD; creating = false }, brush = Mb.colors.accentGradient) {
                                Text("Voltar para entrar", color = Mb.colors.onAccent, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            busy?.let {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    it,
                    color = Mb.colors.danger,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mb.colors.danger.copy(alpha = 0.10f))
                        .padding(12.dp),
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Ao entrar, suas playlists, favoritos, histórico e configurações ficam salvos na sua conta e voltam " +
                    "sozinhos quando você entrar de novo, mesmo depois de reinstalar o app. Os arquivos do cofre nunca saem do aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onSkip != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSkip, enabled = busy == null) {
                    Text("Continuar sem conta", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    background: Color? = null,
    brush: Brush? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .height(58.dp)
        .clip(RoundedCornerShape(29.dp))
    val bg = when {
        brush != null -> base.background(brush)
        else -> base.background(background ?: MaterialTheme.colorScheme.primary)
    }
    Row(
        bg
            .then(if (enabled) Modifier else Modifier.background(Color.Black.copy(alpha = 0.35f)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun ModeTab(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ilustração própria em traço contínuo: fone de ouvido, onda sonora e um "sol" azul. */
@Composable
private fun SoundIllustration(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.028f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // "Sol" com degradê (cores do app)
        drawCircle(
            Brush.linearGradient(listOf(MbPalette.BlueBright, MbPalette.Purple), start = Offset(w * 0.62f, h * 0.05f), end = Offset(w * 0.86f, h * 0.30f)),
            radius = w * 0.11f,
            center = Offset(w * 0.74f, h * 0.17f),
        )

        // Arco do fone
        drawArc(
            color = ink,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.17f, h * 0.20f),
            size = Size(w * 0.66f, h * 0.62f),
            style = stroke,
        )
        // Conchas
        drawRoundRect(ink, topLeft = Offset(w * 0.12f, h * 0.48f), size = Size(w * 0.14f, h * 0.24f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = stroke)
        drawRoundRect(ink, topLeft = Offset(w * 0.74f, h * 0.48f), size = Size(w * 0.14f, h * 0.24f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = stroke)

        // Onda sonora que sai de uma concha e termina em espiral
        val wave = Path().apply {
            moveTo(w * 0.26f, h * 0.60f)
            cubicTo(w * 0.34f, h * 0.42f, w * 0.40f, h * 0.80f, w * 0.48f, h * 0.60f)
            cubicTo(w * 0.54f, h * 0.44f, w * 0.60f, h * 0.76f, w * 0.66f, h * 0.62f)
            cubicTo(w * 0.70f, h * 0.54f, w * 0.72f, h * 0.70f, w * 0.74f, h * 0.60f)
        }
        drawPath(wave, ink, style = stroke)
        val tail = Path().apply {
            moveTo(w * 0.19f, h * 0.72f)
            cubicTo(w * 0.20f, h * 0.90f, w * 0.40f, h * 0.96f, w * 0.52f, h * 0.88f)
            cubicTo(w * 0.62f, h * 0.81f, w * 0.56f, h * 0.74f, w * 0.50f, h * 0.79f)
        }
        drawPath(tail, ink, style = stroke)
        // Notinhas
        drawCircle(MbPalette.BlueBright, radius = w * 0.022f, center = Offset(w * 0.40f, h * 0.38f))
        drawCircle(MbPalette.PurpleBright, radius = w * 0.016f, center = Offset(w * 0.56f, h * 0.33f))
    }
}
