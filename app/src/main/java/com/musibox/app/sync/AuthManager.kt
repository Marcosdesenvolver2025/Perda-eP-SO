package com.musibox.app.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.musibox.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class AccountUser(
    val uid: String,
    val name: String?,
    val email: String?,
    val photoUrl: String?,
)

sealed interface SignInResult {
    data class Success(val user: AccountUser) : SignInResult
    data object Canceled : SignInResult
    data class Error(val message: String) : SignInResult
}

/**
 * Login com Conta Google usando o Credential Manager (API atual recomendada pelo Google)
 * e Firebase Authentication. O login é opcional.
 */
class AuthManager(private val context: Context) {
    companion object {
        const val GOOGLE_FAIL_PREFIX = "Não foi possível entrar com o Google."
    }

    val isConfigured: Boolean =
        BuildConfig.FIREBASE_CONFIGURED &&
            BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() &&
            runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val _user = MutableStateFlow<AccountUser?>(null)
    val user: StateFlow<AccountUser?> = _user.asStateFlow()

    val currentUser: AccountUser? get() = if (isConfigured) FirebaseAuth.getInstance().currentUser?.toAccount() else null

    init {
        if (isConfigured) {
            val auth = FirebaseAuth.getInstance()
            _user.value = auth.currentUser?.toAccount()
            auth.addAuthStateListener { a -> _user.value = a.currentUser?.toAccount() }
        }
    }

    private fun FirebaseUser.toAccount() = AccountUser(
        uid = uid,
        name = displayName,
        email = email,
        photoUrl = photoUrl?.toString(),
    )

    private suspend fun googleCredential(activity: Activity): AuthCredential {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            return GoogleAuthProvider.getCredential(google.idToken, null)
        }
        throw IllegalStateException("Tipo de credencial não suportado.")
    }

    suspend fun signIn(activity: Activity): SignInResult {
        if (!isConfigured) return SignInResult.Error("O login com Google não está configurado neste aplicativo.")
        return try {
            val credential = googleCredential(activity)
            val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            val user = result.user ?: return SignInResult.Error("Não foi possível entrar.")
            SignInResult.Success(user.toAccount())
        } catch (e: Exception) {
            mapError(e)
        }
    }

    /** O Credential Manager falhou por um motivo que o login clássico do Google pode contornar? */
    fun shouldTryLegacy(result: SignInResult): Boolean =
        result is SignInResult.Error && result.message.startsWith(GOOGLE_FAIL_PREFIX)

    /** Tela clássica de escolha de conta do Google (alternativa ao Credential Manager). */
    @Suppress("DEPRECATION")
    fun legacyGoogleIntent(activity: Activity): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        runCatching { client.signOut() }
        return client.signInIntent
    }

    @Suppress("DEPRECATION")
    suspend fun finishLegacyGoogle(data: Intent?): SignInResult = try {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
        val token = account.idToken ?: throw IllegalStateException("O Google não devolveu o token de acesso.")
        val result = FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        result.user?.let { SignInResult.Success(it.toAccount()) } ?: SignInResult.Error("Não foi possível entrar.")
    } catch (e: ApiException) {
        when (e.statusCode) {
            12501 -> SignInResult.Canceled // cancelado pelo usuário
            7 -> SignInResult.Error("Sem conexão com a internet.")
            10 -> SignInResult.Error("O app não está liberado no Firebase (impressão digital SHA-1).")
            else -> SignInResult.Error("Não foi possível entrar com o Google (código ${e.statusCode}).")
        }
    } catch (e: Exception) {
        mapError(e)
    }

    // ---------------- E-mail e senha ----------------

    suspend fun signInWithEmail(email: String, password: String): SignInResult {
        if (!isConfigured) return SignInResult.Error("O login não está configurado neste aplicativo.")
        return try {
            val r = FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await()
            r.user?.let { SignInResult.Success(it.toAccount()) } ?: SignInResult.Error("Não foi possível entrar.")
        } catch (e: Exception) {
            mapEmailError(e)
        }
    }

    suspend fun createAccount(name: String, email: String, password: String): SignInResult {
        if (!isConfigured) return SignInResult.Error("O login não está configurado neste aplicativo.")
        return try {
            val r = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password).await()
            val user = r.user ?: return SignInResult.Error("Não foi possível criar a conta.")
            if (name.isNotBlank()) {
                runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await() }
                runCatching { user.reload().await() }
            }
            runCatching { user.sendEmailVerification().await() }
            val fresh = FirebaseAuth.getInstance().currentUser ?: user
            _user.value = fresh.toAccount()
            SignInResult.Success(fresh.toAccount())
        } catch (e: Exception) {
            mapEmailError(e)
        }
    }

    suspend fun sendPasswordReset(email: String): String = try {
        FirebaseAuth.getInstance().setLanguageCode("pt")
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
        "Enviamos um link para ${email.trim()} criar uma nova senha. Veja também a caixa de spam."
    } catch (e: Exception) {
        (mapEmailError(e) as? SignInResult.Error)?.message ?: "Não foi possível enviar o e-mail."
    }

    private fun mapEmailError(e: Exception): SignInResult {
        val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
        val msg = e.message.orEmpty()
        return SignInResult.Error(
            when {
                "OPERATION_NOT_ALLOWED" in code || "not enabled" in msg || "operation is not allowed" in msg.lowercase() ->
                    "O login por e-mail ainda não foi ativado no Firebase (Authentication → Método de login → E-mail/senha)."
                e is FirebaseAuthWeakPasswordException -> "Senha fraca. Use pelo menos 6 caracteres."
                e is FirebaseAuthUserCollisionException ->
                    "Já existe uma conta com este e-mail. Toque em \"Entrar\" ou use \"Continuar com Google\"."
                e is FirebaseAuthInvalidUserException -> "Conta não encontrada ou desativada."
                code == "ERROR_INVALID_EMAIL" -> "E-mail inválido."
                e is FirebaseAuthInvalidCredentialsException -> "E-mail ou senha incorretos."
                e is FirebaseTooManyRequestsException -> "Muitas tentativas. Aguarde alguns minutos e tente de novo."
                e is FirebaseNetworkException -> "Sem conexão com a internet."
                else -> msg.ifBlank { "Não foi possível continuar." }
            },
        )
    }

    /** Pede o login novamente (necessário antes de ações sensíveis, como excluir dados da nuvem). */
    suspend fun reauthenticate(activity: Activity): SignInResult {
        if (!isConfigured) return SignInResult.Error("Login não configurado.")
        val current = FirebaseAuth.getInstance().currentUser ?: return SignInResult.Error("Você não está conectado.")
        return try {
            val credential = googleCredential(activity)
            current.reauthenticate(credential).await()
            SignInResult.Success(current.toAccount())
        } catch (e: Exception) {
            val mapped = mapError(e)
            if (mapped is SignInResult.Error && e.message?.contains("user", ignoreCase = true) == true &&
                e.message?.contains("mismatch", ignoreCase = true) == true
            ) {
                SignInResult.Error("Escolha a mesma Conta Google que está conectada.")
            } else {
                mapped
            }
        }
    }

    suspend fun signOut() {
        if (!isConfigured) return
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun mapError(e: Exception): SignInResult = when (e) {
        is GetCredentialCancellationException -> SignInResult.Canceled
        is NoCredentialException -> SignInResult.Error("$GOOGLE_FAIL_PREFIX Nenhuma Conta Google disponível neste aparelho.")
        is GetCredentialException -> SignInResult.Error(
            "$GOOGLE_FAIL_PREFIX Verifique a internet e tente de novo. (${e.type})",
        )
        is GoogleIdTokenParsingException -> SignInResult.Error("Resposta inválida do Google. Tente novamente.")
        is FirebaseNetworkException -> SignInResult.Error("Sem conexão com a internet.")
        else -> SignInResult.Error(e.message ?: "Não foi possível entrar.")
    }
}
