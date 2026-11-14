package com.musibox.app.themes

import android.app.WallpaperManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coloca um papel de parede do MusiBox na tela inicial e/ou de bloqueio do celular. */
object DeviceWallpaper {
    enum class Target(val label: String, val flags: Int) {
        HOME("Tela inicial", WallpaperManager.FLAG_SYSTEM),
        LOCK("Tela de bloqueio", WallpaperManager.FLAG_LOCK),
        BOTH("As duas", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
    }

    /** true se deu certo. Lê a imagem do próprio app (assets), nada sai do aparelho. */
    suspend fun apply(context: Context, w: Wallpaper, target: Target): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) return@runCatching false
            context.assets.open("wallpapers/${w.id}.webp").use { input ->
                wm.setStream(input, null, true, target.flags)
            }
            true
        }.getOrDefault(false)
    }
}
