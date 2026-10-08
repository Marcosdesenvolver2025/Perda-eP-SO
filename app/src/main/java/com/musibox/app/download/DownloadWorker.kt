package com.musibox.app.download

import android.content.Context
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.musibox.app.MusiBoxApp
import com.musibox.app.core.Fmt
import com.musibox.app.data.db.DownloadStatus
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.storage.MediaStoreSaver
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

/**
 * Executa um download em segundo plano (WorkManager), com notificação de progresso.
 * Só marca como concluído depois de confirmar que o arquivo foi gravado no destino.
 */
class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val container get() = (applicationContext as MusiBoxApp).container

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val dao = container.db.downloadDao()
        val d = dao.get(id) ?: return Result.success()
        if (d.status == DownloadStatus.PAUSED || d.status == DownloadStatus.CANCELED || d.status == DownloadStatus.COMPLETED) {
            return Result.success()
        }
        val ctx = applicationContext
        DownloadNotifications.ensureChannels(ctx)
        runCatching { setForeground(DownloadNotifications.progressInfo(ctx, id, d.title, 0, "Preparando…")) }
        dao.setStatus(id, DownloadStatus.RUNNING, null, System.currentTimeMillis())

        val workDir = File(ctx.cacheDir, "dl/$id")
        val settings = container.settings.snapshot()

        return try {
            workDir.mkdirs()
            val free = StatFs(workDir.path).availableBytes
            if (d.totalBytes > 0 && free < d.totalBytes * 2 + MIN_FREE) throw DownloadError("Espaço insuficiente no aparelho.")
            if (free < MIN_FREE) throw DownloadError("Espaço insuficiente no aparelho.")

            var lastDb = 0L
            var lastNotif = 0L
            val file = container.ytdlp.download(
                processId = id,
                url = d.sourceUrl,
                selector = d.formatSelector,
                convertTo = d.convertTo,
                mergeMp4 = d.mergeMp4,
                isAudio = d.isAudio,
                mp3Kbps = settings.audioBitrate,
                dir = workDir,
            ) { percent, eta, line ->
                val now = System.currentTimeMillis()
                val parsed = parseLine(line)
                val finishing = line.contains("[Merger]") || line.contains("[ExtractAudio]") ||
                    line.contains("[EmbedThumbnail]") || line.contains("[Metadata]")
                if (now - lastDb > 400) {
                    lastDb = now
                    val p = (percent / 100f).coerceIn(0f, 1f)
                    val total = parsed.total.takeIf { it > 0 } ?: d.totalBytes
                    kotlinx.coroutines.runBlocking {
                        dao.setProgress(id, p, (total * p).toLong(), total, parsed.speed, eta, now)
                    }
                }
                if (now - lastNotif > 1000) {
                    lastNotif = now
                    val text = if (finishing) "Finalizando…" else buildString {
                        append("${percent.toInt().coerceIn(0, 100)}%")
                        if (parsed.speed > 0) append(" • ${Fmt.speed(parsed.speed)}")
                        if (eta > 0) append(" • ${Fmt.durationSec(eta)} restantes")
                    }
                    DownloadNotifications.updateProgress(ctx, id, d.title, percent.toInt(), text)
                }
            }

            if (!file.exists() || file.length() <= 0) throw IOException("O arquivo baixado está vazio.")
            DownloadNotifications.updateProgress(ctx, id, d.title, 100, "Salvando…")

            val ext = file.extension.lowercase(Locale.ROOT)
            val mime = MediaStoreSaver.mimeFromName(file.name)
            val displayName = MediaStoreSaver.sanitize(d.fileName).let { if (it.endsWith(".$ext", true)) it else "$it.$ext" }
            val size = file.length()

            if (d.destination == SaveDestination.VAULT.name) {
                val item = container.vault.importFile(file, displayName, mime, d.sourceUrl)
                dao.complete(id, null, item.id, mime, size, System.currentTimeMillis())
            } else {
                val uri = FileInputStream(file).use { input ->
                    MediaStoreSaver.save(ctx, input, displayName, mime, settings.downloadFolder, size)
                }
                dao.complete(id, uri.toString(), null, mime, size, System.currentTimeMillis())
                if (mime.startsWith("audio/")) container.music.requestRescan()
            }
            workDir.deleteRecursively()
            val where = if (d.destination == SaveDestination.VAULT.name) "Arquivo salvo no cofre." else "Salvo no aparelho."
            DownloadNotifications.finished(ctx, id, d.title, true, where)
            Result.success()
        } catch (e: CancellationException) {
            container.ytdlp.stop(id)
            withContext(NonCancellable) {
                val cur = dao.get(id)
                if (cur?.status == DownloadStatus.RUNNING) {
                    dao.setStatus(id, DownloadStatus.QUEUED, "Aguardando conexão…", System.currentTimeMillis())
                }
            }
            throw e
        } catch (e: YoutubeDL.CanceledException) {
            // Pausado ou cancelado pelo usuário; o estado já foi gravado por quem pediu.
            val cur = dao.get(id)
            if (cur?.status == DownloadStatus.RUNNING) {
                dao.setStatus(id, DownloadStatus.QUEUED, "Aguardando…", System.currentTimeMillis())
                Result.retry()
            } else {
                DownloadNotifications.cancel(ctx, id)
                Result.success()
            }
        } catch (e: Exception) {
            val cur = dao.get(id)
            if (cur?.status == DownloadStatus.PAUSED || cur?.status == DownloadStatus.CANCELED) {
                DownloadNotifications.cancel(ctx, id)
                return Result.success()
            }
            val error = e as? DownloadError ?: DownloadError(e.message ?: "Não foi possível baixar este arquivo.")
            if (error.retryable && runAttemptCount < MAX_RETRIES) {
                dao.setStatus(id, DownloadStatus.QUEUED, "Conexão interrompida. Tentando novamente…", System.currentTimeMillis())
                DownloadNotifications.cancel(ctx, id)
                Result.retry()
            } else {
                dao.setStatus(id, DownloadStatus.FAILED, error.message, System.currentTimeMillis())
                DownloadNotifications.finished(ctx, id, d.title, false, error.message ?: "Erro")
                Result.success()
            }
        }
    }

    private data class LineInfo(val total: Long, val speed: Long)

    private fun parseLine(line: String): LineInfo {
        val total = totalRegex.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        val speed = speedRegex.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        return LineInfo(total, speed)
    }

    private fun toBytes(value: String, unit: String): Long {
        val v = value.toDoubleOrNull() ?: return 0
        val mult = when (unit.lowercase(Locale.ROOT)) {
            "kib" -> 1024.0
            "mib" -> 1024.0 * 1024
            "gib" -> 1024.0 * 1024 * 1024
            "kb" -> 1000.0
            "mb" -> 1000.0 * 1000
            "gb" -> 1000.0 * 1000 * 1000
            else -> 1.0
        }
        return (v * mult).toLong()
    }

    companion object {
        const val KEY_ID = "download_id"
        private const val MIN_FREE = 50L * 1024 * 1024
        private const val MAX_RETRIES = 8
        private val totalRegex = Regex("""of\s+~?\s*([\d.]+)\s*([KMG]i?B)""", RegexOption.IGNORE_CASE)
        private val speedRegex = Regex("""at\s+([\d.]+)\s*([KMG]i?B)/s""", RegexOption.IGNORE_CASE)
    }
}
