package com.musibox.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.musibox.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads grátis por ciclo: a pessoa baixa 2 vídeos/músicas, assiste a 1 anúncio e
 * ganha mais 2 — e assim por diante. Quem é Pró não vê anúncio.
 */
object DownloadQuota {
    const val PER_CYCLE = 2
    private const val PREFS = "download_quota"
    private const val KEY = "credits"

    private val _credits = MutableStateFlow(PER_CYCLE)
    val credits: StateFlow<Int> = _credits.asStateFlow()

    fun load(context: Context) {
        _credits.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, PER_CYCLE)
    }

    private fun save(context: Context, value: Int) {
        _credits.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, value).apply()
    }

    /** Usa 1 download grátis. Retorna false se acabaram. */
    fun consume(context: Context): Boolean {
        val c = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, PER_CYCLE)
        if (c <= 0) return false
        save(context, c - 1)
        return true
    }

    fun refill(context: Context) = save(context, PER_CYCLE)
}

/** Anúncio premiado (AdMob) que libera mais downloads. */
object RewardedAds {
    private const val TAG = "MusiBoxAds"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var initialized = false
    private var ad: RewardedAd? = null
    private var loading = false
    private val waiting = ArrayList<(RewardedAd?) -> Unit>()

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { MobileAds.initialize(app) {} } }
                .onFailure { Log.w(TAG, "Falha ao iniciar AdMob", it) }
        }
    }

    /** Já deixa um anúncio carregado para não fazer a pessoa esperar. */
    fun preload(context: Context) {
        if (ad != null || loading) return
        load(context.applicationContext) {}
    }

    private fun load(context: Context, onReady: (RewardedAd?) -> Unit) {
        val cached = ad
        if (cached != null) {
            onReady(cached)
            return
        }
        waiting += onReady
        if (loading) return
        loading = true
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    loading = false
                    ad = loaded
                    val list = waiting.toList()
                    waiting.clear()
                    list.forEach { it(loaded) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    Log.w(TAG, "Anúncio não carregou: ${error.code} ${error.message}")
                    val list = waiting.toList()
                    waiting.clear()
                    list.forEach { it(null) }
                }
            },
        )
    }

    /**
     * Mostra o anúncio. [onReward] quando a pessoa assiste até ganhar a recompensa;
     * [onUnavailable] se não houver anúncio (sem internet ou sem anúncio disponível);
     * [onClosed] quando o anúncio fecha sem recompensa.
     */
    fun show(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit, onClosed: () -> Unit) {
        load(activity.applicationContext) { loaded ->
            if (loaded == null) {
                onUnavailable()
                return@load
            }
            ad = null
            var rewarded = false
            loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (rewarded) onReward() else onClosed()
                    preload(activity)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Anúncio não abriu: ${error.message}")
                    onUnavailable()
                    preload(activity)
                }
            }
            loaded.show(activity) { rewarded = true }
        }
    }
}
