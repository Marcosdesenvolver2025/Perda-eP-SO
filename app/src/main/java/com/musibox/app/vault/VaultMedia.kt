package com.musibox.app.vault

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import coil.ImageLoader
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

/** Endereço interno de um item do cofre para o player: vault://item/{id} */
fun vaultUri(id: String): Uri = Uri.parse("vault://item/$id")

/**
 * Fonte de dados do player que lê direto do arquivo criptografado, com avanço/retrocesso,
 * sem criar cópia aberta do vídeo ou áudio.
 */
@OptIn(UnstableApi::class)
class VaultDataSource(private val vault: VaultRepository) : BaseDataSource(false) {
    private var channel: SeekableByteChannel? = null
    private var currentUri: Uri? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("Item inválido")
        val ch = vault.openSeekable(id)
        val size = ch.size()
        if (dataSpec.position > size) throw IOException("Posição inválida")
        ch.position(dataSpec.position)
        channel = ch
        currentUri = dataSpec.uri
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - dataSpec.position
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val ch = channel ?: return C.RESULT_END_OF_INPUT
        val toRead = min(length.toLong(), bytesRemaining).toInt()
        val read = ch.read(ByteBuffer.wrap(buffer, offset, toRead))
        if (read <= 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        try {
            channel?.close()
        } finally {
            channel = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val vault: VaultRepository) : DataSource.Factory {
        override fun createDataSource(): DataSource = VaultDataSource(vault)
    }
}

// ---------- Imagens do cofre (descriptografadas só na memória) ----------

data class VaultImage(val id: String, val mimeType: String)
data class VaultThumb(val id: String)

class VaultImageFetcher(
    private val data: VaultImage,
    private val options: Options,
    private val vault: VaultRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val stream = vault.openDecrypted(data.id)
        SourceResult(
            source = ImageSource(stream.source().buffer(), options.context),
            mimeType = data.mimeType,
            dataSource = coil.decode.DataSource.DISK,
        )
    }

    class Factory(private val vault: VaultRepository) : Fetcher.Factory<VaultImage> {
        override fun create(data: VaultImage, options: Options, imageLoader: ImageLoader): Fetcher =
            VaultImageFetcher(data, options, vault)
    }
}

class VaultThumbFetcher(
    private val data: VaultThumb,
    private val options: Options,
    private val vault: VaultRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val stream = vault.openThumb(data.id) ?: throw FileNotFoundException("Sem miniatura")
        SourceResult(
            source = ImageSource(stream.source().buffer(), options.context),
            mimeType = "image/jpeg",
            dataSource = coil.decode.DataSource.DISK,
        )
    }

    class Factory(private val vault: VaultRepository) : Fetcher.Factory<VaultThumb> {
        override fun create(data: VaultThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            VaultThumbFetcher(data, options, vault)
    }
}

class VaultImageKeyer : Keyer<VaultImage> {
    override fun key(data: VaultImage, options: Options): String = "vault_img:${data.id}"
}

class VaultThumbKeyer : Keyer<VaultThumb> {
    override fun key(data: VaultThumb, options: Options): String = "vault_thumb:${data.id}"
}
