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

/** Planos do MusiBox Pró (cobrados pela Google Play). */
enum class ProPlan(
    val title: String,
    val fallbackPrice: String,
    val period: String,
    val detail: String,
    val productId: String,
    val basePlanId: String?,
) {
    THREE_DAYS("3 dias", "R$ 4,99", "", "Pagamento único • não renova sozinho", PlayBilling.PASS_3D, null),
    WEEKLY("Semanal", "R$ 9,60", "/semana", "Renova toda semana • cancele quando quiser", PlayBilling.SUB_PRO, PlayBilling.PLAN_WEEK),
    MONTHLY("Mensal", "R$ 35,99", "/mês", "Renova todo mês • cancele quando quiser", PlayBilling.SUB_PRO, PlayBilling.PLAN_MONTH),
    ;

    val priceKey: String get() = if (basePlanId != null) "$productId:$basePlanId" else productId
}

data class ProState(
    val active: Boolean = false,
    /** Assinatura semanal/mensal ativa. */
    val subscription: Boolean = false,
    /** Assinatura renova sozinha (false = cancelada, vale até o fim do período). */
    val renews: Boolean = false,
    /** Até quando vale o passe de 3 dias (ms). */
    val passUntil: Long = 0L,
)

/**
 * Compras pela Google Play: papéis de parede (compra única, ficam para sempre na conta Google)
 * e o MusiBox Pró (assinatura semanal/mensal ou passe de 3 dias).
 * Nenhuma chave secreta fica no app; a Google cuida do pagamento.
 */
object PlayBilling : PurchasesUpdatedListener {
    private const val TAG = "MusiBoxBilling"
    const val SUB_PRO = "musibox_pro"
    const val PLAN_WEEK = "semanal"
    const val PLAN_MONTH = "mensal"
    const val PASS_3D = "pro_3dias"
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
        _pro.value = ProState(
            active = prefs.getBoolean("sub", false) || prefs.getLong("pass_until", 0L) > System.currentTimeMillis(),
            subscription = prefs.getBoolean("sub", false),
            renews = prefs.getBoolean("renews", false),
            passUntil = prefs.getLong("pass_until", 0L),
        )
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

    fun isProNow(): Boolean {
        val p = _pro.value
        return p.subscription || p.passUntil > System.currentTimeMillis()
    }

    /** O papel pode ser usado? (grátis, comprado, ou exclusivo do Pró com Pró ativo) */
    fun canUse(w: Wallpaper): Boolean = when {
        w.free -> true
        w.pro -> isProNow()
        else -> w.id in _owned.value
    }

    fun priceOf(w: Wallpaper): String = _prices.value[w.productId] ?: w.priceLabel

    fun priceOf(plan: ProPlan): String = _prices.value[plan.priceKey] ?: plan.fallbackPrice

    // ---------------------------------------------------------------- consultas

    private fun loadProducts() {
        val c = client ?: return
        val inapp = Wallpapers.all.filter { !it.free && !it.pro }.map { it.productId } + PASS_3D
        queryDetails(c, inapp, ProductType.INAPP)
        queryDetails(c, listOf(SUB_PRO), ProductType.SUBS)
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
            if (result.responseCode == BillingResponseCode.OK) handleInapp(purchases, fullList = true)
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) handleSubs(purchases)
        }
    }

    // ---------------------------------------------------------------- compras

    fun buyWallpaper(activity: Activity, w: Wallpaper, onResult: (String) -> Unit) {
        launch(activity, w.productId, null, onResult)
    }

    fun buyPro(activity: Activity, plan: ProPlan, onResult: (String) -> Unit) {
        launch(activity, plan.productId, plan.basePlanId, onResult)
    }

    private fun launch(activity: Activity, productId: String, basePlanId: String?, onResult: (String) -> Unit) {
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
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(builder.build())).build()
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
                handleInapp(list.filter { p -> p.products.none { it == SUB_PRO } }, fullList = false)
                val subs = list.filter { p -> p.products.any { it == SUB_PRO } }
                if (subs.isNotEmpty()) handleSubs(subs)
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

    private const val UNAVAILABLE_MSG =
        "A Google Play não está disponível neste aparelho agora. As compras funcionam no app baixado pela Play Store."

    // ---------------------------------------------------------------- processamento

    private fun handleInapp(purchases: List<Purchase>, fullList: Boolean) {
        val c = client ?: return
        val owned = if (fullList) mutableSetOf() else _owned.value.toMutableSet()
        purchases.forEach { p ->
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
            p.products.forEach { product ->
                when {
                    product == PASS_3D -> grantPass(p)
                    product.startsWith("wp_") -> Wallpapers.byProduct(product)?.let { owned += it.id }
                }
            }
            if (p.products.contains(PASS_3D)) {
                c.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { _, _ -> }
            } else if (!p.isAcknowledged) {
                c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
            }
        }
        _owned.value = owned
        save()
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

    private fun handleSubs(purchases: List<Purchase>) {
        val c = client ?: return
        val sub = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(SUB_PRO) }
        sub?.takeIf { !it.isAcknowledged }?.let {
            c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) { }
        }
        _pro.update {
            it.copy(
                subscription = sub != null,
                renews = sub?.isAutoRenewing == true,
                active = sub != null || it.passUntil > System.currentTimeMillis(),
            )
        }
        save()
    }

    private fun save() {
        val p = _pro.value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putStringSet("owned", _owned.value)
            ?.putBoolean("sub", p.subscription)
            ?.putBoolean("renews", p.renews)
            ?.apply()
    }

    /** Página da Play Store para cancelar/trocar a assinatura. */
    fun manageSubscriptionIntent(context: Context): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions?sku=$SUB_PRO&package=${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
