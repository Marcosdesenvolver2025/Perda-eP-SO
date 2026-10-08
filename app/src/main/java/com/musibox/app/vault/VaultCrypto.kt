package com.musibox.app.vault

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/**
 * Criptografia dos arquivos do cofre.
 *
 * AES-256-GCM em blocos (Tink StreamingAead), o que permite criptografar arquivos grandes sem
 * carregá-los na memória e reproduzir vídeos com avanço/retrocesso sem gerar cópia aberta.
 * A chave de dados fica protegida por uma chave mestra do Android Keystore (não exportável).
 */
class VaultCrypto(private val context: Context) {

    private val aead: StreamingAead by lazy {
        StreamingAeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFS_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        @Suppress("DEPRECATION")
        handle.getPrimitive(StreamingAead::class.java)
    }

    fun encrypt(out: OutputStream, associatedData: ByteArray): OutputStream =
        aead.newEncryptingStream(out, associatedData)

    fun decrypt(input: InputStream, associatedData: ByteArray): InputStream =
        aead.newDecryptingStream(input, associatedData)

    fun seekable(file: File, associatedData: ByteArray): SeekableByteChannel =
        aead.newSeekableDecryptingChannel(FileInputStream(file).channel, associatedData)

    /** Apaga a chave (usado somente ao redefinir o cofre por completo). */
    fun destroyKeys() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    companion object {
        private const val KEYSET_NAME = "vault_keyset"
        private const val PREFS_NAME = "musibox_vault_keys"
        private const val MASTER_KEY_ALIAS = "musibox_vault_master"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
    }
}
