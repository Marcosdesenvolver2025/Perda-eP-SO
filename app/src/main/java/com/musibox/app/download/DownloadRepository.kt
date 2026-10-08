package com.musibox.app.download

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.db.DownloadEntity
import com.musibox.app.data.db.DownloadKind
import com.musibox.app.data.db.DownloadStatus
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.data.prefs.SettingsRepository
import com.musibox.app.data.repo.MusicRepository
import com.musibox.app.storage.DeleteResult
import com.musibox.app.storage.MediaStoreSaver
import com.musibox.app.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Fila de downloads/importações e ações do gerenciador. */
class DownloadRepository(
    private val context: Context,
    db: AppDatabase,
    private val settings: SettingsRepository,
    private val vault: VaultRepository,
    private val music: MusicRepository,
    private val engine: YtDlpEngine,
) {
    private val dao = db.downloadDao()
    private val workManager get() = WorkManager.getInstance(context)

    val all: Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun get(id: String): DownloadEntity? = dao.get(id)

    suspend fun enqueue(
        info: MediaInfo,
        option: DownloadOption,
        fileName: String,
        destination: SaveDestination,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            DownloadEntity(
                id = id,
                kind = DownloadKind.DOWNLOAD,
                sourceUrl = info.webpageUrl.ifBlank { info.url },
                platform = info.platform.name,
                title = info.title,
                thumbnailUrl = info.thumbnail,
                durationSec = info.durationSec,
                formatSelector = option.selector,
                formatLabel = "${option.label} • ${option.ext.uppercase()}",
                isAudio = option.kind == OptionKind.AUDIO,
                convertTo = option.convertTo,
                mergeMp4 = option.mergeMp4,
                targetExt = option.convertTo ?: option.ext,
                fileName = MediaStoreSaver.sanitize(fileName.ifBlank { info.title }),
                destination = destination.name,
                status = DownloadStatus.QUEUED,
                progress = 0f,
                downloadedBytes = 0,
                totalBytes = option.estimatedBytes,
                speedBps = 0,
                etaSec = 0,
                errorMessage = null,
                outputUri = null,
                vaultItemId = null,
                mimeType = null,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
            ),
        )
        schedule(id)
        return id
    }

    private suspend fun schedule(id: String) {
        val wifiOnly = settings.snapshot().wifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun pause(id: String) {
        dao.setStatus(id, DownloadStatus.PAUSED, null, System.currentTimeMillis())
        engine.stop(id)
        workManager.cancelUniqueWork(workName(id))
        DownloadNotifications.cancel(context, id)
    }

    suspend fun resume(id: String) {
        val d = dao.get(id) ?: return
        if (d.status != DownloadStatus.PAUSED && d.status != DownloadStatus.QUEUED) return
        dao.setStatus(id, DownloadStatus.QUEUED, null, System.currentTimeMillis())
        schedule(id)
    }

    suspend fun cancel(id: String) {
        dao.setStatus(id, DownloadStatus.CANCELED, null, System.currentTimeMillis())
        engine.stop(id)
        workManager.cancelUniqueWork(workName(id))
        DownloadNotifications.cancel(context, id)
        withContext(Dispatchers.IO) { File(context.cacheDir, "dl/$id").deleteRecursively() }
    }

    suspend fun retry(id: String) {
        val d = dao.get(id) ?: return
        if (d.kind != DownloadKind.DOWNLOAD) return
        dao.resetForRetry(id, System.currentTimeMillis())
        schedule(id)
    }

    /** Retoma downloads interrompidos (ex.: app fechado à força durante o download). */
    suspend fun resumeInterrupted() {
        for (d in dao.unfinished()) {
            if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.QUEUED) {
                dao.setStatus(d.id, DownloadStatus.QUEUED, d.errorMessage, System.currentTimeMillis())
                schedule(d.id)
            }
        }
    }

    /**
     * Remove o registro. Se [deleteFile], também apaga o arquivo (galeria ou cofre).
     * Retorna o pedido de confirmação do Android quando necessário.
     */
    suspend fun remove(id: String, deleteFile: Boolean): DeleteResult {
        val d = dao.get(id) ?: return DeleteResult.Deleted
        if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.QUEUED || d.status == DownloadStatus.PAUSED) {
            cancel(id)
        }
        if (deleteFile) {
            d.vaultItemId?.let { vault.delete(listOf(it)) }
            val uri = d.outputUri
            if (uri != null) {
                val result = withContext(Dispatchers.IO) { MediaStoreSaver.delete(context, listOf(Uri.parse(uri))) }
                if (result !is DeleteResult.Deleted) return result
                music.requestRescan()
            }
        }
        dao.delete(id)
        return DeleteResult.Deleted
    }

    suspend fun removeRecordOnly(id: String) = dao.delete(id)

    /** Limpa o histórico de downloads. Arquivos só são apagados se [deleteFiles]. */
    suspend fun clearHistory(deleteFiles: Boolean): DeleteResult {
        val all = dao.all().filter {
            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED
        }
        if (deleteFiles) {
            val vaultIds = all.mapNotNull { it.vaultItemId }
            if (vaultIds.isNotEmpty()) vault.delete(vaultIds)
            val uris = all.mapNotNull { it.outputUri }.map(Uri::parse)
            val result = withContext(Dispatchers.IO) { MediaStoreSaver.delete(context, uris) }
            if (result !is DeleteResult.Deleted) {
                // O usuário ainda precisa confirmar a exclusão dos arquivos; mantém os registros por enquanto.
                return result
            }
            music.requestRescan()
        }
        all.forEach { dao.delete(it.id) }
        return DeleteResult.Deleted
    }

    /** Move um download concluído da galeria para o cofre. */
    suspend fun moveToVault(id: String): DeleteResult {
        val d = dao.get(id) ?: throw IllegalStateException("Download não encontrado.")
        val uriStr = d.outputUri ?: throw IllegalStateException("Este arquivo já está no cofre.")
        val uri = Uri.parse(uriStr)
        val name = d.fileName + "." + (d.mimeType?.let { MediaStoreSaver.extensionFor(it) } ?: d.targetExt)
        val item = vault.importUri(uri, nameOverride = name, mimeOverride = d.mimeType, source = d.sourceUrl)
        dao.setLocation(id, SaveDestination.VAULT.name, null, item.id, System.currentTimeMillis())
        val result = withContext(Dispatchers.IO) { MediaStoreSaver.delete(context, listOf(uri)) }
        if (result is DeleteResult.Deleted) music.requestRescan()
        return result
    }

    /** Registra uma importação manual no histórico. */
    suspend fun logImport(
        title: String,
        mime: String,
        size: Long,
        destination: SaveDestination,
        outputUri: String?,
        vaultItemId: String?,
        source: String?,
    ) {
        val now = System.currentTimeMillis()
        dao.upsert(
            DownloadEntity(
                id = UUID.randomUUID().toString(),
                kind = DownloadKind.IMPORT,
                sourceUrl = source ?: "",
                platform = Platform.LOCAL.name,
                title = title.substringBeforeLast('.'),
                thumbnailUrl = null,
                durationSec = 0,
                formatSelector = "",
                formatLabel = "Importado",
                isAudio = mime.startsWith("audio/"),
                convertTo = null,
                mergeMp4 = false,
                targetExt = title.substringAfterLast('.', ""),
                fileName = title.substringBeforeLast('.'),
                destination = destination.name,
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                downloadedBytes = size,
                totalBytes = size,
                speedBps = 0,
                etaSec = 0,
                errorMessage = null,
                outputUri = outputUri,
                vaultItemId = vaultItemId,
                mimeType = mime,
                createdAt = now,
                updatedAt = now,
                completedAt = now,
            ),
        )
    }

    /** Importa um arquivo do aparelho para a galeria (pasta do MusiBox) ou para o cofre. */
    suspend fun importLocal(uri: Uri, destination: SaveDestination): String = withContext(Dispatchers.IO) {
        val meta = MediaStoreSaver.queryMeta(context, uri)
        when (destination) {
            SaveDestination.VAULT -> {
                val item = vault.importUri(uri, source = "import")
                logImport(meta.name, meta.mime, item.size, destination, null, item.id, null)
                "Arquivo salvo no cofre."
            }
            SaveDestination.GALLERY -> {
                val folder = settings.snapshot().downloadFolder
                val out = context.contentResolver.openInputStream(uri)?.use { input ->
                    MediaStoreSaver.save(context, input, meta.name, meta.mime, folder)
                } ?: throw IllegalStateException("Não foi possível abrir o arquivo.")
                logImport(meta.name, meta.mime, meta.size, destination, out.toString(), null, null)
                if (meta.mime.startsWith("audio/")) music.requestRescan()
                "Arquivo importado para o aparelho."
            }
        }
    }

    companion object {
        const val TAG = "musibox_download"
        fun workName(id: String) = "download_$id"
    }
}
