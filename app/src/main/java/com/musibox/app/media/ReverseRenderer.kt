package com.musibox.app.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * "Reversed": decodifica a música inteira e grava uma cópia de trás para frente (WAV) no cache do app.
 * Guarda só as últimas 3 para não ocupar espaço.
 */
object ReverseRenderer {
    const val SCHEME = "musibox-reverse"

    fun reverseUri(original: String): Uri = Uri.parse("$SCHEME://song?u=" + Uri.encode(original))

    fun originalOf(uri: Uri): Uri? = uri.getQueryParameter("u")?.let(Uri::parse)

    private val lock = Any()

    fun render(context: Context, source: Uri): File = synchronized(lock) {
        val dir = File(context.cacheDir, "reversed").apply { mkdirs() }
        val name = sha1(source.toString()) + ".wav"
        val out = File(dir, name)
        if (out.exists() && out.length() > 44) {
            out.setLastModified(System.currentTimeMillis())
            return out
        }
        dir.listFiles()?.filter { it.name.endsWith(".wav") }?.sortedByDescending { it.lastModified() }
            ?.drop(2)?.forEach { it.delete() }
        val raw = File(dir, "$name.raw")
        val tmp = File(dir, "$name.tmp")
        try {
            val (rate, channels) = decode(context, source, raw)
            writeReversedWav(raw, tmp, rate, channels)
            if (!tmp.renameTo(out)) throw IOException("rename")
            return out
        } finally {
            raw.delete()
            tmp.delete()
        }
    }

    private fun decode(context: Context, source: Uri, raw: File): Pair<Int, Int> {
        val ex = MediaExtractor()
        ex.setDataSource(context, source, null)
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IOException("sem áudio")
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        BufferedOutputStream(FileOutputStream(raw), 1 shl 16).use { os ->
            var chunk = ByteArray(1 shl 16)
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        if (floatPcm) {
                            val fb = buf.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            val n = fb.remaining()
                            if (chunk.size < n * 2) chunk = ByteArray(n * 2)
                            for (i in 0 until n) {
                                val v = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt()
                                chunk[i * 2] = (v and 0xFF).toByte()
                                chunk[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                            }
                            os.write(chunk, 0, n * 2)
                        } else {
                            val n = info.size
                            if (chunk.size < n) chunk = ByteArray(n)
                            buf.get(chunk, 0, n)
                            os.write(chunk, 0, n)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        }
        runCatching { codec.stop() }
        codec.release()
        ex.release()
        return rate to channels.coerceIn(1, 8)
    }

    private fun writeReversedWav(raw: File, out: File, rate: Int, channels: Int) {
        val frame = channels * 2
        val frames = raw.length() / frame
        val dataSize = frames * frame
        RandomAccessFile(raw, "r").use { src ->
            BufferedOutputStream(FileOutputStream(out), 1 shl 16).use { os ->
                os.write(wavHeader(dataSize, rate, channels))
                val chunkFrames = 16_384
                val buf = ByteArray(chunkFrames * frame)
                val rev = ByteArray(chunkFrames * frame)
                var end = frames
                while (end > 0) {
                    val start = maxOf(0L, end - chunkFrames)
                    val n = (end - start).toInt()
                    src.seek(start * frame)
                    src.readFully(buf, 0, n * frame)
                    for (i in 0 until n) {
                        System.arraycopy(buf, (n - 1 - i) * frame, rev, i * frame, frame)
                    }
                    os.write(rev, 0, n * frame)
                    end = start
                }
            }
        }
    }

    private fun wavHeader(dataSize: Long, rate: Int, channels: Int): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + dataSize).toInt())
        b.put("WAVE".toByteArray()); b.put("fmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(channels.toShort())
        b.putInt(rate); b.putInt(rate * channels * 2); b.putShort((channels * 2).toShort()); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataSize.toInt())
        return b.array()
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
