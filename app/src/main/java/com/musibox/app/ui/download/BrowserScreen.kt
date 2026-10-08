package com.musibox.app.ui.download

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.musibox.app.download.LinkUtils
import com.musibox.app.ui.components.appViewModel
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.theme.Mb

/**
 * Navegador interno (como no Snaptube): o usuário navega no site e, ao abrir um vídeo ou música,
 * aparece o botão "Baixar". Não usa login nem cookies do usuário no download.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(nav: NavController, startUrl: String) {
    val vm = appViewModel { LinkDownloadViewModel(it) }
    val snack = rememberSnack()
    val focus = LocalFocusManager.current
    var currentUrl by rememberSaveable { mutableStateOf(startUrl) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    val isMedia = LinkUtils.isMediaPage(currentUrl)

    fun startDownload(url: String) {
        vm.analyze(url)
        showSheet = true
    }

    BackHandler(enabled = editing || canGoBack) {
        if (editing) {
            editing = false
            focus.clearFocus()
        } else {
            webView?.goBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                it.stopLoading()
                it.onPause()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Barra de endereço
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.Rounded.Close, "Fechar navegador") { nav.popBackStack() }
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(Mb.colors.card)
                    .border(1.dp, if (editing) MaterialTheme.colorScheme.primary else Mb.colors.border, RoundedCornerShape(23.dp))
                    .clickable {
                        address = currentUrl
                        editing = true
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (currentUrl.startsWith("https")) Icons.Rounded.Lock else Icons.Rounded.Public,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                if (editing) {
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val text = address.trim()
                            if (text.isNotEmpty()) {
                                val target = if (LinkUtils.looksLikeAddress(text)) LinkUtils.normalizeAddress(text)
                                else "https://m.youtube.com/results?search_query=${Uri.encode(text)}"
                                webView?.loadUrl(target)
                            }
                            editing = false
                            focus.clearFocus()
                        }),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title.ifBlank { LinkUtils.hostOf(currentUrl) },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            LinkUtils.hostOf(currentUrl),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            BarButton(Icons.Rounded.Refresh, "Recarregar") { webView?.reload() }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = false
                        // Sem "; wv": os sites mostram a versão móvel normal.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                val scheme = uri.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                if (scheme == "intent") {
                                    // Links "abrir no app": usa o endereço web alternativo, se houver.
                                    val fallback = runCatching {
                                        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                                    }.getOrNull()
                                    if (!fallback.isNullOrBlank()) view.loadUrl(fallback)
                                }
                                return true // bloqueia esquemas de outros apps (vnd.youtube://, etc.)
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                url?.let { currentUrl = it }
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                url?.let { currentUrl = it }
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                url?.let { currentUrl = it }
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView, t: String?) {
                                title = t.orEmpty()
                            }
                        }
                        setDownloadListener { url, _, _, _, _ ->
                            // Link direto para arquivo (mp3, mp4…): baixa pelo MusiBox.
                            startDownload(url)
                        }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )

            // Dica quando não há vídeo aberto
            AnimatedVisibility(
                !isMedia && progress >= 100,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.TouchApp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abra um vídeo ou música para baixar", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Botão flutuante "Baixar"
            AnimatedVisibility(
                isMedia,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                val pulse = rememberInfiniteTransition(label = "fab")
                val s by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "fabScale")
                Row(
                    Modifier
                        .scale(s)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Mb.colors.accentGradient)
                        .clickable { startDownload(currentUrl) }
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Baixar", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Barra inferior de navegação do navegador
        Row(
            Modifier
                .fillMaxWidth()
                .background(Mb.colors.card)
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", enabled = canGoBack) { webView?.goBack() }
            Spacer(Modifier.weight(1f))
            BarButton(Icons.Rounded.Home, "Início do site") { webView?.loadUrl(startUrl) }
            Spacer(Modifier.weight(1f))
            BarButton(Icons.AutoMirrored.Rounded.ArrowForward, "Avançar", enabled = canGoForward) { webView?.goForward() }
        }
    }

    if (showSheet) {
        DownloadSheet(vm, onDismiss = { showSheet = false }, onMessage = snack)
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}
