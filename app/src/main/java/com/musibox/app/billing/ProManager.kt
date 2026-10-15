package com.musibox.app.billing

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.musibox.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Planos do MusiBox Pró (os preços ficam cadastrados na Stripe). */
enum class ProPlan(
    val id: String,
    val title: String,
    val price: String,
    val period: String,
    val detail: String,
    val priceId: String,
    val oneTime: Boolean,
) {
    THREE_DAYS("3d", "3 dias", "R$ 4,99", "", "Pagamento único • não renova sozinho", BuildConfig.STRIPE_PRICE_3D, true),
    WEEKLY("week", "Semanal", "R$ 9,60", "/semana", "Renova toda semana • cancele quando quiser", BuildConfig.STRIPE_PRICE_WEEK, false),
    MONTHLY("month", "Mensal", "R$ 35,99", "/mês", "Renova todo mês • cancele quando quiser", BuildConfig.STRIPE_PRICE_MONTH, false),
}

data class ProState(
    val signedIn: Boolean = false,
    val active: Boolean = false,
    /** Até quando o Pró vale (ms). */
    val until: Long = 0L,
    /** true = assinatura que renova sozinha; false = passe ou assinatura cancelada. */
    val renews: Boolean = false,
    val fromSubscription: Boolean = false,
)

/**
 * Situação do Pró da pessoa, lida do Firestore (a extensão "Run Payments with Stripe" grava lá
 * as assinaturas e os pagamentos). Nenhuma chave da Stripe fica no aplicativo.
 */
object ProManager {
    private const val TAG = "MusiBoxPro"
    private const val PREFS = "pro_cache"
    private const val THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

    private val _state = MutableStateFlow(ProState())
    val state: StateFlow<ProState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var subsReg: ListenerRegistration? = null
    private var payReg: ListenerRegistration? = null
    private var subUntil = 0L
    private var subRenews = false
    private var passUntil = 0L
    private var started = false

    private val firebaseReady: Boolean
        get() = appContext?.let { FirebaseApp.getApps(it).isNotEmpty() } == true

    /** Pagamentos já configurados (IDs dos preços da Stripe no app). */
    val paymentsConfigured: Boolean
        get() = ProPlan.entries.any { it.priceId.isNotBlank() }

    private val returnBase: String
        get() = "https://${BuildConfig.FB_PROJECT_ID.ifBlank { "musibox" }}.web.app/pro"
    val successUrl: String get() = "$returnBase/sucesso"
    val cancelUrl: String get() = "$returnBase/cancelado"

    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        // Último estado conhecido (funciona sem internet)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedUntil = prefs.getLong("until", 0L)
        if (cachedUntil > System.currentTimeMillis()) {
            _state.value = ProState(signedIn = true, active = true, until = cachedUntil, renews = prefs.getBoolean("renews", false))
        }
        if (!firebaseReady) return
        runCatching {
            FirebaseAuth.getInstance().addAuthStateListener { auth -> attach(auth.currentUser?.uid) }
        }.onFailure { Log.w(TAG, "Pró indisponível", it) }
    }

    /** Pró vale agora? (confere a data na hora, para o passe de 3 dias expirar certinho) */
    fun isProNow(): Boolean {
        val s = _state.value
        return s.active && (s.renews || s.until > System.currentTimeMillis())
    }

    private fun attach(uid: String?) {
        subsReg?.remove()
        payReg?.remove()
        subsReg = null
        payReg = null
        subUntil = 0L
        passUntil = 0L
        subRenews = false
        if (uid == null) {
            publish(signedIn = false)
            return
        }
        val customer = FirebaseFirestore.getInstance().collection("customers").document(uid)
        subsReg = customer.collection("subscriptions")
            .whereIn("status", listOf("trialing", "active"))
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "Assinaturas: ${err.message}")
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                val best = docs.maxByOrNull { it.getTimestamp("current_period_end")?.toDate()?.time ?: 0L }
                subUntil = best?.getTimestamp("current_period_end")?.toDate()?.time ?: if (best != null) Long.MAX_VALUE else 0L
                subRenews = best != null && best.getBoolean("cancel_at_period_end") != true
                publish(signedIn = true)
            }
        payReg = customer.collection("payments")
            .whereEqualTo("status", "succeeded")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "Pagamentos: ${err.message}")
                    return@addSnapshotListener
                }
                // Cada passe de 3 dias soma 3 dias (pagamentos de assinatura têm "invoice" e não contam aqui)
                var until = 0L
                snap?.documents.orEmpty()
                    .filter { it.get("invoice") == null }
                    .mapNotNull { it.getLong("created")?.times(1000) }
                    .sorted()
                    .forEach { created -> until = maxOf(until, created) + THREE_DAYS_MS }
                passUntil = until
                publish(signedIn = true)
            }
    }

    private fun publish(signedIn: Boolean) {
        val now = System.currentTimeMillis()
        val subActive = subUntil > now
        val passActive = passUntil > now
        val until = maxOf(if (subActive) subUntil else 0L, if (passActive) passUntil else 0L)
        val state = ProState(
            signedIn = signedIn,
            active = subActive || passActive,
            until = until,
            renews = subActive && subRenews,
            fromSubscription = subActive,
        )
        _state.value = state
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putLong("until", if (state.active) until else 0L)
            ?.putBoolean("renews", state.renews)
            ?.apply()
    }

    /** Cria a sessão de pagamento na Stripe (via extensão) e devolve o link do checkout. */
    suspend fun createCheckout(plan: ProPlan): String {
        check(firebaseReady) { "Conta Google indisponível neste aparelho." }
        check(plan.priceId.isNotBlank()) { "Os pagamentos ainda estão sendo configurados." }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Entre na sua conta para assinar.")
        val data = hashMapOf<String, Any>(
            "price" to plan.priceId,
            "success_url" to successUrl,
            "cancel_url" to cancelUrl,
            "allow_promotion_codes" to true,
            "metadata" to mapOf("plan" to plan.id, "app" to "MusiBox"),
        )
        if (plan.oneTime) data["mode"] = "payment"
        val ref = FirebaseFirestore.getInstance()
            .collection("customers").document(uid)
            .collection("checkout_sessions").add(data).await()
        return withTimeout(45_000) {
            suspendCancellableCoroutine { cont ->
                var reg: ListenerRegistration? = null
                reg = ref.addSnapshotListener { snap, err ->
                    if (!cont.isActive) return@addSnapshotListener
                    when {
                        err != null -> {
                            reg?.remove()
                            cont.resumeWithException(IllegalStateException("Não foi possível iniciar o pagamento (${err.message})."))
                        }
                        snap?.getString("url") != null -> {
                            reg?.remove()
                            cont.resume(snap.getString("url")!!)
                        }
                        snap?.get("error") != null -> {
                            reg?.remove()
                            val msg = (snap.get("error") as? Map<*, *>)?.get("message")?.toString() ?: "erro"
                            cont.resumeWithException(IllegalStateException("A Stripe recusou: $msg"))
                        }
                    }
                }
                cont.invokeOnCancellation { reg?.remove() }
            }
        }
    }

    /** Link do portal da Stripe para cancelar/trocar a assinatura. */
    suspend fun portalUrl(): String {
        val result = FirebaseFunctions.getInstance(BuildConfig.STRIPE_REGION)
            .getHttpsCallable("ext-firestore-stripe-payments-createPortalLink")
            .call(mapOf("returnUrl" to cancelUrl, "locale" to "pt-BR"))
            .await()
        return (result.data as? Map<*, *>)?.get("url") as? String ?: error("Portal indisponível.")
    }
}
