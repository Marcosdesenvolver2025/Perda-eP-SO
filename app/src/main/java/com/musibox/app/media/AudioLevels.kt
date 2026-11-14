package com.musibox.app.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Níveis do som que está tocando (graves, médios, agudos e geral), lidos do próprio áudio do player.
 * Não usa o microfone nem pede permissão: só "escuta" o que o MusiBox toca.
 */
object AudioLevels {
    /** 0..1 cada. Lidos pela tela do player (visualizador). */
    @Volatile var bass = 0f
    @Volatile var mid = 0f
    @Volatile var treble = 0f
    @Volatile var level = 0f

    /** Só calcula quando o visualizador está na tela (economiza bateria). */
    @Volatile var listening = false

    internal fun reset() { bass = 0f; mid = 0f; treble = 0f; level = 0f }
}

/** Passa o áudio adiante sem mudar nada e mede a energia de cada faixa de frequência. */
@UnstableApi
class LevelTapProcessor : BaseAudioProcessor() {
    private var channels = 2
    private var lp1 = 0f
    private var lp2 = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // formato diferente: fica desligado (o áudio passa direto, sem visualizador)
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (AudioLevels.listening) measure(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    private fun measure(buf: ByteBuffer) {
        var sumB = 0.0; var sumM = 0.0; var sumT = 0.0; var sumAll = 0.0
        var n = 0
        val frameBytes = 2 * channels
        // olha um quadro a cada 2 (é só para o visual)
        while (buf.remaining() >= frameBytes * 2) {
            var s = 0f
            for (c in 0 until channels) s += buf.short.toFloat()
            buf.position(buf.position() + frameBytes)
            s /= (channels * 32768f)
            lp1 += 0.035f * (s - lp1)          // graves (~ até 250 Hz)
            lp2 += 0.25f * (s - lp2)           // até ~2–3 kHz
            val b = lp1; val m = lp2 - lp1; val t = s - lp2
            sumB += b * b; sumM += m * m; sumT += t * t; sumAll += s * s
            n++
        }
        if (n == 0) return
        fun lvl(e: Double, gain: Float) = (sqrt(e / n).toFloat() * gain).coerceIn(0f, 1f)
        AudioLevels.bass = smooth(AudioLevels.bass, lvl(sumB, 3.2f))
        AudioLevels.mid = smooth(AudioLevels.mid, lvl(sumM, 4.5f))
        AudioLevels.treble = smooth(AudioLevels.treble, lvl(sumT, 7f))
        AudioLevels.level = smooth(AudioLevels.level, lvl(sumAll, 2.6f))
    }

    private fun smooth(old: Float, new: Float) = if (new > old) old + (new - old) * 0.7f else old + (new - old) * 0.25f

    override fun onFlush() { lp1 = 0f; lp2 = 0f; AudioLevels.reset() }

    override fun onReset() { lp1 = 0f; lp2 = 0f; AudioLevels.reset() }
}
