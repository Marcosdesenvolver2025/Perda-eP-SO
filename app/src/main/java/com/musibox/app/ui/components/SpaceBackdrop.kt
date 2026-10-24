package com.musibox.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.musibox.app.themes.AppTheme
import com.musibox.app.themes.Wallpaper

/**
 * Fundo do app: preto liso, ou o papel de parede escolhido (com uma camada escura para o texto
 * ficar legível). Na página do navegador aparece o papel do navegador, trocando suavemente
 * durante o deslize (browserAmount = 0..1).
 */
@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier, browserAmount: () -> Float = { 0f }) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Crossfade(AppTheme.appWallpaper, animationSpec = tween(450), label = "appWall") { w ->
            if (w != null) {
                Box(Modifier.fillMaxSize()) {
                    WallpaperImage(w, Modifier.fillMaxSize())
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            // camada escura tingida com a cor do tema (o app inteiro combina com o papel)
                            .background(Brush.verticalGradient(listOf(AppTheme.bg.copy(alpha = 0.52f), AppTheme.bg.copy(alpha = 0.64f), AppTheme.bg.copy(alpha = 0.82f)))),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = browserAmount().coerceIn(0f, 1f) },
        ) {
            Crossfade(AppTheme.browserWallpaper, animationSpec = tween(450), label = "browserWall") { w ->
                Box(Modifier.fillMaxSize()) {
                    WallpaperImage(w, Modifier.fillMaxSize())
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.25f),
                                    0.2f to Color.Transparent,
                                    0.75f to Color.Black.copy(alpha = 0.10f),
                                    1f to Color.Black.copy(alpha = 0.55f),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

/** Imagem do papel de parede (vem dos arquivos do app, sem internet). */
@Composable
fun WallpaperImage(w: Wallpaper, modifier: Modifier = Modifier, thumbnail: Boolean = false) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data(w.assetUri)
            .apply { if (thumbnail) size(360, 780) }
            .crossfade(false)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
