package com.musibox.app

import android.app.Application
import coil.ImageLoader
import kotlinx.coroutines.launch
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.musibox.app.media.AudioCoverFetcher
import com.musibox.app.media.AudioCoverKeyer
import com.musibox.app.vault.VaultImageFetcher
import com.musibox.app.vault.VaultImageKeyer
import com.musibox.app.vault.VaultThumbFetcher
import com.musibox.app.vault.VaultThumbKeyer

class MusiBoxApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        container = AppContainer(this)
        container.start()
        // Tema (papel de parede/cor) e compras pela Google Play
        com.musibox.app.themes.ThemeStore.get(this)
        com.musibox.app.billing.PlayBilling.start(this)
        com.musibox.app.media.AudioFx.init(this)
        // Widget da tela inicial acompanha o plano (Plus e Pro) e a cor do tema
        container.appScope.launch {
            kotlinx.coroutines.flow.combine(
                com.musibox.app.billing.PlayBilling.pro,
                androidx.compose.runtime.snapshotFlow { com.musibox.app.themes.AppTheme.paletteKey },
            ) { p, k -> p.plan to k }.collect {
                runCatching { com.musibox.app.media.MusicWidget.refresh(this@MusiBoxApp) }
            }
        }
        // Quando a Play confirma as compras: sem Pro, desfaz o ícone disfarçado; papel não comprado volta ao padrão
        container.appScope.launch {
            kotlinx.coroutines.flow.combine(
                com.musibox.app.billing.PlayBilling.synced,
                com.musibox.app.billing.PlayBilling.pro,
                com.musibox.app.billing.PlayBilling.owned,
                com.musibox.app.billing.PlayBilling.authKnown,
            ) { synced, _, _, auth -> synced && auth }.collect { synced ->
                if (!synced) return@collect
                val app = this@MusiBoxApp
                if (!com.musibox.app.billing.PlayBilling.isProNow()) {
                    runCatching { com.musibox.app.vault.IconDisguise.resetIfNeeded(app) }
                }
                com.musibox.app.themes.ThemeStore.get(app).revalidate { com.musibox.app.billing.PlayBilling.canUse(it) }
            }
        }
    }

    /**
     * Quando o google-services.json não tem uma entrada para este package, o Firebase é
     * iniciado aqui com os dados públicos do próprio arquivo (chave de API, ID do projeto).
     */
    private fun initFirebase() {
        if (!BuildConfig.FIREBASE_MANUAL_INIT || BuildConfig.FB_APP_ID.isBlank()) return
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FB_API_KEY)
                    .setApplicationId(BuildConfig.FB_APP_ID)
                    .setProjectId(BuildConfig.FB_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FB_SENDER_ID)
                    .apply { if (BuildConfig.FB_STORAGE_BUCKET.isNotBlank()) setStorageBucket(BuildConfig.FB_STORAGE_BUCKET) }
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(AudioCoverKeyer())
                add(AudioCoverFetcher.Factory())
                add(VaultImageKeyer())
                add(VaultThumbKeyer())
                add(VaultImageFetcher.Factory(container.vault))
                add(VaultThumbFetcher.Factory(container.vault))
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            // O cache em disco guarda só imagens da internet (miniaturas de vídeos), nunca itens do cofre.
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(80L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
}
