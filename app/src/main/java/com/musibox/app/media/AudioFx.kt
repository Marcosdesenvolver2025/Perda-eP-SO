package com.musibox.app.media

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ln

/** Faixas do equalizador deste aparelho. */
data class EqInfo(val centersHz: List<Int>, val minMb: Int, val maxMb: Int)

data class EqSettings(
    val enabled: Boolean = false,
    val preset: String = "Normal",
    /** Ganho de cada faixa em milibéis (100 mB = 1 dB). */
    val gains: List<Int> = emptyList(),
    /** Reforço de graves 0..1000. */
    val bass: Int = 0,
    /** Som 3D (virtualizador) 0..1000. */
    val surround: Int = 0,
    /** Volume extra acima do máximo do celular, em milibéis (0 = desligado). Plus e Pro. */
    val boostMb: Int = 0,
    /** Transição suave entre músicas (abaixa no fim e sobe no começo). Plus e Pro. */
    val smooth: Boolean = false,
)

/**
 * Equalizador, reforço de graves e som 3D (recurso do Pro), ligados à sessão de áudio do player.
 * O serviço de música chama attach(); a tela do equalizador só muda as configurações.
 */
object AudioFx {
    private const val TAG = "MusiBoxFx"
    private const val PREFS = "audio_fx"

    /** Presets próprios (dB nas frequências 60, 230, 910, 3600 e 14000 Hz). */
    val PRESETS: List<Pair<String, List<Float>>> = listOf(
        "Normal" to listOf(0f, 0f, 0f, 0f, 0f),
        "Graves+" to listOf(7f, 4.5f, 0f, 0f, 0.5f),
        "Pop" to listOf(-1f, 2f, 4f, 2f, -1f),
        "Rock" to listOf(5f, 3f, -1f, 3f, 5f),
        "Funk" to listOf(6.5f, 3.5f, -0.5f, 2f, 4f),
        "Sertanejo" to listOf(3f, 1.5f, 2f, 3f, 2f),
        "Eletrônica" to listOf(5.5f, 3f, 0f, 2.5f, 5f),
        "Hip-hop" to listOf(6f, 4f, 0f, 1.5f, 3f),
        "Vocal" to listOf(-2f, 0f, 4f, 3.5f, 0.5f),
        "Acústico" to listOf(3f, 1f, 1.5f, 2.5f, 3f),
        "Clássica" to listOf(4f, 2.5f, -1.5f, 2f, 4f),
        "Jazz" to listOf(3.5f, 2f, -1.5f, 2f, 3.5f),
    )
    private val PRESET_FREQS = listOf(60, 230, 910, 3600, 14000)

    private var appContext: Context? = null
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    /** Volume extra máximo (+15 dB). */
    const val BOOST_MAX_MB = 1500

    private val _info = MutableStateFlow<EqInfo?>(null)
    val info: StateFlow<EqInfo?> = _info.asStateFlow()

    private val _settings = MutableStateFlow(EqSettings())
    val settings: StateFlow<EqSettings> = _settings.asStateFlow()

    /** Quando false (sem Pro), os efeitos ficam desligados mesmo se "ativados". */
    @Volatile var allowed: Boolean = false
        set(value) {
            field = value
            apply()
        }

    /** Plus ou Pro ativo: libera o volume extra e a transição suave. */
    @Volatile var plusAllowed: Boolean = false
        set(value) {
            field = value
            apply()
        }

    fun smoothActive(): Boolean = plusAllowed && _settings.value.smooth

    fun setBoost(mb: Int) = update { it.copy(boostMb = mb.coerceIn(0, BOOST_MAX_MB)) }

    fun setSmooth(on: Boolean) = update { it.copy(smooth = on) }

    fun attach(context: Context, sessionId: Int) {
        appContext = context.applicationContext
        release()
        runCatching {
            val e = Equalizer(0, sessionId)
            val range = e.bandLevelRange
            val centers = (0 until e.numberOfBands).map { b -> e.getCenterFreq(b.toShort()) / 1000 }
            eq = e
            _info.value = EqInfo(centers, range[0].toInt(), range[1].toInt())
        }.onFailure { Log.w(TAG, "Equalizador indisponível", it) }
        runCatching { bassBoost = BassBoost(0, sessionId) }
        runCatching { virtualizer = Virtualizer(0, sessionId) }
        runCatching { loudness = LoudnessEnhancer(sessionId) }.onFailure { Log.w(TAG, "Volume extra indisponível", it) }
        load()
        apply()
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        eq = null
        bassBoost = null
        virtualizer = null
        loudness = null
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    fun setBand(index: Int, mb: Int) = update { s ->
        val g = currentGains(s).toMutableList()
        if (index in g.indices) g[index] = mb
        s.copy(gains = g, preset = "Personalizado", enabled = true)
    }

    fun setPreset(name: String) {
        val preset = PRESETS.firstOrNull { it.first == name } ?: return
        val info = _info.value
        val gains = info?.centersHz?.map { hz -> (interp(preset.second, hz) * 100).toInt().coerceIn(info.minMb, info.maxMb) } ?: emptyList()
        update { it.copy(preset = name, gains = gains, enabled = true) }
    }

    fun setBass(v: Int) = update { it.copy(bass = v.coerceIn(0, 1000), enabled = true) }

    fun setSurround(v: Int) = update { it.copy(surround = v.coerceIn(0, 1000), enabled = true) }

    fun reset() = update { EqSettings(enabled = it.enabled, gains = List(_info.value?.centersHz?.size ?: 0) { 0 }, boostMb = it.boostMb, smooth = it.smooth) }

    fun currentGains(s: EqSettings = _settings.value): List<Int> {
        val n = _info.value?.centersHz?.size ?: 0
        return if (s.gains.size == n) s.gains else List(n) { 0 }
    }

    private fun update(change: (EqSettings) -> EqSettings) {
        _settings.value = change(_settings.value)
        save()
        apply()
    }

    /** Ganho do preset numa frequência (interpolação em escala logarítmica). */
    private fun interp(db: List<Float>, hz: Int): Float {
        val x = ln(hz.coerceAtLeast(20).toDouble())
        val xs = PRESET_FREQS.map { ln(it.toDouble()) }
        if (x <= xs.first()) return db.first()
        if (x >= xs.last()) return db.last()
        for (i in 0 until xs.lastIndex) {
            if (x <= xs[i + 1]) {
                val t = ((x - xs[i]) / (xs[i + 1] - xs[i])).toFloat()
                return db[i] + (db[i + 1] - db[i]) * t
            }
        }
        return 0f
    }

    private fun apply() {
        val s = _settings.value
        val on = s.enabled && allowed
        runCatching {
            eq?.let { e ->
                currentGains(s).forEachIndexed { i, g -> e.setBandLevel(i.toShort(), g.toShort()) }
                e.setEnabled(on && currentGains(s).any { abs(it) > 0 })
            }
        }
        runCatching {
            bassBoost?.let { b ->
                if (b.strengthSupported) b.setStrength(s.bass.toShort())
                b.setEnabled(on && s.bass > 0)
            }
        }
        runCatching {
            virtualizer?.let { v ->
                if (v.strengthSupported) v.setStrength(s.surround.toShort())
                v.setEnabled(on && s.surround > 0)
            }
        }
        runCatching {
            loudness?.let { l ->
                val boost = if (plusAllowed) s.boostMb else 0
                l.setTargetGain(boost)
                l.setEnabled(boost > 0)
            }
        }
    }

    private fun save() {
        val s = _settings.value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean("enabled", s.enabled)
            ?.putString("preset", s.preset)
            ?.putString("gains", s.gains.joinToString(","))
            ?.putInt("bass", s.bass)
            ?.putInt("surround", s.surround)
            ?.putInt("boost", s.boostMb)
            ?.putBoolean("smooth", s.smooth)
            ?.apply()
    }

    private fun load() {
        val p = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        val gains = p.getString("gains", "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
        _settings.value = EqSettings(
            enabled = p.getBoolean("enabled", false),
            preset = p.getString("preset", "Normal") ?: "Normal",
            gains = gains,
            bass = p.getInt("bass", 0),
            surround = p.getInt("surround", 0),
            boostMb = p.getInt("boost", 0),
            smooth = p.getBoolean("smooth", false),
        )
    }

    /** Carrega as configurações salvas sem precisar do player (para a tela abrir certinha). */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            load()
        }
    }
}
