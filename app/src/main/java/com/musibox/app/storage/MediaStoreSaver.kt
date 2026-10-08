package com.musibox.app.storage

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.musibox.app.data.prefs.DownloadFolder
import java.io.IOException
import java.io.InputStream

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data class NeedsUserConfirmation(val intentSender: IntentSender) : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

data class FileMeta(val name: String, val size: Long, val mime: String)

/** Grava arquivos na galeria/armazenamento público pelo MediaStore (sem permissão de armazenamento). */
object MediaStoreSaver {

    private data class Target(val collection: Uri, val relativePath: String)

    private fun targetFor(mime: String, folder: DownloadFolder): Target {
        val downloads = Target(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${Environment.DIRECTORY_DOWNLOADS}/MusiBox",
        )
        if (folder == DownloadFolder.DOWNLOADS) return downloads
        return when {
            mime.startsWith("audio/") -> Target(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${Environment.DIRECTORY_MUSIC}/MusiBox",
            )
            mime.startsWith("video/") -> Target(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${Environment.DIRECTORY_MOVIES}/MusiBox",
            )
            mime.startsWith("image/") -> Target(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${Environment.DIRECTORY_PICTURES}/MusiBox",
            )
            else -> downloads
        }
    }

    /**
     * Copia [input] para um novo arquivo público. Nunca sobrescreve: se o nome já existir,
     * acrescenta " (2)", " (3)"... O arquivo só fica visível depois de gravado por completo.
     */
    fun save(
        context: Context,
        input: InputStream,
        displayName: String,
        mime: String,
        folder: DownloadFolder,
        expectedSize: Long = -1,
    ): Uri {
        val resolver = context.contentResolver
        val target = targetFor(mime, folder)
        val name = uniqueName(context, target, sanitize(displayName))
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(target.collection, values)
            ?: throw IOException("Não foi possível criar o arquivo no aparelho.")
        try {
            val written = resolver.openOutputStream(uri, "w")?.use { out ->
                val n = input.copyTo(out, 256 * 1024)
                out.flush()
                n
            } ?: throw IOException("Não foi possível gravar o arquivo.")
            if (written <= 0L) throw IOException("O arquivo ficou vazio.")
            if (expectedSize >= 0 && written != expectedSize) throw IOException("O arquivo não foi gravado por completo.")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw if (e is IOException) e else IOException(e.message ?: "Falha ao salvar o arquivo.", e)
        }
    }

    private fun uniqueName(context: Context, target: Target, name: String): String {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isNotEmpty() && it != name) ".$it" else "" }
        var candidate = name
        var n = 2
        while (exists(context, target, candidate) && n < 1000) {
            candidate = "$base ($n)$ext"
            n++
        }
        return candidate
    }

    private fun exists(context: Context, target: Target, name: String): Boolean = try {
        context.contentResolver.query(
            target.collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, target.relativePath + "%"),
            null,
        )?.use { it.count > 0 } ?: false
    } catch (e: Exception) {
        false
    }

    fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return cleaned.ifBlank { "arquivo" }.take(150)
    }

    /** Apaga arquivos públicos. Quando o Android exigir, devolve o pedido de confirmação do sistema. */
    fun delete(context: Context, uris: List<Uri>): DeleteResult {
        if (uris.isEmpty()) return DeleteResult.Deleted
        val resolver = context.contentResolver
        val pending = ArrayList<Uri>()
        for (uri in uris) {
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 30) {
                    pending += uri
                } else if (e is RecoverableSecurityException) {
                    return DeleteResult.NeedsUserConfirmation(e.userAction.actionIntent.intentSender)
                } else {
                    return DeleteResult.Failed("Sem permissão para apagar o arquivo original.")
                }
            } catch (e: Exception) {
                return DeleteResult.Failed(e.message ?: "Não foi possível apagar o arquivo.")
            }
        }
        if (pending.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
            return DeleteResult.NeedsUserConfirmation(MediaStore.createDeleteRequest(resolver, pending).intentSender)
        }
        return DeleteResult.Deleted
    }

    /**
     * Prepara a exclusão do original de um arquivo escolhido pelo seletor do sistema.
     * Converte o documento em item do MediaStore para pedir a confirmação do Android.
     */
    fun deleteOriginals(context: Context, documentUris: List<Uri>): DeleteResult {
        val resolver = context.contentResolver
        val mediaUris = ArrayList<Uri>()
        for (doc in documentUris) {
            val media = runCatching { MediaStore.getMediaUri(context, doc) }.getOrNull()
            if (media != null) {
                mediaUris += media
            } else {
                val ok = runCatching { DocumentsContract.deleteDocument(resolver, doc) }.getOrDefault(false)
                if (!ok) return DeleteResult.Failed("Não foi possível apagar o arquivo original.")
            }
        }
        if (mediaUris.isEmpty()) return DeleteResult.Deleted
        if (Build.VERSION.SDK_INT >= 30) {
            return DeleteResult.NeedsUserConfirmation(MediaStore.createDeleteRequest(resolver, mediaUris).intentSender)
        }
        return delete(context, mediaUris)
    }

    fun queryMeta(context: Context, uri: Uri): FileMeta {
        var name: String? = null
        var size = -1L
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val si = c.getColumnIndex(OpenableColumns.SIZE)
                            if (ni >= 0) name = c.getString(ni)
                            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                        }
                    }
            }
        } else if (uri.scheme == "file") {
            val f = java.io.File(uri.path ?: "")
            name = f.name
            size = f.length()
        }
        val finalName = name ?: uri.lastPathSegment ?: "arquivo"
        val mime = context.contentResolver.getType(uri) ?: mimeFromName(finalName)
        return FileMeta(finalName, size, mime)
    }

    fun mimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "opus" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
    }

    fun extensionFor(mime: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: when (mime) {
            "audio/mp4" -> "m4a"
            "audio/mpeg" -> "mp3"
            else -> "bin"
        }
}
