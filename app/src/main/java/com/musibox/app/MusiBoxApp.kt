package com.musibox.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
        container = AppContainer(this)
        container.start()
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
