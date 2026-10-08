package com.musibox.app.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import android.provider.MediaStore
import coil.Coil
import com.musibox.app.core.Permissions
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.db.DownloadStatus
import com.musibox.app.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageInfo(
    val music: Long?,
    val videos: Long?,
    val images: Long?,
    val vault: Long,
    val downloads: Long,
    val cache: Long,
    val appTotal: Long?,
)

/** Calcula o espaço usado e limpa somente o cache (nunca arquivos pessoais). */
class StorageRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val vault: VaultRepository,
) {
    suspend fun load(): StorageInfo = withContext(Dispatchers.IO) {
        val music = if (Permissions.hasAudio(context)) sumMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) else null
        val videos = if (Permissions.hasVideos(context)) sumMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI) else null
        val images = if (Permissions.hasImages(context)) sumMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI) else null
        val appTotal = runCatching {
            val ssm = context.getSystemService(StorageStatsManager::class.java)
            val stats = ssm.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())
            stats.appBytes + stats.dataBytes
        }.getOrNull()
        StorageInfo(
            music = music,
            videos = videos,
            images = images,
            vault = vault.totalSize(),
            downloads = db.downloadDao().completedSize(),
            cache = dirSize(context.cacheDir) + (context.externalCacheDir?.let { dirSize(it) } ?: 0L),
            appTotal = appTotal,
        )
    }

    private fun sumMedia(uri: android.net.Uri): Long? = runCatching {
        var total = 0L
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            val col = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (c.moveToNext()) total += c.getLong(col)
        }
        total
    }.getOrNull()

    private fun dirSize(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Limpa arquivos temporários e o cache de imagens.
     * Preserva downloads em andamento/pausados (para poderem continuar). Retorna os bytes liberados.
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val keep = db.downloadDao().unfinished().mapTo(HashSet()) { it.id }
        val before = dirSize(context.cacheDir) + (context.externalCacheDir?.let { dirSize(it) } ?: 0L)
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.name == "dl") {
                f.listFiles()?.forEach { d -> if (d.name !in keep) d.deleteRecursively() }
            } else {
                f.deleteRecursively()
            }
        }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        runCatching {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        val after = dirSize(context.cacheDir) + (context.externalCacheDir?.let { dirSize(it) } ?: 0L)
        (before - after).coerceAtLeast(0)
    }

    @Suppress("unused")
    private fun isActive(status: String) =
        status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED || status == DownloadStatus.PAUSED
}
