package com.musibox.app.browser

import android.content.Context
import android.webkit.WebResourceResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream

/**
 * Bloqueador de anúncios e rastreadores do navegador (recurso do Pró).
 * Bloqueia pedidos para domínios conhecidos de propaganda/rastreamento e esconde
 * os espaços vazios que sobram na página. Só atua dentro do navegador do MusiBox.
 */
object AdBlocker {
    private val DOMAINS: Set<String> = """
        doubleclick.net googlesyndication.com googleadservices.com google-analytics.com googletagservices.com
        adservice.google.com adservice.google.com.br pagead2.googlesyndication.com imasdk.googleapis.com
        googletagmanager.com analytics.google.com app-measurement.com
        amazon-adsystem.com aax.amazon-adsystem.com adsystem.amazon.com
        taboola.com outbrain.com outbrainimg.com revcontent.com mgid.com zergnet.com
        criteo.com criteo.net adnxs.com adnxs-simple.com rubiconproject.com pubmatic.com openx.net
        casalemedia.com indexww.com smartadserver.com sascdn.com adform.net adformdsp.net
        adsrvr.org bidswitch.net yieldmo.com 33across.com sharethrough.com teads.tv triplelift.com
        3lift.com media.net contextual.media.net quantserve.com quantcount.com scorecardresearch.com
        moatads.com moatpixel.com adsafeprotected.com doubleverify.com serving-sys.com flashtalking.com
        advertising.com adtechus.com yahoo-ads.com ads.yahoo.com gemini.yahoo.com
        popads.net popcash.net propellerads.com propellerclick.com onclickads.net adcash.com
        exoclick.com exosrv.com juicyads.com trafficjunky.net trafficjunky.com adsterra.com
        hilltopads.net clickadu.com admaven.com mondiad.net a-ads.com coinzilla.com
        hotjar.com mouseflow.com fullstory.com clarity.ms crazyegg.com luckyorange.com
        adroll.com rlcdn.com bluekai.com demdex.net everesttech.net krxd.net exelator.com
        agkn.com tapad.com crwdcntrl.net eyeota.net lijit.com sovrn.com gumgum.com
        undertone.com conversantmedia.com mathtag.com turn.com simpli.fi spotxchange.com spotx.tv
        springserve.com unrulymedia.com vidoomy.com primis.tech connatix.com ex.co
        ads.tiktok.com analytics.tiktok.com ads-api.tiktok.com
        ads.twitter.com static.ads-twitter.com ads.linkedin.com px.ads.linkedin.com
        adcolony.com applovin.com applvn.com unityads.unity3d.com startappservice.com
        inmobi.com chartboost.com vungle.com ironsrc.com supersonicads.com
        branch.io app.adjust.com adjust.com appsflyer.com kochava.com
        segment.io mixpanel.com amplitude.com newrelic.com nr-data.net
        zemanta.com nativo.com plista.com ligatus.com adition.com
        tradedoubler.com awin1.com cj.com anrdoezrs.net dpbolvw.net
        ad.doubleclick.net securepubads.g.doubleclick.net static.doubleclick.net
        pixel.facebook.com an.facebook.com
        uol-ads.com.br ads.uol.com.br adsplay.com.br navdmp.com navegg.com
        tail.digital tailtarget.com
    """.trim().split(Regex("\\s+")).toSet()

    /** CSS que some com os espaços de anúncio que sobram vazios. */
    val HIDE_CSS = """
        ins.adsbygoogle, .adsbygoogle, [id^="google_ads"], [id^="div-gpt-ad"], iframe[src*="doubleclick"],
        iframe[src*="googlesyndication"], .ad-slot, .ad-container, .ad-banner, .advertisement, .publicidade,
        [class*="taboola"], [id*="taboola"], [class*="outbrain"], [id*="outbrain"], .OUTBRAIN
        { display: none !important; }
    """.trimIndent().replace("\n", " ")

    val HIDE_JS = "(function(){try{var s=document.createElement('style');s.textContent=" +
        org.json.JSONObject.quote(HIDE_CSS) + ";(document.head||document.documentElement).appendChild(s);}catch(e){}})();"

    private val _blocked = MutableStateFlow(0)
    /** Quantos pedidos foram bloqueados na página atual. */
    val blocked: StateFlow<Int> = _blocked.asStateFlow()

    fun resetCount() {
        _blocked.value = 0
    }

    /** O endereço é de propaganda/rastreador? (confere o domínio e os domínios "pai") */
    fun isAd(host: String?): Boolean {
        var h = host?.lowercase()?.trimEnd('.') ?: return false
        while (true) {
            if (h in DOMAINS) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.lastIndexOf('.')) return false
            h = h.substring(dot + 1)
        }
    }

    /** Resposta vazia que substitui o anúncio bloqueado. */
    fun emptyResponse(): WebResourceResponse {
        _blocked.value = _blocked.value + 1
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    // ---------------------------------------------------------------- preferências do navegador

    private const val PREFS = "browser_prefs"

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("adblock", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("adblock", on).apply()
    }

    fun darkSites(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("dark_sites", false)

    fun setDarkSites(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("dark_sites", on).apply()
    }

    fun privateLock(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("private_lock", false)

    fun setPrivateLock(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("private_lock", on).apply()
    }
}
