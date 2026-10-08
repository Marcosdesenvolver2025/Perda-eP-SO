package com.musibox.app.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.db.VaultCategory
import com.musibox.app.data.db.VaultItemEntity
import com.musibox.app.data.prefs.DownloadFolder
import com.musibox.app.storage.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.util.UUID

class VaultException(message: String) : IOException(message)

/**
 * Armazenamento privado do cofre: arquivos criptografados dentro da área interna do app
 * (inacessível a outros apps e à galeria).
 */
class VaultRepository(
    private val context: Context,
    db: AppDatabase,
    private val crypto: VaultCrypto,
) {
    private val dao = db.vaultDao()
    private val root = File(context.filesDir, "vault")
    private val dataDir = File(root, "data")
    private val thumbDir = File(root, "thumbs")
    private val tempDir = File(context.cacheDir, "vault_open")

    val items: Flow<List<VaultItemEntity>> = dao.observeAll()

    init {
        dataDir.mkdirs()
        thumbDir.mkdirs()
    }

    private fun dataFile(id: String) = File(dataDir, "$id.bin")
    private fun thumbFile(id: String) = File(thumbDir, "$id.bin")
    private fun aad(id: String) = "data:$id".toByteArray()
    private fun thumbAad(id: String) = "thumb:$id".toByteArray()

    suspend fun get(id: String): VaultItemEntity? = dao.get(id)

    /** Copia um arquivo (content:// ou file://) para o cofre, criptografado. */
    suspend fun importUri(
        uri: Uri,
        nameOverride: String? = null,
        mimeOverride: String? = null,
        source: String? = null,
        folder: String = "",
    ): VaultItemEntity = withContext(Dispatchers.IO) {
        val meta = MediaStoreSaver.queryMeta(context, uri)
        val name = nameOverride ?: meta.name
        val mime = mimeOverride ?: meta.mime
        val free = StatFs(context.filesDir.path).availableBytes
        if (meta.size > 0 && free < meta.size + 50L * 1024 * 1024) {
            throw VaultException("Espaço insuficiente no aparelho.")
        }
        val id = UUID.randomUUID().toString()
        val target = dataFile(id)
        val category = VaultCategory.fromMime(mime)
        try {
            val written = (context.contentResolver.openInputStream(uri)
                ?: throw VaultException("Não foi possível abrir o arquivo.")).use { input ->
                FileOutputStream(target).use { fos ->
                    crypto.encrypt(fos, aad(id)).use { enc -> input.copyTo(enc, 256 * 1024) }
                }
            }
            if (written <= 0) throw VaultException("O arquivo está vazio.")
            // Confirma que o arquivo criptografado pode ser lido por completo.
            val check = openDecrypted(id).use { countBytes(it) }
            if (check != written) throw VaultException("Falha ao verificar o arquivo no cofre.")

            val info = readMediaInfo(uri, category)
            val thumb = createThumbnail(uri, category)
            val hasThumb = thumb?.let { saveThumb(id, it) } ?: false

            val item = VaultItemEntity(
                id = id,
                name = name,
                mimeType = mime,
                category = category,
                size = written,
                durationMs = info.first,
                width = info.second,
                height = info.third,
                folder = folder,
                hasThumb = hasThumb,
                createdAt = System.currentTimeMillis(),
                importedFrom = source,
            )
            dao.upsert(item)
            item
        } catch (e: Exception) {
            target.delete()
            thumbFile(id).delete()
            throw if (e is IOException) e else VaultException(e.message ?: "Não foi possível salvar no cofre.")
        }
    }

    suspend fun importFile(file: File, name: String, mime: String, source: String?): VaultItemEntity =
        importUri(Uri.fromFile(file), name, mime, source)

    /** Importa vindo de outro fluxo já descriptografado (ex.: restauração de backup). */
    suspend fun importStream(
        input: InputStream,
        name: String,
        mime: String,
        size: Long,
        durationMs: Long,
        width: Int,
        height: Int,
        folder: String,
        createdAt: Long,
        thumb: ByteArray?,
    ): VaultItemEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val target = dataFile(id)
        try {
            val written = FileOutputStream(target).use { fos ->
                crypto.encrypt(fos, aad(id)).use { enc -> input.copyTo(enc, 256 * 1024) }
            }
            if (size >= 0 && written != size) throw VaultException("Arquivo incompleto no backup.")
            var hasThumb = false
            if (thumb != null && thumb.isNotEmpty()) {
                FileOutputStream(thumbFile(id)).use { fos ->
                    crypto.encrypt(fos, thumbAad(id)).use { it.write(thumb) }
                }
                hasThumb = true
            }
            val item = VaultItemEntity(
                id, name, mime, VaultCategory.fromMime(mime), written, durationMs, width, height,
                folder, hasThumb, createdAt, "backup",
            )
            dao.upsert(item)
            item
        } catch (e: Exception) {
            target.delete()
            thumbFile(id).delete()
            throw if (e is IOException) e else VaultException(e.message ?: "Falha ao restaurar item.")
        }
    }

    fun openDecrypted(id: String): InputStream = crypto.decrypt(FileInputStream(dataFile(id)), aad(id))

    fun openThumb(id: String): InputStream? {
        val f = thumbFile(id)
        if (!f.exists()) return null
        return crypto.decrypt(FileInputStream(f), thumbAad(id))
    }

    fun readThumbBytes(id: String): ByteArray? = runCatching { openThumb(id)?.use { it.readBytes() } }.getOrNull()

    fun openSeekable(id: String): SeekableByteChannel = crypto.seekable(dataFile(id), aad(id))

    /** Copia o arquivo para fora do cofre (galeria/Downloads). O item continua no cofre. */
    suspend fun export(id: String, folder: DownloadFolder): Uri = withContext(Dispatchers.IO) {
        val item = dao.get(id) ?: throw VaultException("Arquivo não encontrado no cofre.")
        openDecrypted(id).use { input ->
            MediaStoreSaver.save(context, input, item.name, item.mimeType, folder, item.size)
        }
    }

    suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        for (id in ids) {
            dataFile(id).delete()
            thumbFile(id).delete()
            dao.delete(id)
        }
    }

    suspend fun rename(id: String, newName: String) {
        val item = dao.get(id) ?: return
        val ext = item.name.substringAfterLast('.', "")
        val clean = MediaStoreSaver.sanitize(newName)
        val finalName = if (ext.isNotEmpty() && !clean.endsWith(".$ext", ignoreCase = true)) "$clean.$ext" else clean
        dao.rename(id, finalName)
    }

    suspend fun move(ids: List<String>, folder: String) = dao.move(ids, folder.trim())

    /** Cria uma cópia temporária aberta para entregar a outro app (abrir/compartilhar). */
    suspend fun decryptToTemp(id: String): File = withContext(Dispatchers.IO) {
        val item = dao.get(id) ?: throw VaultException("Arquivo não encontrado no cofre.")
        val dir = File(tempDir, id).apply { mkdirs() }
        val out = File(dir, MediaStoreSaver.sanitize(item.name))
        openDecrypted(id).use { input -> FileOutputStream(out).use { input.copyTo(it, 256 * 1024) } }
        out
    }

    fun clearTemp() {
        runCatching { tempDir.deleteRecursively() }
    }

    suspend fun totalSize(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun all(): List<VaultItemEntity> = dao.all()

    /** Apaga todo o conteúdo do cofre (usado ao redefinir um cofre cujo PIN foi esquecido). */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        root.deleteRecursively()
        dao.clear()
        clearTemp()
        dataDir.mkdirs()
        thumbDir.mkdirs()
    }

    // ------------------------------------------------------------------

    private fun countBytes(input: InputStream): Long {
        val buffer = ByteArray(256 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun saveThumb(id: String, bitmap: Bitmap): Boolean = try {
        FileOutputStream(thumbFile(id)).use { fos ->
            crypto.encrypt(fos, thumbAad(id)).use { enc -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, enc) }
        }
        true
    } catch (e: Exception) {
        thumbFile(id).delete()
        false
    } finally {
        bitmap.recycle()
    }

    /** (duração ms, largura, altura) */
    private fun readMediaInfo(uri: Uri, category: String): Triple<Long, Int, Int> {
        if (category == VaultCategory.PHOTO) {
            return runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                Triple(0L, opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0))
            }.getOrDefault(Triple(0L, 0, 0))
        }
        if (category != VaultCategory.VIDEO && category != VaultCategory.AUDIO) return Triple(0L, 0, 0)
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Triple(d, w, h)
        } catch (e: Exception) {
            Triple(0L, 0, 0)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun createThumbnail(uri: Uri, category: String): Bitmap? = try {
        when (category) {
            VaultCategory.PHOTO -> {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = (THUMB_SIZE.toFloat() / maxOf(w, h)).coerceAtMost(1f)
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            VaultCategory.VIDEO -> {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_SIZE, THUMB_SIZE)
                        ?: r.frameAtTime
                } finally {
                    runCatching { r.release() }
                }
            }
            VaultCategory.AUDIO -> {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.embeddedPicture?.let { bytes ->
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        var sample = 1
                        while (opts.outWidth / (sample * 2) >= THUMB_SIZE) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                } finally {
                    runCatching { r.release() }
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val THUMB_SIZE = 360
    }
}
