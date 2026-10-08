package com.musibox.app.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN de 6 dígitos do cofre.
 *
 * O PIN nunca é salvo. Guardamos apenas um verificador:
 * HMAC(chave do Android Keystore, PBKDF2(PIN, sal)). Sem a chave do Keystore (que não sai
 * do aparelho), não é possível testar PINs fora do celular. Também há bloqueio progressivo
 * após tentativas erradas.
 */
class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("vault_security", Context.MODE_PRIVATE)

    sealed interface VerifyResult {
        data object Ok : VerifyResult
        data class Wrong(val attemptsLeft: Int) : VerifyResult
        data class LockedOut(val remainingMs: Long) : VerifyResult
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_VERIFIER)

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "O PIN deve ter 6 dígitos." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val verifier = verifierFor(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0)
            .apply()
    }

    fun lockoutRemainingMs(): Long =
        (prefs.getLong(KEY_LOCK_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    suspend fun verify(pin: String): VerifyResult = withContext(Dispatchers.Default) {
        val remaining = lockoutRemainingMs()
        if (remaining > 0) return@withContext VerifyResult.LockedOut(remaining)
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val stored = prefs.getString(KEY_VERIFIER, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (salt == null || stored == null) return@withContext VerifyResult.Wrong(0)
        val ok = MessageDigest.isEqual(verifierFor(pin, salt), stored)
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
            VerifyResult.Ok
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAILS, fails)
            val result = if (fails >= MAX_ATTEMPTS) {
                // 30 s, 60 s, 120 s ... até 30 min
                val step = (fails - MAX_ATTEMPTS).coerceAtMost(6)
                val lockMs = (30_000L shl step).coerceAtMost(30 * 60_000L)
                editor.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + lockMs)
                VerifyResult.LockedOut(lockMs)
            } else {
                VerifyResult.Wrong(MAX_ATTEMPTS - fails)
            }
            editor.apply()
            result
        }
    }

    // ---------------- Recuperação do PIN ----------------
    // O cofre é criptografado com uma chave do Keystore (não derivada do PIN), então trocar
    // o PIN depois de provar a identidade não exige recriptografar nada.

    fun hasRecoveryCode(): Boolean = prefs.contains(KEY_RC_VERIFIER)

    fun recoveryCodeCreatedAt(): Long = prefs.getLong(KEY_RC_CREATED, 0L)

    /** Cria um novo código de recuperação (o anterior deixa de valer) e o devolve formatado. */
    suspend fun createRecoveryCode(): String = withContext(Dispatchers.Default) {
        val rnd = SecureRandom()
        val raw = (1..16).map { CODE_ALPHABET[rnd.nextInt(CODE_ALPHABET.length)] }.joinToString("")
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        prefs.edit()
            .putString(KEY_RC_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_RC_VERIFIER, Base64.encodeToString(verifierFor(raw, salt), Base64.NO_WRAP))
            .putLong(KEY_RC_CREATED, System.currentTimeMillis())
            .putInt(KEY_RC_FAILS, 0)
            .putLong(KEY_RC_LOCK_UNTIL, 0)
            .apply()
        raw.chunked(4).joinToString("-")
    }

    fun recoveryLockoutRemainingMs(): Long =
        (prefs.getLong(KEY_RC_LOCK_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    suspend fun verifyRecoveryCode(input: String): VerifyResult = withContext(Dispatchers.Default) {
        val remaining = recoveryLockoutRemainingMs()
        if (remaining > 0) return@withContext VerifyResult.LockedOut(remaining)
        val salt = prefs.getString(KEY_RC_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val stored = prefs.getString(KEY_RC_VERIFIER, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (salt == null || stored == null) return@withContext VerifyResult.Wrong(0)
        val normalized = input.uppercase().filter { it.isLetterOrDigit() }
        if (normalized.length == 16 && MessageDigest.isEqual(verifierFor(normalized, salt), stored)) {
            prefs.edit().putInt(KEY_RC_FAILS, 0).putLong(KEY_RC_LOCK_UNTIL, 0).apply()
            VerifyResult.Ok
        } else {
            val fails = prefs.getInt(KEY_RC_FAILS, 0) + 1
            val editor = prefs.edit().putInt(KEY_RC_FAILS, fails)
            val result = if (fails >= MAX_ATTEMPTS) {
                val step = (fails - MAX_ATTEMPTS).coerceAtMost(6)
                val lockMs = (60_000L shl step).coerceAtMost(60 * 60_000L)
                editor.putLong(KEY_RC_LOCK_UNTIL, System.currentTimeMillis() + lockMs)
                VerifyResult.LockedOut(lockMs)
            } else {
                VerifyResult.Wrong(MAX_ATTEMPTS - fails)
            }
            editor.apply()
            result
        }
    }

    fun clearRecoveryCode() {
        prefs.edit().remove(KEY_RC_SALT).remove(KEY_RC_VERIFIER).remove(KEY_RC_CREATED).apply()
    }

    /** Conta Google vinculada ao cofre (identificada pelo UID do Firebase). */
    fun linkGoogle(uid: String, email: String?) {
        prefs.edit().putString(KEY_G_UID, uid).putString(KEY_G_EMAIL, email).apply()
    }

    fun linkedGoogleUid(): String? = prefs.getString(KEY_G_UID, null)
    fun linkedGoogleEmail(): String? = prefs.getString(KEY_G_EMAIL, null)

    fun unlinkGoogle() {
        prefs.edit().remove(KEY_G_UID).remove(KEY_G_EMAIL).apply()
    }

    /** Remove o PIN (usado somente ao redefinir o cofre). */
    fun clear() {
        prefs.edit().clear().apply()
        runCatching { keyStore().deleteEntry(HMAC_ALIAS) }
    }

    private fun verifierFor(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        return mac.doFinal(derived)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun hmacKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(HMAC_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(HMAC_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_VERIFIER = "pin_verifier"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCK_UNTIL = "pin_lock_until"
        private const val HMAC_ALIAS = "musibox_pin_hmac"
        private const val KEY_RC_SALT = "rc_salt"
        private const val KEY_RC_VERIFIER = "rc_verifier"
        private const val KEY_RC_CREATED = "rc_created"
        private const val KEY_RC_FAILS = "rc_fails"
        private const val KEY_RC_LOCK_UNTIL = "rc_lock_until"
        private const val KEY_G_UID = "google_uid"
        private const val KEY_G_EMAIL = "google_email"
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val ITERATIONS = 60_000
        const val MAX_ATTEMPTS = 5
    }
}
