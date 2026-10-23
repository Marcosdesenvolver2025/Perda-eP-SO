package com.musibox.app.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cria um toque de celular com um trecho da música (recurso do Pro).
 * MP3: corta nos quadros do próprio arquivo (sem perder qualidade).
 * M4A/AAC: copia as amostras do trecho para um novo .m4a.
 * WAV: copia o trecho de áudio PCM.
 */
object RingtoneMaker {
    const val MAX_MS = 40_000L

    class Result(val uri: Uri, val name: String)

    class Unsupported(msg: String) : Exception(msg)

    suspend fun create(context: Context, source: Uri, title: String, startMs: Long, endMs: Long): Result = withContext(Dispatchers.IO) {
        require(endMs > startMs) { "Trecho inválido" }
        val mime = detectMime(context, source)
        val tmp = File(context.cacheDir, "toque_tmp")
        tmp.delete()
        val (ext, outMime) = when {
            mime == "audio/mpeg" -> {
                cutMp3(context, source, startMs, endMs, tmp)
                "mp3" to "audio/mpeg"
            }
            mime == "audio/mp4a-latm" -> {
                cutAac(context, source, startMs, endMs, tmp)
                "m4a" to "audio/mp4"
            }
            mime == "audio/raw" || mime == "audio/wav" || mime == "audio/x-wav" -> {
                cutWav(context, source, startMs, endMs, tmp)
                "wav" to "audio/wav"
            }
            else -> throw Unsupported("Este formato ainda não vira toque. Use uma música MP3 ou M4A.")
        }
        val clean = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60).ifBlank { "Toque" }
        val name = "$clean (toque MusiBox).$ext"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.TITLE, "$clean (toque)")
            put(MediaStore.Audio.Media.MIME_TYPE, outMime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/MusiBox")
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.IS_ALARM, 1)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Não foi possível salvar o toque")
        try {
            resolver.openOutputStream(uri)!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        } finally {
            tmp.delete()
        }
        Result(uri, name)
    }

    /** Pode trocar o toque do celular? (o Android pede autorização uma vez) */
    fun canSetDefault(context: Context): Boolean = Settings.System.canWrite(context)

    fun setAsDefault(context: Context, uri: Uri): Boolean = runCatching {
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        true
    }.getOrDefault(false)

    private fun detectMime(context: Context, uri: Uri): String {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.getString(MediaFormat.KEY_MIME) ?: context.contentResolver.getType(uri).orEmpty()
        } catch (e: Exception) {
            context.contentResolver.getType(uri).orEmpty()
        } finally {
            ex.release()
        }
    }

    // ------------------------------------------------------------ MP3 (quadros)

    private val BITRATE_V1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val BITRATE_V2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    private fun cutMp3(context: Context, uri: Uri, startMs: Long, endMs: Long, out: File) {
        val data = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        var p = 0
        if (data.size > 10 && data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
            val size = ((data[6].toInt() and 0x7f) shl 21) or ((data[7].toInt() and 0x7f) shl 14) or
                ((data[8].toInt() and 0x7f) shl 7) or (data[9].toInt() and 0x7f)
            p = 10 + size + if ((data[5].toInt() and 0x10) != 0) 10 else 0
        }
        val buf = ByteArrayOutputStream()
        var timeUs = 0.0
        var first = true
        while (p + 4 <= data.size) {
            val b1 = data[p].toInt() and 0xff
            val b2 = data[p + 1].toInt() and 0xff
            if (b1 != 0xff || (b2 and 0xe0) != 0xe0) { p++; continue }
            val ver = (b2 shr 3) and 3 // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
            val layer = (b2 shr 1) and 3 // 1 = Layer III
            val b3 = data[p + 2].toInt() and 0xff
            val brIdx = (b3 shr 4) and 0xf
            val srIdx = (b3 shr 2) and 3
            val pad = (b3 shr 1) and 1
            if (ver == 1 || layer != 1 || brIdx == 0 || brIdx == 15 || srIdx == 3) { p++; continue }
            val sr = when (ver) {
                3 -> intArrayOf(44100, 48000, 32000)[srIdx]
                2 -> intArrayOf(22050, 24000, 16000)[srIdx]
                else -> intArrayOf(11025, 12000, 8000)[srIdx]
            }
            val br = (if (ver == 3) BITRATE_V1 else BITRATE_V2)[brIdx] * 1000
            val len = (if (ver == 3) 144 else 72) * br / sr + pad
            val samples = if (ver == 3) 1152 else 576
            if (len < 4 || p + len > data.size) break
            val isInfo = first && (indexOf(data, p, minOf(p + len, data.size), "Xing") >= 0 || indexOf(data, p, minOf(p + len, data.size), "Info") >= 0)
            first = false
            val tMs = timeUs / 1000.0
            if (!isInfo && tMs >= startMs && tMs < endMs) buf.write(data, p, len)
            if (!isInfo) timeUs += samples * 1_000_000.0 / sr
            if (tMs >= endMs) break
            p += len
        }
        if (buf.size() == 0) throw Unsupported("Não consegui ler esta música MP3.")
        out.writeBytes(buf.toByteArray())
    }

    private fun indexOf(data: ByteArray, from: Int, to: Int, s: String): Int {
        val pat = s.toByteArray()
        var i = from
        while (i + pat.size <= to) {
            var ok = true
            for (k in pat.indices) if (data[i + k] != pat[k]) { ok = false; break }
            if (ok) return i
            i++
        }
        return -1
    }

    // ------------------------------------------------------------ AAC / M4A

    private fun cutAac(context: Context, uri: Uri, startMs: Long, endMs: Long, out: File) {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val outTrack = m.addTrack(format)
            m.start()
            val startUs = startMs * 1000
            val endUs = endMs * 1000
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 256 * 1024
            val buffer = ByteBuffer.allocate(maxOf(size, 64 * 1024))
            val info = MediaCodec.BufferInfo()
            var base = -1L
            var wrote = 0
            while (true) {
                info.offset = 0
                info.size = ex.readSampleData(buffer, 0)
                if (info.size < 0) break
                val t = ex.sampleTime
                if (t > endUs) break
                if (base < 0) base = t
                info.presentationTimeUs = t - base
                info.flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                m.writeSampleData(outTrack, buffer, info)
                wrote++
                if (!ex.advance()) break
            }
            if (wrote == 0) throw Unsupported("Não consegui ler esta música.")
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            ex.release()
        }
    }

    // ------------------------------------------------------------ WAV (PCM)

    private fun cutWav(context: Context, uri: Uri, startMs: Long, endMs: Long, out: File) {
        val data = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (data.size < 44 || String(data, 0, 4) != "RIFF" || String(data, 8, 4) != "WAVE") throw Unsupported("WAV inválido.")
        var p = 12
        var channels = 2
        var rate = 44100
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (p + 8 <= data.size) {
            val id = String(data, p, 4)
            val len = bb.getInt(p + 4)
            if (id == "fmt ") {
                channels = bb.getShort(p + 10).toInt()
                rate = bb.getInt(p + 12)
                bits = bb.getShort(p + 22).toInt()
            } else if (id == "data") {
                dataStart = p + 8
                dataLen = minOf(len, data.size - dataStart)
                break
            }
            p += 8 + len + (len and 1)
        }
        if (dataStart < 0) throw Unsupported("WAV inválido.")
        val frame = channels * bits / 8
        val from = (startMs * rate / 1000 * frame).toInt().coerceIn(0, dataLen)
        val to = (endMs * rate / 1000 * frame).toInt().coerceIn(from, dataLen)
        val pcmLen = to - from
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcmLen).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * frame).putShort(frame.toShort()).putShort(bits.toShort())
        header.put("data".toByteArray()).putInt(pcmLen)
        out.outputStream().use {
            it.write(header.array())
            it.write(data, dataStart + from, pcmLen)
        }
    }
}
