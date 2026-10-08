package com.musibox.app.vault

import android.content.Context
import android.net.Uri
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Backup manual do cofre em um arquivo protegido por senha.
 *
 * O arquivo é criptografado com uma chave derivada da senha (PBKDF2 + AES-256-GCM em blocos),
 * então pode ser guardado onde o usuário quiser e restaurado em outro aparelho.
 * Nada é enviado para a nuvem automaticamente.
 */
class VaultBackup(private val context: Context, private val vault: VaultRepository) {

    class WrongPasswordException : IOException("Senha incorreta ou arquivo danificado.")

    suspend fun export(target: Uri, password: CharArray, onProgress: (Float) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val items = vault.all()
            if (items.isEmpty()) throw VaultException("O cofre está vazio.")
            val total = items.sumOf { it.size }.coerceAtLeast(1)
            var done = 0L
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val streaming = streamingFor(password, salt)
            val out = context.contentResolver.openOutputStream(target, "w")
                ?: throw IOException("Não foi possível criar o arquivo de backup.")
            out.use { raw ->
                val header = DataOutputStream(raw)
                header.write(MAGIC)
                header.write(salt)
                header.writeInt(ITERATIONS)
                header.flush()
                streaming.newEncryptingStream(raw, MAGIC).use { enc ->
                    ZipOutputStream(enc).use { zip ->
                        zip.setLevel(Deflater.NO_COMPRESSION)
                        val manifest = JSONArray()
                        items.forEach { item ->
                            manifest.put(
                                JSONObject()
                                    .put("id", item.id)
                                    .put("name", item.name)
                                    .put("mime", item.mimeType)
                                    .put("size", item.size)
                                    .put("durationMs", item.durationMs)
                                    .put("width", item.width)
                                    .put("height", item.height)
                                    .put("folder", item.folder)
                                    .put("createdAt", item.createdAt),
                            )
                        }
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(JSONObject().put("version", 1).put("items", manifest).toString().toByteArray())
                        zip.closeEntry()
                        for (item in items) {
                            vault.readThumbBytes(item.id)?.let { bytes ->
                                zip.putNextEntry(ZipEntry("thumbs/${item.id}"))
                                zip.write(bytes)
                                zip.closeEntry()
                            }
                            zip.putNextEntry(ZipEntry("items/${item.id}"))
                            vault.openDecrypted(item.id).use { input ->
                                val buf = ByteArray(256 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    zip.write(buf, 0, n)
                                    done += n
                                    onProgress(done.toFloat() / total)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }
            }
            items.size
        }

    suspend fun import(source: Uri, password: CharArray, onProgress: (Float) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("Não foi possível abrir o arquivo de backup.")
            var restored = 0
            input.use { raw ->
                val header = DataInputStream(raw)
                val magic = ByteArray(MAGIC.size)
                header.readFully(magic)
                if (!magic.contentEquals(MAGIC)) throw VaultException("Este arquivo não é um backup do cofre MusiBox.")
                val salt = ByteArray(16)
                header.readFully(salt)
                val iterations = header.readInt()
                if (iterations !in 10_000..2_000_000) throw VaultException("Backup inválido.")
                val streaming = streamingFor(password, salt, iterations)
                val decrypted = try {
                    streaming.newDecryptingStream(raw, MAGIC)
                } catch (e: Exception) {
                    throw WrongPasswordException()
                }
                try {
                    ZipInputStream(decrypted).use { zip ->
                        var meta: Map<String, JSONObject> = emptyMap()
                        val thumbs = HashMap<String, ByteArray>()
                        var total = 1L
                        var done = 0L
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            when {
                                entry.name == "manifest.json" -> {
                                    val json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                                    val arr = json.getJSONArray("items")
                                    val m = HashMap<String, JSONObject>()
                                    for (i in 0 until arr.length()) {
                                        val o = arr.getJSONObject(i)
                                        m[o.getString("id")] = o
                                        total += o.optLong("size", 0)
                                    }
                                    meta = m
                                }
                                entry.name.startsWith("thumbs/") ->
                                    thumbs[entry.name.removePrefix("thumbs/")] = zip.readBytes()
                                entry.name.startsWith("items/") -> {
                                    val id = entry.name.removePrefix("items/")
                                    val o = meta[id] ?: throw VaultException("Backup inválido.")
                                    vault.importStream(
                                        input = NonClosingStream(zip),
                                        name = o.optString("name", "arquivo"),
                                        mime = o.optString("mime", "application/octet-stream"),
                                        size = o.optLong("size", -1),
                                        durationMs = o.optLong("durationMs", 0),
                                        width = o.optInt("width", 0),
                                        height = o.optInt("height", 0),
                                        folder = o.optString("folder", ""),
                                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                        thumb = thumbs.remove(id),
                                    )
                                    restored++
                                    done += o.optLong("size", 0)
                                    onProgress(done.toFloat() / total)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                } catch (e: VaultException) {
                    throw e
                } catch (e: IOException) {
                    if (restored == 0) throw WrongPasswordException() else throw e
                }
            }
            restored
        }

    private fun streamingFor(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): AesGcmHkdfStreaming {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val ikm = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return AesGcmHkdfStreaming(ikm, "HmacSha256", 32, 1 shl 20, 0)
    }

    /** Evita que o importador feche o ZipInputStream inteiro ao terminar um item. */
    private class NonClosingStream(input: InputStream) : FilterInputStream(input) {
        override fun close() {}
    }

    companion object {
        private val MAGIC = "MBXVLT01".toByteArray()
        private const val ITERATIONS = 210_000
    }
}
