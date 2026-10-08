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
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
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
        is NoCredentialException -> SignInResult.Error("Nenhuma Conta Google disponível neste aparelho.")
        is GetCredentialException -> SignInResult.Error(
            "Não foi possível entrar com o Google. Verifique a internet e se o app ${BuildConfig.APPLICATION_ID} " +
                "está cadastrado no Firebase com a impressão digital SHA-1. (${e.type})",
        )
        is GoogleIdTokenParsingException -> SignInResult.Error("Resposta inválida do Google. Tente novamente.")
        is FirebaseNetworkException -> SignInResult.Error("Sem conexão com a internet.")
        else -> SignInResult.Error(e.message ?: "Não foi possível entrar.")
    }
}
