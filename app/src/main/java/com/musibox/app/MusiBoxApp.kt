package com.musibox.app

import android.app.Application
import coil.ImageLoader
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
