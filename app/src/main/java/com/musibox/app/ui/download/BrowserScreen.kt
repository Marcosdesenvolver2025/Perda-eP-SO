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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.download.LinkUtils
import com.musibox.app.ui.components.Artwork
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
    var imageToSave by remember { mutableStateOf<String?>(null) }
    val isMedia = LinkUtils.isMediaPage(currentUrl)

    fun startDownload(url: String, videoHint: String? = null) {
        vm.analyze(url, videoHint = videoHint)
        showSheet = true
    }

    // Procura o vídeo que está na página (para sites que o motor não conhece, como o Kwai).
    fun downloadCurrentPage() {
        val view = webView
        if (view == null) {
            startDownload(currentUrl)
            return
        }
        view.evaluateJavascript(FIND_VIDEO_JS) { result ->
            val src = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()
            startDownload(currentUrl, src?.takeIf { it.startsWith("http") })
        }
    }

    fun openSite(url: String) {
        editing = false
        webView?.loadUrl(url)
    }

    BackHandler(enabled = editing || canGoBack) {
        if (editing) {
            editing = false
            focus.clearFocus()
        } else {
            webView?.goBack()
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
        // Abas rápidas dos sites
        val host = LinkUtils.hostOf(currentUrl)
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            items(DownloadSites.size) { i ->
                val site = DownloadSites[i]
                val selected = host.isNotEmpty() && LinkUtils.hostOf(site.url).let { h -> host == h || host.endsWith(".$h") }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Mb.colors.card)
                        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Mb.colors.border, RoundedCornerShape(18.dp))
                        .clickable { openSite(site.url) }
                        .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteIcon(site, 24.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(site.name, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
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
                        // Toque longo numa imagem: salvar na galeria ou no cofre (como no Snaptube).
                        setOnLongClickListener {
                            val hit = hitTestResult
                            val img = hit.extra
                            if ((hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) &&
                                img != null && img.startsWith("http")
                            ) {
                                imageToSave = img
                                true
                            } else {
                                false
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

            // Botão flutuante "Baixar" (fica ativo e pulsando quando a página tem um vídeo)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 84.dp),
            ) {
                if (isMedia) {
                    val pulse = rememberInfiniteTransition(label = "fab")
                    val sc by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "fabScale")
                    DownloadFab(active = true, modifier = Modifier.scale(sc)) { downloadCurrentPage() }
                } else {
                    DownloadFab(active = false) { downloadCurrentPage() }
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
    imageToSave?.let { img ->
        SaveImageDialog(
            imageUrl = img,
            onSave = { dest ->
                imageToSave = null
                vm.enqueueImage(img, currentUrl, title, dest, snack)
            },
            onDismiss = { imageToSave = null },
        )
    }
}

@Composable
private fun DownloadFab(active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .then(
                if (active) Modifier.background(Mb.colors.accentGradient)
                else Modifier.background(Color.Black.copy(alpha = 0.72f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (active) 22.dp else 16.dp, vertical = if (active) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(if (active) 24.dp else 20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Baixar", color = Color.White, style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
    }
}

/** Devolve o endereço do vídeo da página (ignora "blob:"), ou o og:video. */
private const val FIND_VIDEO_JS = """
(function(){
  try {
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) {
      var v = vs[i];
      var s = v.currentSrc || v.src || '';
      if (!s) { var so = v.querySelector('source'); if (so) s = so.src || ''; }
      if (s && s.indexOf('http') === 0) return s;
    }
    var m = document.querySelector('meta[property="og:video:secure_url"],meta[property="og:video:url"],meta[property="og:video"]');
    if (m && m.content && m.content.indexOf('http') === 0) return m.content;
  } catch (e) {}
  return '';
})();
"""

@Composable
private fun SaveImageDialog(imageUrl: String, onSave: (SaveDestination) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salvar imagem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Artwork(
                    imageUrl,
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    corner = 14.dp,
                    fallbackIcon = Icons.Rounded.Image,
                    contentScale = ContentScale.Fit,
                )
                SaveChoice(Icons.Rounded.PhoneAndroid, "Salvar na galeria", "Fica visível no aparelho", MaterialTheme.colorScheme.primary) {
                    onSave(SaveDestination.GALLERY)
                }
                SaveChoice(Icons.Rounded.Lock, "Salvar no cofre", "Privado e criptografado", Mb.colors.vault) {
                    onSave(SaveDestination.VAULT)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Mb.colors.cardHigh,
    )
}

@Composable
private fun SaveChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Mb.colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
