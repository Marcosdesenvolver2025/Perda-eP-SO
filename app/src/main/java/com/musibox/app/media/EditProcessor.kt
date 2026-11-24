package com.musibox.app.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Aplica o Music Edit atual no som (PCM 16 bits, mono ou estéreo), antes do volume e da velocidade.
 * Com "Normal" o som só passa direto.
 */
@OptIn(UnstableApi::class)
class EditProcessor : BaseAudioProcessor() {
    private var channels = 2
    private var applied: MusicEdit? = null
    private val dsp = EditDsp()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        dsp.sampleRate = inputAudioFormat.sampleRate.toFloat()
        applied = null
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val edit = MusicEdits.active
        if (edit != applied) {
            dsp.setup(edit, channels)
            applied = edit
        }
        if (!dsp.processing) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)
        val frames = remaining / (2 * channels)
        for (i in 0 until frames) {
            val l = input.short / 32768f
            val r = if (channels == 2) input.short / 32768f else l
            dsp.process(l, r)
            out.putShort(toPcm(dsp.outL))
            if (channels == 2) out.putShort(toPcm(dsp.outR))
        }
        // sobra (bytes soltos) passa direto
        while (input.hasRemaining()) out.put(input.get())
        out.flip()
    }

    override fun onFlush() {
        applied = null
    }

    override fun onReset() {
        applied = null
    }

    private fun toPcm(x: Float): Short {
        // limitador suave: nunca estoura nem estala
        val y = if (abs(x) <= 0.85f) x else (if (x > 0) 1f else -1f) * (0.85f + 0.15f * tanh((abs(x) - 0.85f) / 0.15f))
        return (y.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    }
}

/** Filtro biquad (fórmulas do RBJ Audio EQ Cookbook). */
internal class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun reset() {
        z1 = 0f; z2 = 0f
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
        reset()
    }

    fun lowpass(sr: Float, f: Float, q: Float = 0.707f) = apply {
        val w = 2 * PI * f.coerceAtMost(sr * 0.45f) / sr
        val c = cos(w); val a = sin(w) / (2 * q)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a)
    }

    fun highpass(sr: Float, f: Float, q: Float = 0.707f) = apply {
        val w = 2 * PI * f / sr
        val c = cos(w); val a = sin(w) / (2 * q)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a)
    }

    fun peaking(sr: Float, f: Float, gainDb: Float, q: Float = 1f) = apply {
        val aa = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * f / sr
        val c = cos(w); val a = sin(w) / (2 * q)
        set(1 + a * aa, -2 * c, 1 - a * aa, 1 + a / aa, -2 * c, 1 - a / aa)
    }

    fun lowShelf(sr: Float, f: Float, gainDb: Float) = apply {
        val aa = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * f / sr
        val c = cos(w); val s = sin(w)
        val a = s / 2 * sqrt(2.0)
        val sq = 2 * sqrt(aa) * a
        set(
            aa * ((aa + 1) - (aa - 1) * c + sq), 2 * aa * ((aa - 1) - (aa + 1) * c), aa * ((aa + 1) - (aa - 1) * c - sq),
            (aa + 1) + (aa - 1) * c + sq, -2 * ((aa - 1) + (aa + 1) * c), (aa + 1) + (aa - 1) * c - sq,
        )
    }

    fun highShelf(sr: Float, f: Float, gainDb: Float) = apply {
        val aa = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * f.coerceAtMost(sr * 0.45f) / sr
        val c = cos(w); val s = sin(w)
        val a = s / 2 * sqrt(2.0)
        val sq = 2 * sqrt(aa) * a
        set(
            aa * ((aa + 1) + (aa - 1) * c + sq), -2 * aa * ((aa - 1) + (aa + 1) * c), aa * ((aa + 1) + (aa - 1) * c - sq),
            (aa + 1) - (aa - 1) * c + sq, 2 * ((aa - 1) - (aa + 1) * c), (aa + 1) - (aa - 1) * c - sq,
        )
    }

    fun p(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

/** Reverb no estilo Freeverb (combs + allpass), estéreo. */
internal class Reverb {
    private class Comb(size: Int) {
        val buf = FloatArray(size.coerceAtLeast(1))
        var idx = 0
        var store = 0f
        fun p(x: Float, feedback: Float, damp: Float): Float {
            val out = buf[idx]
            store = out * (1 - damp) + store * damp
            buf[idx] = x + store * feedback
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private class AllPass(size: Int) {
        val buf = FloatArray(size.coerceAtLeast(1))
        var idx = 0
        fun p(x: Float): Float {
            val b = buf[idx]
            buf[idx] = x + b * 0.5f
            if (++idx >= buf.size) idx = 0
            return b - x
        }
    }

    private var combsL = emptyList<Comb>()
    private var combsR = emptyList<Comb>()
    private var apL = emptyList<AllPass>()
    private var apR = emptyList<AllPass>()
    private var feedback = 0.84f
    private var damp = 0.2f
    var wet = 0.3f
    var dry = 1f
    var outL = 0f
    var outR = 0f

    fun setup(sr: Float, room: Float, damping: Float, wetMix: Float, dryMix: Float) {
        val k = sr / 44100f
        val combT = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491)
        val apT = intArrayOf(556, 441, 341, 225)
        combsL = combT.map { Comb((it * k).toInt()) }
        combsR = combT.map { Comb(((it + 23) * k).toInt()) }
        apL = apT.map { AllPass((it * k).toInt()) }
        apR = apT.map { AllPass(((it + 23) * k).toInt()) }
        feedback = room * 0.28f + 0.7f
        damp = damping * 0.4f
        wet = wetMix
        dry = dryMix
    }

    fun p(l: Float, r: Float) {
        val input = (l + r) * 0.015f
        var sl = 0f
        var sr = 0f
        for (c in combsL) sl += c.p(input, feedback, damp)
        for (c in combsR) sr += c.p(input, feedback, damp)
        for (a in apL) sl = a.p(sl)
        for (a in apR) sr = a.p(sr)
        outL = l * dry + sl * wet * 3f
        outR = r * dry + sr * wet * 3f
    }
}

/** Linha de atraso com leitura fracionária (wow/flutter, coro, efeito Haas). */
internal class DelayLine(size: Int) {
    private val buf = FloatArray(size.coerceAtLeast(4))
    private var w = 0

    fun write(x: Float) {
        buf[w] = x
        if (++w >= buf.size) w = 0
    }

    /** Lê [delay] amostras atrás da última escrita. */
    fun read(delay: Float): Float {
        val d = delay.coerceIn(1f, (buf.size - 2).toFloat())
        var pos = w - 1 - d
        while (pos < 0) pos += buf.size
        val i = pos.toInt()
        val frac = pos - i
        val a = buf[i % buf.size]
        val b = buf[(i + 1) % buf.size]
        return a + (b - a) * frac
    }
}

internal class EditDsp {
    var sampleRate = 44100f
    var processing = false
    var outL = 0f
    var outR = 0f

    private var edit = MusicEdit.NONE
    private var stereo = true
    private val reverb = Reverb()
    private var useReverb = false
    private val fL = Array(4) { Biquad() }
    private val fR = Array(4) { Biquad() }
    private var nFilters = 0
    private var gain = 1f
    private var t = 0L
    private var seed = 22222
    private var dlL = DelayLine(8)
    private var dlR = DelayLine(8)
    private val lowMid1 = Biquad()
    private val lowMid2 = Biquad()
    private var crackle = 0f
    private var holdN = 1
    private var holdC = 0
    private var holdL = 0f
    private var holdR = 0f
    // chopped and screwed
    private var ringL = FloatArray(1)
    private var ringR = FloatArray(1)
    private var ringW = 0
    private var chopEvery = 1
    private var chopSeg = 1
    private var chopCount = 0
    private var repeatLeft = 0
    private var repeatStart = 0
    private var repeatPos = 0

    fun setup(e: MusicEdit, channels: Int) {
        edit = e
        stereo = channels == 2
        val sr = sampleRate
        t = 0
        useReverb = false
        nFilters = 0
        gain = 1f
        crackle = 0f
        fun filter(make: (Biquad) -> Unit) {
            make(fL[nFilters]); make(fR[nFilters]); nFilters++
        }
        fun rev(room: Float, damp: Float, wet: Float, dry: Float) {
            reverb.setup(sr, room, damp, wet, dry); useReverb = true
        }
        processing = true
        when (e) {
            MusicEdit.NONE, MusicEdit.REVERSED, MusicEdit.SPED_UP -> processing = false
            MusicEdit.SLOWED_REVERB -> {
                filter { it.lowpass(sr, 11000f) }
                rev(0.88f, 0.45f, 0.33f, 0.85f)
            }
            MusicEdit.SUPER_SLOWED -> {
                filter { it.lowShelf(sr, 90f, 3f) }
                filter { it.lowpass(sr, 10000f) }
                rev(0.9f, 0.45f, 0.38f, 0.8f)
            }
            MusicEdit.ULTRA_SLOWED -> {
                filter { it.lowShelf(sr, 90f, 4f) }
                filter { it.lowpass(sr, 9000f) }
                rev(0.93f, 0.5f, 0.42f, 0.78f)
            }
            MusicEdit.NIGHTCORE -> filter { it.highShelf(sr, 6000f, 2.5f) }
            MusicEdit.DAYCORE -> {
                filter { it.lowShelf(sr, 110f, 2f) }
                filter { it.lowpass(sr, 9000f) }
            }
            MusicEdit.EIGHT_D -> rev(0.6f, 0.5f, 0.18f, 1f)
            MusicEdit.UNDERWATER -> {
                filter { it.lowpass(sr, 420f, 1f) }
                filter { it.lowpass(sr, 420f, 0.8f) }
                rev(0.7f, 0.8f, 0.25f, 1f)
                dlL = DelayLine((sr * 0.02f).toInt()); dlR = DelayLine((sr * 0.02f).toInt())
                gain = 1.5f
            }
            MusicEdit.OTHER_ROOM -> {
                filter { it.highpass(sr, 90f) }
                filter { it.lowpass(sr, 650f, 0.9f) }
                filter { it.lowpass(sr, 900f) }
                rev(0.65f, 0.75f, 0.5f, 0.55f)
                gain = 1.1f
            }
            MusicEdit.BATHROOM -> {
                filter { it.lowShelf(sr, 110f, 6f) }
                filter { it.lowpass(sr, 1100f, 0.9f) }
                rev(0.45f, 0.15f, 0.55f, 0.6f)
                gain = 0.95f
            }
            MusicEdit.SPATIAL -> {
                rev(0.6f, 0.5f, 0.12f, 1f)
                dlL = DelayLine((sr * 0.03f).toInt()); dlR = DelayLine((sr * 0.03f).toInt())
                gain = 0.9f
            }
            MusicEdit.LOFI -> {
                filter { it.highpass(sr, 120f) }
                filter { it.lowpass(sr, 3800f) }
                filter { it.lowpass(sr, 5000f) }
                dlL = DelayLine((sr * 0.02f).toInt()); dlR = DelayLine((sr * 0.02f).toInt())
                gain = 1.1f
            }
            MusicEdit.BITCRUSHED -> {
                holdN = max(1, (sr / 7000f).toInt())
                holdC = 0
                filter { it.highpass(sr, 60f) }
            }
            MusicEdit.VHS -> {
                filter { it.highpass(sr, 100f) }
                filter { it.lowpass(sr, 4500f) }
                filter { it.peaking(sr, 1500f, 2f, 0.8f) }
                dlL = DelayLine((sr * 0.03f).toInt()); dlR = DelayLine((sr * 0.03f).toInt())
            }
            MusicEdit.CHOPPED -> {
                filter { it.lowShelf(sr, 90f, 3f) }
                rev(0.7f, 0.5f, 0.15f, 0.95f)
                val ring = (sr * 2f).toInt()
                ringL = FloatArray(ring); ringR = FloatArray(ring); ringW = 0
                chopEvery = (sr * 3.2f).toInt()
                chopSeg = (sr * 0.35f).toInt()
                chopCount = 0; repeatLeft = 0
            }
            MusicEdit.BASS -> {
                filter { it.lowShelf(sr, 100f, 12f) }
                filter { it.peaking(sr, 55f, 4f, 1f) }
                gain = 0.62f
            }
            MusicEdit.KARAOKE -> {
                if (!stereo) processing = false
                lowMid1.lowpass(sr, 140f)
                lowMid2.lowpass(sr, 140f)
            }
        }
    }

    private fun noise(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 32768f - 1f
    }

    fun process(inL: Float, inR: Float) {
        var l = inL * gain
        var r = inR * gain
        val sr = sampleRate
        val time = t / sr
        t++
        when (edit) {
            MusicEdit.EIGHT_D -> {
                // o som gira de um ouvido para o outro (uma volta a cada ~9 s)
                val m = (l + r) * 0.5f
                val pan = sin(2 * PI * 0.11 * time).toFloat()
                val ang = (pan + 1f) * (PI.toFloat() / 4f)
                l = m * cos(ang) * 1.35f + l * 0.1f
                r = m * sin(ang) * 1.35f + r * 0.1f
            }
            MusicEdit.UNDERWATER -> {
                dlL.write(l); dlR.write(r)
                val d = sr * (0.004f + 0.0025f * sin(2 * PI * 0.35 * time).toFloat())
                l = dlL.read(d); r = dlR.read(d * 1.1f)
            }
            MusicEdit.SPATIAL -> {
                val mid = (l + r) * 0.5f
                val side = (l - r) * 0.5f * 1.7f
                val wl = mid + side
                val wr = mid - side
                dlL.write(wl); dlR.write(wr)
                l = wl + dlR.read(sr * 0.011f) * 0.22f
                r = wr + dlL.read(sr * 0.014f) * 0.22f
            }
            MusicEdit.LOFI -> {
                dlL.write(l); dlR.write(r)
                val d = sr * (0.006f + 0.0015f * sin(2 * PI * 0.4 * time).toFloat())
                l = dlL.read(d); r = dlR.read(d)
                l = tanh(l * 1.3f) / 1.1f
                r = tanh(r * 1.3f) / 1.1f
            }
            MusicEdit.BITCRUSHED -> {
                if (holdC == 0) {
                    holdL = (l * 24f).toInt() / 24f
                    holdR = (r * 24f).toInt() / 24f
                }
                holdC = (holdC + 1) % holdN
                l = holdL; r = holdR
            }
            MusicEdit.VHS -> {
                val mid = (l + r) * 0.5f
                l = mid + (l - mid) * 0.5f
                r = mid + (r - mid) * 0.5f
                dlL.write(l); dlR.write(r)
                val d = sr * (0.008f + 0.0025f * sin(2 * PI * 0.6 * time).toFloat() + 0.0004f * sin(2 * PI * 7 * time).toFloat())
                l = tanh(dlL.read(d) * 1.6f) / tanh(1.6f)
                r = tanh(dlR.read(d) * 1.6f) / tanh(1.6f)
            }
            MusicEdit.KARAOKE -> {
                val mid = (l + r) * 0.5f
                val side = (l - r) * 0.5f * 1.6f
                val low = lowMid2.p(lowMid1.p(mid))
                l = side + low
                r = -side + low
            }
            else -> Unit
        }
        for (i in 0 until nFilters) {
            l = fL[i].p(l)
            r = fR[i].p(r)
        }
        if (edit == MusicEdit.CHOPPED) {
            ringL[ringW] = l; ringR[ringW] = r
            if (repeatLeft > 0) {
                val idx = (repeatStart + repeatPos) % ringL.size
                l = ringL[idx]; r = ringR[idx]
                repeatPos = (repeatPos + 1) % chopSeg
                repeatLeft--
            } else if (++chopCount >= chopEvery) {
                // repete o último pedaço duas vezes (o "corte" do chopped)
                chopCount = 0
                repeatStart = (ringW - chopSeg + ringL.size) % ringL.size
                repeatPos = 0
                repeatLeft = chopSeg * 2
            }
            ringW = (ringW + 1) % ringL.size
        }
        if (useReverb) {
            reverb.p(l, r)
            l = reverb.outL; r = reverb.outR
        }
        when (edit) {
            MusicEdit.LOFI -> {
                // chiado de vinil: estalinhos e um leve ruído de fundo
                if (noise() > 0.9998f) crackle = 0.12f + abs(noise()) * 0.22f
                val c = crackle * (if (noise() > 0) 1f else -1f)
                crackle *= 0.55f
                val hiss = noise() * 0.004f
                l += c + hiss; r += c + hiss
            }
            MusicEdit.VHS -> {
                val hiss = noise() * 0.006f
                l += hiss; r += hiss
            }
            MusicEdit.BASS -> {
                l = tanh(l * 1.4f)
                r = tanh(r * 1.4f)
            }
            else -> Unit
        }
        if (stereo) {
            outL = l; outR = r
        } else {
            outL = (l + r) * 0.5f; outR = outL
        }
    }
}
