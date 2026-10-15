package com.musibox.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import com.musibox.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** Vídeo premiado (Start.io) que libera mais downloads. */
object RewardedAds {
    private const val TAG = "MusiBoxAds"
    @Volatile private var initialized = false
    private var ad: StartAppAd? = null
    private var ready = false
    private var loading = false
    private val waiting = ArrayList<(StartAppAd?) -> Unit>()

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        runCatching {
            // O App ID e o "sem anúncio ao abrir/voltar" vêm do AndroidManifest.
            StartAppSDK.setTestAdsEnabled(BuildConfig.ADS_TEST)
        }.onFailure { Log.w(TAG, "Falha ao configurar anúncios", it) }
    }

    /** Já deixa um vídeo carregado para não fazer a pessoa esperar. */
    fun preload(context: Context) {
        if (ready || loading) return
        load(context.applicationContext) {}
    }

    private fun load(context: Context, onReady: (StartAppAd?) -> Unit) {
        val cached = ad
        if (cached != null && ready) {
            onReady(cached)
            return
        }
        waiting += onReady
        if (loading) return
        loading = true
        val fresh = StartAppAd(context)
        ad = fresh
        runCatching {
            fresh.loadAd(
                StartAppAd.AdMode.REWARDED_VIDEO,
                object : AdEventListener {
                    override fun onReceiveAd(loaded: Ad) {
                        loading = false
                        ready = true
                        val list = waiting.toList()
                        waiting.clear()
                        list.forEach { it(fresh) }
                    }

                    override fun onFailedToReceiveAd(failed: Ad?) {
                        loading = false
                        ready = false
                        ad = null
                        Log.w(TAG, "Vídeo não carregou")
                        val list = waiting.toList()
                        waiting.clear()
                        list.forEach { it(null) }
                    }
                },
            )
        }.onFailure {
            loading = false
            ready = false
            ad = null
            Log.w(TAG, "Falha ao carregar vídeo", it)
            val list = waiting.toList()
            waiting.clear()
            list.forEach { cb -> cb(null) }
        }
    }

    /**
     * Mostra o vídeo. [onReward] quando a pessoa assiste até o fim; [onUnavailable] se não houver
     * vídeo (sem internet ou sem anúncio); [onClosed] quando fecha antes do fim.
     */
    fun show(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit, onClosed: () -> Unit) {
        load(activity.applicationContext) { loaded ->
            if (loaded == null) {
                onUnavailable()
                return@load
            }
            ad = null
            ready = false
            var rewarded = false
            loaded.setVideoListener(VideoListener { rewarded = true })
            val shown = runCatching {
                loaded.showAd(object : AdDisplayListener {
                    override fun adHidden(a: Ad?) {
                        if (rewarded) onReward() else onClosed()
                        preload(activity)
                    }

                    override fun adDisplayed(a: Ad?) = Unit
                    override fun adClicked(a: Ad?) = Unit
                    override fun adNotDisplayed(a: Ad?) {
                        onUnavailable()
                        preload(activity)
                    }
                })
            }.getOrDefault(false)
            if (!shown) {
                onUnavailable()
                preload(activity)
            }
        }
    }
}
