package com.musibox.app.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.musibox.app.themes.Wallpaper
import com.musibox.app.themes.Wallpapers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Nível do plano ativo (do menor para o maior). Todos liberam os recursos extras; mudam os temas exclusivos. */
enum class Plan(val label: String, val key: String) {
    NONE("", ""),
    GO("Go", "go"),
    PLUS("Plus", "plus"),
    PRO("Pro", "pro"),
}

/** Planos do MusiBox (cobrados pela Google Play): Go (3 dias), Plus (semanal) e Pro (mensal). */
enum class ProPlan(
    val title: String,
    val fallbackPrice: String,
    val period: String,
    val detail: String,
    val productId: String,
    val basePlanId: String?,
    val tier: Plan,
) {
    GO("Go", "R$ 2,99", "/3 dias", "Pagamento único • vale 3 dias • não renova", PlayBilling.PASS_3D, null, Plan.GO),
    PLUS("Plus", "R$ 5,99", "/semana", "Renova toda semana • cancele quando quiser", PlayBilling.SUB_PLUS, PlayBilling.PLAN_WEEK, Plan.PLUS),
    PRO("Pro", "R$ 18,99", "/mês", "Renova todo mês • cancele quando quiser", PlayBilling.SUB_PRO, PlayBilling.PLAN_MONTH, Plan.PRO),
    ;

    val priceKey: String get() = if (basePlanId != null) "$productId:$basePlanId" else productId
}

data class ProState(
    val active: Boolean = false,
    /** Alguma assinatura (Plus ou Pro) ativa. */
    val subscription: Boolean = false,
    /** Assinatura renova sozinha (false = cancelada, vale até o fim do período). */
    val renews: Boolean = false,
    /** Até quando vale o Go, o passe de 3 dias (ms). */
    val passUntil: Long = 0L,
    val subPro: Boolean = false,
    val subPlus: Boolean = false,
    /** Conta do dono do app (acesso total, sem cobrança). */
    val owner: Boolean = false,
) {
    /** Algum plano ativo (Go, Plus, Pro ou conta do dono). */
    val isActive: Boolean
        get() = owner || subscription || passUntil > System.currentTimeMillis()

    /** Maior plano ativo agora. */
    val plan: Plan
        get() = when {
            owner || subPro -> Plan.PRO
            subPlus -> Plan.PLUS
            passUntil > System.currentTimeMillis() -> Plan.GO
            else -> Plan.NONE
        }
}

/**
 * Compras pela Google Play: papéis de parede (compra única, ficam para sempre na conta Google)
 * e os planos Go (3 dias), Plus (semanal) e Pro (mensal).
 * Nenhuma chave secreta fica no app; a Google cuida do pagamento.
 */
object PlayBilling : PurchasesUpdatedListener {
    private const val TAG = "MusiBoxBilling"
    const val SUB_PRO = "musibox_pro"
    const val SUB_PLUS = "musibox_plus"
    const val PLAN_WEEK = "semanal"
    const val PLAN_MONTH = "mensal"
    const val PASS_3D = "pro_3dias"
    private val SUBS = setOf(SUB_PRO, SUB_PLUS)
    private const val THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000
    private const val PREFS = "billing_cache"

    enum class Status { CONNECTING, READY, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.CONNECTING)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Preço vindo da Play Store: chave = productId ou "productId:basePlan". */
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    /** Papéis de parede comprados (ids). */
    private val _owned = MutableStateFlow<Set<String>>(emptySet())
    val owned: StateFlow<Set<String>> = _owned.asStateFlow()

    private val _pro = MutableStateFlow(ProState())
    val pro: StateFlow<ProState> = _pro.asStateFlow()

    /** true depois que a Play confirmou as compras (papéis e assinatura) nesta abertura do app. */
    private val _synced = MutableStateFlow(false)
    val synced: StateFlow<Boolean> = _synced.asStateFlow()
    @Volatile private var inappOk = false
    @Volatile private var subsOk = false

    private var client: BillingClient? = null
    private var appContext: Context? = null
    private val details = mutableMapOf<String, ProductDetails>()
    private var pendingResult: ((String) -> Unit)? = null

    fun start(context: Context) {
        if (client != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Último estado conhecido (funciona sem internet)
        _owned.value = prefs.getStringSet("owned", emptySet())!!.toSet()
        val subPro = prefs.getBoolean("sub_pro", prefs.getBoolean("sub", false))
        val subPlus = prefs.getBoolean("sub_plus", false)
        _pro.value = ProState(
            active = subPro || subPlus || prefs.getLong("pass_until", 0L) > System.currentTimeMillis(),
            subscription = subPro || subPlus,
            renews = prefs.getBoolean("renews", false),
            passUntil = prefs.getLong("pass_until", 0L),
            subPro = subPro,
            subPlus = subPlus,
        )
        if (firebaseReady()) {
            runCatching {
                com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { a ->
                    checkOwner(a.currentUser)
                    a.currentUser?.uid?.let { loadOwnedFromCloud(it) }
                }
            }
        }
        client = runCatching {
            BillingClient.newBuilder(ctx)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()
        }.getOrNull()
        connect()
    }

    private fun connect() {
        val c = client ?: run { _status.value = Status.UNAVAILABLE; return }
        _status.value = Status.CONNECTING
        runCatching {
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingResponseCode.OK) {
                        _status.value = Status.READY
                        refresh()
                        loadProducts()
                    } else {
                        Log.w(TAG, "Play Billing indisponível: ${result.debugMessage}")
                        _status.value = Status.UNAVAILABLE
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // enableAutoServiceReconnection() reconecta sozinho
                }
            })
        }.onFailure { _status.value = Status.UNAVAILABLE }
    }

    fun isProNow(): Boolean = _pro.value.isActive

    fun currentPlan(): Plan = _pro.value.plan

    /** O plano atual é pelo menos [min]? (ex.: Plus e Pro para o volume extra) */
    fun hasPlan(min: Plan): Boolean = currentPlan().ordinal >= min.ordinal

    fun isOwner(): Boolean = _pro.value.owner

    /** Conta do dono: guardamos só o SHA-256 do e-mail (o repositório é público). */
    private const val OWNER_HASH = "15f6c06e3cc37a7c991f5fd7e67a65c34c65c01ed70595158bdc65b61547a9fd"

    private fun checkOwner(user: com.google.firebase.auth.FirebaseUser?) {
        val email = user?.email?.trim()?.lowercase()
        val google = user?.providerData?.any { it.providerId == "google.com" } == true
        val isOwner = email != null && google && sha256(email) == OWNER_HASH
        if (_pro.value.owner != isOwner) _pro.update { it.copy(owner = isOwner) }
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** O plano libera este tema exclusivo? O Pro libera todos; Plus e Go só os da sua lista. */
    fun planUnlocks(plan: Plan, w: Wallpaper): Boolean = when (plan) {
        Plan.PRO -> w.pro
        Plan.PLUS -> "plus" in w.plans
        Plan.GO -> "go" in w.plans
        Plan.NONE -> false
    }

    /** O papel pode ser usado? (grátis, comprado, ou exclusivo de um plano que a pessoa tem) */
    fun canUse(w: Wallpaper): Boolean = when {
        _pro.value.owner -> true
        w.free -> true
        w.pro -> planUnlocks(currentPlan(), w)
        else -> w.id in _owned.value
    }

    fun priceOf(w: Wallpaper): String = _prices.value[w.productId] ?: w.priceLabel

    fun priceOf(plan: ProPlan): String = _prices.value[plan.priceKey] ?: plan.fallbackPrice

    // ---------------------------------------------------------------- consultas

    private fun loadProducts() {
        val c = client ?: return
        val inapp = Wallpapers.tierProducts + PASS_3D
        queryDetails(c, inapp, ProductType.INAPP)
        queryDetails(c, SUBS.toList(), ProductType.SUBS)
    }

    private fun queryDetails(c: BillingClient, ids: List<String>, type: String) {
        val products = ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        c.queryProductDetailsAsync(params) { result, query ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            val found = mutableMapOf<String, String>()
            query.productDetailsList.forEach { pd ->
                details[pd.productId] = pd
                pd.oneTimePurchaseOfferDetails?.let { found[pd.productId] = it.formattedPrice }
                pd.subscriptionOfferDetails?.forEach { offer ->
                    val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@forEach
                    val key = "${pd.productId}:${offer.basePlanId}"
                    if (offer.offerId == null || key !in found) found[key] = phase.formattedPrice
                }
            }
            _prices.update { it + found }
        }
    }

    /** Lê de novo o que a pessoa já comprou (ao abrir o app e ao voltar para ele). */
    fun refresh() {
        val c = client ?: return
        if (_status.value != Status.READY) return
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                handleInapp(purchases, fullList = true)
                inappOk = true
                _synced.value = inappOk && subsOk
            }
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                handleSubs(purchases, full = true)
                subsOk = true
                _synced.value = inappOk && subsOk
            }
        }
    }

    // ---------------------------------------------------------------- compras

    /**
     * Compra um papel. Cada faixa de preço é um produto da Play; o papel escolhido vai junto
     * na compra e fica salvo na conta Google da pessoa (Firestore), então volta ao reinstalar.
     */
    fun buyWallpaper(activity: Activity, w: Wallpaper, onResult: (String) -> Unit) {
        val uid = currentUid()
        if (uid == null) {
            onResult(LOGIN_MSG)
            return
        }
        launch(activity, w.productId, null, onResult, accountId = hashUid(uid), profileId = w.id)
    }

    fun buyPro(activity: Activity, plan: ProPlan, onResult: (String) -> Unit) {
        launch(activity, plan.productId, plan.basePlanId, onResult)
    }

    private fun launch(
        activity: Activity,
        productId: String,
        basePlanId: String?,
        onResult: (String) -> Unit,
        accountId: String? = currentUid()?.let { hashUid(it) },
        profileId: String? = null,
    ) {
        val c = client
        if (c == null || _status.value != Status.READY) {
            onResult(UNAVAILABLE_MSG)
            if (_status.value == Status.UNAVAILABLE) connect()
            return
        }
        val pd = details[productId]
        if (pd == null) {
            onResult("Este item ainda não está à venda. As compras funcionam quando o MusiBox estiver publicado na Play Store.")
            loadProducts()
            return
        }
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        if (basePlanId != null) {
            val offer = pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId && it.offerId == null }
                ?: pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId }
            if (offer == null) {
                onResult("Plano indisponível no momento.")
                return
            }
            builder.setOfferToken(offer.offerToken)
        }
        val flowBuilder = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(builder.build()))
        accountId?.let { flowBuilder.setObfuscatedAccountId(it) }
        profileId?.let { flowBuilder.setObfuscatedProfileId(it) }
        val flow = flowBuilder.build()
        pendingResult = onResult
        val r = c.launchBillingFlow(activity, flow)
        if (r.responseCode != BillingResponseCode.OK) {
            pendingResult = null
            onResult(messageFor(r))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val callback = pendingResult
        pendingResult = null
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                handleInapp(list.filter { p -> p.products.none { it in SUBS } }, fullList = false)
                val subs = list.filter { p -> p.products.any { it in SUBS } }
                if (subs.isNotEmpty()) handleSubs(subs, full = false)
                val pending = list.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                callback?.invoke(if (pending) "Pagamento pendente. Assim que for aprovado, a compra é liberada." else "Compra concluída! Obrigado.")
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refresh()
                callback?.invoke("Você já tem este item. Ele foi liberado de novo.")
            }
            else -> callback?.invoke(messageFor(result))
        }
    }

    private fun messageFor(r: BillingResult): String = when (r.responseCode) {
        BillingResponseCode.USER_CANCELED -> "Compra cancelada."
        BillingResponseCode.ITEM_UNAVAILABLE -> "Este item ainda não está à venda na Play Store."
        BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.SERVICE_DISCONNECTED -> UNAVAILABLE_MSG
        BillingResponseCode.NETWORK_ERROR -> "Sem internet. Tente de novo."
        else -> "Não foi possível concluir a compra (${r.responseCode})."
    }

    const val LOGIN_MSG = "Entre com sua conta Google para comprar. Assim o papel fica salvo na sua conta para sempre."

    private const val UNAVAILABLE_MSG =
        "A Google Play não está disponível neste aparelho agora. As compras funcionam no app baixado pela Play Store."

    // ---------------------------------------------------------------- processamento

    @Suppress("UNUSED_PARAMETER")
    private fun handleInapp(purchases: List<Purchase>, fullList: Boolean) {
        val c = client ?: return
        purchases.forEach { p ->
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
            when {
                p.products.contains(PASS_3D) -> {
                    grantPass(p)
                    c.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { _, _ -> }
                }
                p.products.any { it.startsWith("papel_") } -> {
                    val id = p.accountIdentifiers?.obfuscatedProfileId
                    val w = Wallpapers.get(id)
                    if (w == null) {
                        // compra sem papel identificado: não consome (fica para o suporte resolver)
                        Log.w(TAG, "Compra de papel sem identificação: ${p.orderId}")
                        return@forEach
                    }
                    addOwned(setOf(w.id))
                    // Salva na conta e só então consome (se falhar, tenta de novo na próxima abertura)
                    saveOwnedToCloud(w.id) { ok ->
                        if (ok) c.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { _, _ -> }
                    }
                }
                !p.isAcknowledged -> {
                    c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
                }
            }
        }
        save()
    }

    private fun addOwned(ids: Set<String>) {
        if (ids.isEmpty() || _owned.value.containsAll(ids)) return
        _owned.update { it + ids }
        save()
    }

    // ---------------------------------------------------------------- conta (Firestore)

    private fun firebaseReady(): Boolean = appContext?.let { com.google.firebase.FirebaseApp.getApps(it).isNotEmpty() } == true

    private fun currentUid(): String? =
        if (firebaseReady()) runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() else null

    private fun hashUid(uid: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun wallpapersDoc(uid: String) =
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("appData").document("wallpapers")

    private fun saveOwnedToCloud(id: String, done: (Boolean) -> Unit) {
        val uid = currentUid()
        if (uid == null) {
            done(!firebaseReady()) // sem Firebase no app: guarda só no aparelho
            return
        }
        runCatching {
            wallpapersDoc(uid).set(
                mapOf(
                    "owned" to com.google.firebase.firestore.FieldValue.arrayUnion(id),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).addOnCompleteListener { done(it.isSuccessful) }
        }.onFailure { done(false) }
    }

    /** Traz da conta os papéis já comprados (ao entrar com Google ou abrir o app). */
    private fun loadOwnedFromCloud(uid: String) {
        runCatching {
            wallpapersDoc(uid).get().addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val ids = (doc.get("owned") as? List<String>).orEmpty().filter { Wallpapers.get(it) != null }.toSet()
                addOwned(ids)
                (doc.get("freeClaims") as? Map<String, Any?>)?.keys?.let { months ->
                    appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.apply {
                        months.forEach { putBoolean("free_claim_$it", true) }
                    }?.apply()
                    _claimTick.update { it + 1 }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 1 tema pago grátis por mês (Pro)

    private val _claimTick = MutableStateFlow(0)
    /** Muda quando o tema grátis do mês é usado (para a tela atualizar). */
    val claimTick: StateFlow<Int> = _claimTick.asStateFlow()

    private fun monthKey(): String = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())

    /** O Pro ainda pode pegar o tema grátis deste mês? */
    fun freeClaimAvailable(): Boolean {
        if (currentPlan() != Plan.PRO || _pro.value.owner) return false
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return false
        return !prefs.getBoolean("free_claim_${monthKey()}", false)
    }

    /** Pega um tema pago de graça (1 por mês no Pro). Fica na conta Google para sempre. */
    fun claimFreeTheme(w: Wallpaper, onResult: (String) -> Unit) {
        if (!freeClaimAvailable()) {
            onResult("O tema grátis deste mês já foi usado. Mês que vem tem outro!")
            return
        }
        if (w.free || w.pro || w.id in _owned.value) {
            onResult("Escolha um tema pago que você ainda não tem.")
            return
        }
        val uid = currentUid()
        if (uid == null) {
            onResult(LOGIN_MSG)
            return
        }
        val month = monthKey()
        runCatching {
            wallpapersDoc(uid).set(
                mapOf(
                    "owned" to com.google.firebase.firestore.FieldValue.arrayUnion(w.id),
                    "freeClaims" to mapOf(month to w.id),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("free_claim_$month", true)?.apply()
                    addOwned(setOf(w.id))
                    _claimTick.update { it + 1 }
                    onResult("Pronto! ${w.name} agora é seu, de graça.")
                } else {
                    onResult("Sem conexão. Tente de novo em instantes.")
                }
            }
        }.onFailure { onResult("Não foi possível liberar agora.") }
    }

    /** Passe de 3 dias: soma 3 dias (uma vez por compra). */
    private fun grantPass(p: Purchase) {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        val done = prefs.getStringSet("pass_tokens", emptySet())!!.toMutableSet()
        if (p.purchaseToken in done) return
        done += p.purchaseToken
        val start = maxOf(System.currentTimeMillis(), _pro.value.passUntil)
        val until = start + THREE_DAYS_MS
        prefs.edit().putStringSet("pass_tokens", done).putLong("pass_until", until).apply()
        _pro.update { it.copy(passUntil = until, active = true) }
    }

    /** full = lista completa da Play (ao abrir); senão, só as compras novas (soma ao que já havia). */
    private fun handleSubs(purchases: List<Purchase>, full: Boolean) {
        val c = client ?: return
        val ok = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any { p -> p in SUBS } }
        ok.filter { !it.isAcknowledged }.forEach {
            c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) { }
        }
        val pro = ok.firstOrNull { it.products.contains(SUB_PRO) }
        val plus = ok.firstOrNull { it.products.contains(SUB_PLUS) }
        _pro.update {
            val subPro = pro != null || (!full && it.subPro)
            val subPlus = plus != null || (!full && it.subPlus)
            val top = pro ?: plus
            it.copy(
                subPro = subPro,
                subPlus = subPlus,
                subscription = subPro || subPlus,
                renews = top?.isAutoRenewing ?: (!full && it.renews),
                active = subPro || subPlus || it.passUntil > System.currentTimeMillis(),
            )
        }
        save()
    }

    private fun save() {
        val p = _pro.value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putStringSet("owned", _owned.value)
            ?.putBoolean("sub", p.subscription)
            ?.putBoolean("sub_pro", p.subPro)
            ?.putBoolean("sub_plus", p.subPlus)
            ?.putBoolean("renews", p.renews)
            ?.apply()
    }

    /** Página da Play Store para cancelar/trocar a assinatura. */
    fun manageSubscriptionIntent(context: Context): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions?sku=${if (_pro.value.subPro) SUB_PRO else SUB_PLUS}&package=${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
